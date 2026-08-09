package com.cauverystore.service;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.Cart;
import com.cauverystore.entities.CartItem;
import com.cauverystore.entities.GstConfiguration;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.entities.User;
import com.cauverystore.repository.GstConfigurationRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The itemised bill a customer sees before they are asked to pay.
 *
 * <h2>Why the tax has to be shown per line</h2>
 *
 * A basket can carry four different GST rates at once - nil on loose rice, 5% on a lungi, 18%
 * on a charger, 3% on a gold chain - so a single "Tax" figure tells the customer nothing they
 * could check. Section 31 requires the invoice to state the HSN, the taxable value and the rate
 * for every line, and someone about to pay should see the same breakdown they will be invoiced,
 * not a total they have to take on trust.
 *
 * <h2>Why nothing here comes from the browser</h2>
 *
 * Prices and quantities are read from the stored cart, never from the request. A checkout that
 * posted its own line values could be edited to pay tax on a lower figure than the goods are
 * sold at. The only thing the caller supplies is where the order is going, and even that is
 * resolved through the same code the invoice uses.
 *
 * <h2>Why an untaxable line stops the order</h2>
 *
 * If any line has no determinable rate the bill is returned with canProceed false. Quoting a
 * total that excludes it would understate what is owed, and charging nil on it would be a rate
 * nobody published - so the customer is stopped rather than billed wrongly and corrected later
 * with a credit note.
 */
