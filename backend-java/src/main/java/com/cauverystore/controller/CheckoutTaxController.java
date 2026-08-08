package com.cauverystore.controller;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.User;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.service.CheckoutBillService;
import com.cauverystore.service.TaxPreviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Quotes the GST on a basket so the customer sees it before agreeing to pay.
 *
 * <h2>What this is not</h2>
 *
 * It does not apply tax. Nothing it returns is stored, and no caller is trusted with the
 * figure: the tax actually charged is recomputed inside GstInvoiceService from the order as
 * stored, at the moment the invoice is raised. Were the checkout to send a total and have it
 * believed, the tax on the invoice would be whatever the client said it was.
 *
 * So this exists to show a number the server will independently arrive at, not to decide one.
 */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutTaxController {

    private final TaxPreviewService taxPreview;
    private final CheckoutBillService billService;
    private final UserRepository userRepo;

    public CheckoutTaxController(TaxPreviewService taxPreview,
                                 CheckoutBillService billService,
                                 UserRepository userRepo) {
        this.taxPreview = taxPreview;
        this.billService = billService;
        this.userRepo = userRepo;
    }

    /**
     * The itemised bill for the signed-in user's cart, delivered to a given address.
     *
     * Prices and quantities come from the stored cart, never from the request - a checkout that
     * posted its own line values could be edited to pay tax on a lower figure than the goods
     * sell for. The body supplies only the destination, since that decides IGST against
     * CGST+SGST.
     *
     * Body: {@code state}, {@code pincode}, and optionally {@code city} / {@code street}.
     */
    @PostMapping("/bill")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> bill(@RequestBody Map<String, Object> body,
                                                    java.security.Principal principal) {
        User user = userRepo.findByEmail(principal.getName());
        if (user == null) user = userRepo.findByUsername(principal.getName());
        if (user == null) return ResponseEntity.status(401).build();

        Address to = new Address();
        to.setState(str(body.get("state")));
        to.setPincode(str(body.get("pincode")));
        to.setCity(str(body.get("city")));
        return ResponseEntity.ok(billService.billFor(user, to));
    }

    /**
     * Quotes tax for a basket.
     *
     * Body: {@code sellerStateCode}, {@code deliveryStateCode}, optional {@code supplyDate},
     * and {@code lines} of {@code productId} / {@code quantity} / {@code unitPrice}.
     *
     * The delivery state is the *shipping* address's, not the billing address's - place of
     * supply follows where the goods go, and using the billing state swaps CGST+SGST for IGST
     * on every order shipped to a different state than the card is registered in.
     */
    @PostMapping("/apply-tax")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> applyTax(@RequestBody Map<String, Object> body) {
        List<TaxPreviewService.Line> lines = new ArrayList<>();
        Object raw = body.get("lines");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) continue;
                lines.add(new TaxPreviewService.Line(
                        asLong(m.get("productId")),
                        asInt(m.get("quantity")),
                        asDouble(m.get("unitPrice"))));
            }
        }

        String supplyDate = str(body.get("supplyDate"));
        return ResponseEntity.ok(taxPreview.preview(
                lines,
                str(body.get("sellerStateCode")),
                str(body.get("deliveryStateCode")),
                supplyDate == null || supplyDate.isBlank() ? null : LocalDate.parse(supplyDate)));
    }

    private String str(Object v) { return v == null ? null : String.valueOf(v); }

    private Long asLong(Object v) {
        if (v == null) return null;
        return v instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(v).trim());
    }

    private Integer asInt(Object v) {
        if (v == null) return null;
        return v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v).trim());
    }

    private Double asDouble(Object v) {
        if (v == null) return null;
        return v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v).trim());
    }
}
