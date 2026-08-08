package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows what tax a basket would attract, before anyone is asked to pay it.
 *
 * <h2>Why this only reports</h2>
 *
 * It would be natural to have the checkout page call this and use what comes back. That would
 * put the tax on the invoice under the client's control - a caller could send a different price,
 * a different delivery state, or simply ignore the answer, and the invoice would follow. So
 * nothing here writes anything, and no caller is trusted with the result: the tax that actually
 * gets charged is computed again inside {@link GstInvoiceService} from the order as stored, and
 * that is the number on the invoice.
 *
 * Which makes this a display of a calculation the server will repeat, not the calculation
 * itself. Same resolver, same date, same inputs - so what the customer is shown is what they
 * will be charged, without the shown figure being the one that binds.
 *
 * <h2>Why an unresolvable line is not simply skipped</h2>
 *
 * A basket containing goods with no determinable rate cannot be totalled honestly. Leaving the
 * line out would understate the total; charging nil would be a rate nobody published. Both are
 * reported as unresolved, with the reason, so the caller can block the checkout rather than
 * quote a figure that will not survive invoicing.
 */
@Service
public class TaxPreviewService {

    private final ProductRepository productRepo;
    private final GstRateResolver rateResolver;

    public TaxPreviewService(ProductRepository productRepo, GstRateResolver rateResolver) {
        this.productRepo = productRepo;
        this.rateResolver = rateResolver;
    }

    /** One requested line: which product, how many, and at what price it is being sold. */
    public record Line(Long productId, Integer quantity, Double unitPrice) {}

    /**
     * @param deliveryStateCode the two-digit state of the *shipping* address. Place of supply
     *                          follows where the goods go, not where the card is registered,
     *                          and getting this wrong swaps CGST+SGST for IGST.
     * @param sellerStateCode   the supplier's state.
     * @param onDate            the supply date; null means today. Passed through so a quote for
     *                          a past order re-prices at the rate that applied then.
     */
    public Map<String, Object> preview(List<Line> lines, String sellerStateCode,
                                       String deliveryStateCode, LocalDate onDate) {
        LocalDate date = onDate != null ? onDate : LocalDate.now();
        boolean interState = isInterState(sellerStateCode, deliveryStateCode);

        List<Map<String, Object>> priced = new ArrayList<>();
        List<Map<String, Object>> unresolved = new ArrayList<>();
        double taxableTotal = 0, cgstTotal = 0, sgstTotal = 0, igstTotal = 0;

        for (Line line : lines == null ? List.<Line>of() : lines) {
            if (line == null || line.productId() == null) continue;
            Product product = productRepo.findById(line.productId()).orElse(null);
            if (product == null) {
                unresolved.add(problem(line.productId(), null,
                        "No such product, so nothing can be priced for it."));
                continue;
            }

            int qty = line.quantity() == null || line.quantity() < 1 ? 1 : line.quantity();
            // The price sent is what the basket is quoting; fall back to the catalogue price so
            // a caller that omits it still gets a figure rather than a silent zero.
            double unitPrice = line.unitPrice() != null ? line.unitPrice()
                    : (product.getPrice() != null ? product.getPrice() : 0.0);

            GstRateResolver.Resolved resolved;
            try {
                resolved = rateResolver.resolve(product, interState, date, unitPrice);
            } catch (GstRateResolver.GstRateUnresolvedException e) {
                // The rate exists in law; this system cannot yet say which. Reported, never
                // guessed, and never quietly dropped from the total.
                unresolved.add(problem(product.getId(), product.getName(), e.getMessage()));
                continue;
            }

            double taxable = round(unitPrice * qty);
            double cgst = round(taxable * resolved.getCgstRate() / 100.0);
            double sgst = round(taxable * resolved.getSgstRate() / 100.0);
            double igst = round(taxable * resolved.getIgstRate() / 100.0);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", product.getId());
            row.put("description", product.getName());
            row.put("hsnCode", resolved.getHsnCode());
            row.put("quantity", qty);
            row.put("unitPrice", unitPrice);
            row.put("taxableValue", taxable);
            row.put("gstRate", resolved.getTotalRate());
            row.put("cgstRate", resolved.getCgstRate());
            row.put("sgstRate", resolved.getSgstRate());
            row.put("igstRate", resolved.getIgstRate());
            row.put("cgstAmount", cgst);
            row.put("sgstAmount", sgst);
            row.put("igstAmount", igst);
            row.put("lineTotal", round(taxable + cgst + sgst + igst));
            priced.add(row);

            taxableTotal += taxable;
            cgstTotal += cgst;
            sgstTotal += sgst;
            igstTotal += igst;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("supplyDate", date);
        out.put("sellerStateCode", sellerStateCode);
        out.put("placeOfSupply", deliveryStateCode);
        out.put("interState", interState);
        out.put("taxType", interState ? "IGST" : "CGST+SGST");
        out.put("lines", priced);
        out.put("taxableValue", round(taxableTotal));
        out.put("cgstAmount", round(cgstTotal));
        out.put("sgstAmount", round(sgstTotal));
        out.put("igstAmount", round(igstTotal));
        out.put("totalTax", round(cgstTotal + sgstTotal + igstTotal));
        out.put("grandTotal", round(taxableTotal + cgstTotal + sgstTotal + igstTotal));
        out.put("unresolved", unresolved);
        // A caller that ignores this and charges the total anyway would be invoicing goods at a
        // rate nobody published, so it is stated plainly rather than left to be inferred.
        out.put("canProceed", unresolved.isEmpty());
        if (!unresolved.isEmpty()) {
            out.put("message", unresolved.size() + " item(s) have no determinable GST rate. The "
                    + "totals above exclude them and the order must not be placed until they are "
                    + "resolved.");
        }
        out.put("note", "A quotation. The tax charged is recomputed from the stored order when "
                + "the invoice is raised, and that figure is the one that binds.");
        return out;
    }

    /**
     * The same comparison GstInvoiceService makes when it stamps the invoice.
     *
     * Kept identical on purpose. If the preview decided this any other way - by pincode, by the
     * billing address, by a cleverer rule - a basket could be quoted CGST+SGST and invoiced
     * IGST, and the customer would be charged something other than what they agreed to.
     */
    private boolean isInterState(String sellerStateCode, String deliveryStateCode) {
        if (sellerStateCode == null || deliveryStateCode == null) {
            // Unknown is not the same as same-state. Treating it as intra-state would split the
            // tax two ways for a supply that may owe IGST.
            throw new IllegalArgumentException(
                    "Both the seller's state and the delivery state are needed before tax can be "
                            + "quoted - they decide whether this is CGST+SGST or IGST.");
        }
        return !deliveryStateCode.trim().equals(sellerStateCode.trim());
    }

    private Map<String, Object> problem(Long productId, String name, String why) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productId", productId);
        row.put("description", name);
        row.put("reason", why);
        return row;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