@Service
public class CheckoutBillService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutBillService.class);

    private final CartService cartService;
    private final GstRateResolver rateResolver;
    private final GstInvoiceService invoiceService;
    private final GstConfigurationRepository gstConfigRepo;
    private final SellerRegistrationRepository sellerRegRepo;

    public CheckoutBillService(CartService cartService,
                               GstRateResolver rateResolver,
                               GstInvoiceService invoiceService,
                               GstConfigurationRepository gstConfigRepo,
                               SellerRegistrationRepository sellerRegRepo) {
        this.cartService = cartService;
        this.rateResolver = rateResolver;
        this.invoiceService = invoiceService;
        this.gstConfigRepo = gstConfigRepo;
        this.sellerRegRepo = sellerRegRepo;
    }

    /**
     * Builds the bill for a user's current cart delivered to a given address.
     *
     * @param deliveryAddress where the goods are going. Place of supply follows the destination,
     *                        not the billing address, and it decides IGST against CGST+SGST.
     */
    public Map<String, Object> billFor(User user, Address deliveryAddress) {
        Cart cart = cartService.getCart(user);
        LocalDate today = LocalDate.now();

        String deliveryStateCode = null;
        String placeOfSupplyNote = null;
        try {
            deliveryStateCode = invoiceService.resolveDeliveryState(deliveryAddress);
        } catch (RuntimeException e) {
            // Without a destination the tax heads cannot be decided. Say so rather than
            // defaulting to intra-state, which would split a tax that may be wholly IGST.
            placeOfSupplyNote = e.getMessage();
        }

        List<Map<String, Object>> lines = new ArrayList<>();
        List<Map<String, Object>> blocked = new ArrayList<>();
        double taxable = 0, cgst = 0, sgst = 0, igst = 0;
        boolean anyInterState = false;

        // With no destination there is no place of supply, and every line's tax heads are
        // unknowable. Pricing them anyway would mean picking intra-state and quoting a split
        // that may be wholly IGST, so nothing is priced until the address is known.
        for (CartItem item : deliveryStateCode == null ? List.<CartItem>of() : cart.getItems()) {
            if (item.isSavedForLater()) continue;
            Product product = item.getProduct();
            if (product == null) continue;

            int qty = item.getQuantity() > 0 ? item.getQuantity() : 1;
            double unitPrice = unitPriceOf(item);
            double lineValue = round(unitPrice * qty);

            // Each seller's own state decides the heads for their lines - a basket spanning two
            // states can be IGST on one line and CGST+SGST on another.
            String sellerState = sellerStateOf(product);
            if (sellerState == null) {
                blocked.add(problem(product, "The seller's GST registration state is not "
                        + "recorded, so it cannot be said whether IGST or CGST+SGST applies."));
                continue;
            }
            boolean interState = !sellerState.equals(deliveryStateCode);
            anyInterState |= interState;

            GstRateResolver.Resolved resolved;
            try {
                resolved = rateResolver.resolve(product, interState, today, unitPrice);
            } catch (GstRateResolver.GstRateUnresolvedException e) {
                blocked.add(problem(product, e.getMessage()));
                continue;
            }

            double lineCgst = round(lineValue * resolved.getCgstRate() / 100.0);
            double lineSgst = round(lineValue * resolved.getSgstRate() / 100.0);
            double lineIgst = round(lineValue * resolved.getIgstRate() / 100.0);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", product.getId());
            row.put("description", product.getName());
            row.put("hsnCode", resolved.getHsnCode());
            row.put("quantity", qty);
            row.put("unitPrice", unitPrice);
            row.put("taxableValue", lineValue);
            row.put("gstRate", resolved.getTotalRate());
            row.put("cgstRate", resolved.getCgstRate());
            row.put("sgstRate", resolved.getSgstRate());
            row.put("igstRate", resolved.getIgstRate());
            row.put("cgstAmount", lineCgst);
            row.put("sgstAmount", lineSgst);
            row.put("igstAmount", lineIgst);
            row.put("taxAmount", round(lineCgst + lineSgst + lineIgst));
            // What this line actually costs, which is the number a customer is looking for.
            row.put("lineTotal", round(lineValue + lineCgst + lineSgst + lineIgst));
            lines.add(row);

            taxable += lineValue;
            cgst += lineCgst;
            sgst += lineSgst;
            igst += lineIgst;
        }

        double totalTax = round(cgst + sgst + igst);
        Map<String, Object> bill = new LinkedHashMap<>();
        bill.put("lines", lines);
        bill.put("taxableValue", round(taxable));
        bill.put("cgstAmount", round(cgst));
        bill.put("sgstAmount", round(sgst));
        bill.put("igstAmount", round(igst));
        bill.put("totalTax", totalTax);
        bill.put("payable", round(taxable + totalTax));
        bill.put("placeOfSupply", deliveryStateCode);
        bill.put("taxType", deliveryStateCode == null ? null : (anyInterState ? "IGST" : "CGST+SGST"));
        bill.put("blocked", blocked);
        bill.put("canProceed", blocked.isEmpty() && !lines.isEmpty() && deliveryStateCode != null);

        if (deliveryStateCode == null) {
            bill.put("message", placeOfSupplyNote != null ? placeOfSupplyNote
                    : "A delivery address is needed before the tax can be worked out.");
        } else if (!blocked.isEmpty()) {
            bill.put("message", blocked.size() + " item(s) in this order cannot be taxed, so the "
                    + "order cannot be placed. Remove them or contact the seller.");
            log.warn("Checkout blocked for user {}: {} untaxable line(s)", user.getId(), blocked.size());
        }
        // Delivery and any discount are applied by the caller; this is the goods and their tax.
        bill.put("note", "Tax is worked out for each item from its HSN code and the CBIC rate in "
                + "force today. Delivery and discounts are shown separately.");
        return bill;
    }

    /** The seller's registered state, which is one half of the intra/inter-state question.
     *  The GST configuration is authoritative; the GSTIN on the seller's registration is the
     *  fallback (same precedence as invoice generation), so an order from a registered seller
     *  is never blocked just because their config row is missing. */
    private String sellerStateOf(Product product) {
        if (product.getSellerId() == null) return null;
        String gstin = gstConfigRepo.findBySellerId(product.getSellerId())
                .map(GstConfiguration::getGstin)
                .orElse(null);
        if (gstin == null || gstin.length() < 2) {
            gstin = sellerRegRepo.findByUserId(product.getSellerId())
                    .map(SellerRegistration::getGstin)
                    .orElse(null);
        }
        if (gstin == null || gstin.length() < 2) return null;
        return gstin.substring(0, 2);
    }

    /** The price actually being charged, offer price included. */
    private double unitPriceOf(CartItem item) {
        Product p = item.getProduct();
        if (p.getOfferPrice() != null && p.getOfferPrice() > 0 && p.getOfferPrice() < p.getPrice()) {
            return p.getOfferPrice();
        }
        return p.getPrice() != null ? p.getPrice() : 0.0;
    }

    private Map<String, Object> problem(Product product, String why) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productId", product.getId());
        row.put("description", product.getName());
        row.put("reason", why);
        return row;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
