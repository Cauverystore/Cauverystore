package com.cauverystore.billinglink;

import com.cauverystore.entities.*;
import com.cauverystore.repository.GstInvoiceRepository;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Sends the store's OWN paid website orders to Cauvery Billing, where each becomes a bill of the
 * business that owns the connection key (Cauvery Store is business 1 there).
 *
 * What is sent is the store's GST invoice exactly as issued: its number, lines and tax. Cauvery
 * Billing keeps that number and does not issue an invoice of its own, so the sale is not
 * invoiced twice. Lines carry the product's SKU and barcode so the billing side can match them
 * to the shop's products and take the stock off.
 *
 * Each invoice is sent once and remembered in billing_link_records. Cauvery Billing answers a
 * repeat of the same invoice number with the bill it already made, so a retry after a timeout
 * never makes a second bill. A failure is retried every run, up to MAX_AUTO_ATTEMPTS; after that
 * it waits for "Send now" on the admin screen.
 *
 * Sellers' orders are left out on purpose: those are the sellers' own sales.
 */
@Service
public class BillingLinkService {

    private static final Logger log = LoggerFactory.getLogger(BillingLinkService.class);
    static final int MAX_AUTO_ATTEMPTS = 5;

    private final BillingLinkRecordRepository records;
    private final GstInvoiceRepository invoices;
    private final OrderRepository orders;
    private final UserRepository users;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${billing-link.url:}") private String baseUrl;
    @Value("${billing-link.key:}") private String key;
    /** Comma-separated user ids of SELLER accounts that are the store's own business. */
    @Value("${billing-link.store-seller-ids:}") private String ownSellerIdsSetting;
    /** Orders before this one were tests and are never sent. */
    @Value("${billing-link.first-order-id:0}") private long firstOrderId;

    public BillingLinkService(BillingLinkRecordRepository records, GstInvoiceRepository invoices,
                              OrderRepository orders, UserRepository users, PlatformTransactionManager txm,
                              ObjectMapper json) {
        this.records = records;
        this.invoices = invoices;
        this.orders = orders;
        this.users = users;
        this.tx = new TransactionTemplate(txm);
        this.tx.setReadOnly(true);
        this.json = json;
    }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && key != null && !key.isBlank();
    }

    /** Sends every paid store invoice not yet in Cauvery Billing. force also retries ones given up on. */
    public Map<String, Object> sendAll(boolean force) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!isConfigured()) {
            result.put("skipped", "Cauvery Billing is not configured (BILLING_LINK_URL / BILLING_LINK_KEY)");
            return result;
        }
        Set<Long> store = storeSellerIds();
        int sent = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        for (GstInvoice inv : invoices.findAll()) {
            if (!due(inv, store, force)) continue;
            BillingLinkRecord rec = records.findByInvoiceId(inv.getId()).orElseGet(() -> {
                BillingLinkRecord n = new BillingLinkRecord();
                n.setInvoiceId(inv.getId());
                return n;
            });
            rec.setInvoiceNumber(inv.getInvoiceNumber());
            try {
                Map<String, Object> body = tx.execute(s -> request(inv.getId()));
                JsonNode reply = post(body);
                rec.setStatus(BillingLinkRecord.SENT);
                rec.setBillId(reply.path("billId").isNumber() ? reply.get("billId").asLong() : null);
                rec.setErrorMessage(null);
                sent++;
            } catch (RuntimeException e) {
                rec.setStatus(BillingLinkRecord.FAILED);
                rec.setErrorMessage(e.getMessage());
                failed++;
                errors.add(inv.getInvoiceNumber() + ": " + e.getMessage());
                log.warn("Cauvery Billing: invoice {} not sent: {}", inv.getInvoiceNumber(), e.getMessage());
            }
            rec.setAttempts(rec.getAttempts() + 1);
            records.save(rec);
        }
        result.put("sent", sent);
        result.put("failed", failed);
        result.put("errors", errors);
        return result;
    }

    /** A store invoice, issued, for a paid order from the real books, not yet sent (or retryable). */
    boolean due(GstInvoice inv, Set<Long> store, boolean force) {
        if (inv.getSellerId() != null && !store.contains(inv.getSellerId())) return false;
        String status = inv.getStatus() == null ? "" : inv.getStatus().toUpperCase(Locale.ROOT);
        if (status.startsWith("CANCEL") || "DRAFT".equals(status)) return false;
        if (inv.getOrderId() == null || inv.getOrderId() < firstOrderId) return false;
        Order order = orders.findById(inv.getOrderId()).orElse(null);
        if (order == null || !order.isPaid()) return false;
        Optional<BillingLinkRecord> rec = records.findByInvoiceId(inv.getId());
        if (rec.isEmpty()) return true;
        if (BillingLinkRecord.SENT.equals(rec.get().getStatus())) return false;
        return force || rec.get().getAttempts() < MAX_AUTO_ATTEMPTS;
    }

    /** The order as Cauvery Billing's /api/connect/orders expects it. Runs inside a transaction. */
    Map<String, Object> request(Long invoiceId) {
        GstInvoice inv = invoices.findById(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Invoice " + invoiceId + " no longer exists"));
        Order order = orders.findById(inv.getOrderId()).orElse(null);
        Map<String, Product> byName = new HashMap<>();
        Map<String, ProductVariant> variantByName = new HashMap<>();
        String phone = null;
        if (order != null) {
            if (order.getAddress() != null) phone = order.getAddress().getPhone();
            if (order.getItems() != null) {
                for (OrderItem i : order.getItems()) {
                    Product p = i.getProduct();
                    if (p == null) continue;
                    for (String n : new String[]{p.getName(), p.getBillDescription()}) {
                        if (n == null) continue;
                        String k = n.trim().toLowerCase(Locale.ROOT);
                        byName.putIfAbsent(k, p);
                        if (i.getVariant() != null) variantByName.putIfAbsent(k, i.getVariant());
                    }
                }
            }
        }

        boolean inter = Boolean.TRUE.equals(inv.getIsInterState());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (GstInvoiceItem l : inv.getItems()) {
            Map<String, Object> m = new LinkedHashMap<>();
            String name = l.getProductName() != null ? l.getProductName() : l.getBillDescription();
            String k = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
            Product p = byName.get(k);
            if (p == null && l.getBillDescription() != null) {
                k = l.getBillDescription().trim().toLowerCase(Locale.ROOT);
                p = byName.get(k);
            }
            ProductVariant v = variantByName.get(k);
            String sku = v != null && v.getSku() != null && !v.getSku().isBlank() ? v.getSku()
                    : p != null ? p.getSku() : null;
            m.put("sku", sku);
            m.put("barcode", p != null ? p.getBarcode() : null);
            m.put("name", l.getBillDescription() != null && !l.getBillDescription().isBlank() ? l.getBillDescription() : name);
            m.put("hsnCode", l.getHsnCode() != null ? l.getHsnCode() : l.getSacCode());
            m.put("unit", l.getUnitOfMeasure());
            m.put("quantity", l.getQuantity() == null ? 1 : l.getQuantity());
            m.put("unitPrice", l.getUnitPrice());
            m.put("taxableValue", l.getTaxableValue());
            m.put("gstRate", inter ? nz(l.getIgstRate()) : nz(l.getCgstRate()) + nz(l.getSgstRate()));
            m.put("cgst", nz(l.getCgstAmount()));
            m.put("sgst", nz(l.getSgstAmount()));
            m.put("igst", nz(l.getIgstAmount()));
            m.put("cessRate", nz(l.getCessRate()));
            m.put("cess", nz(l.getCessAmount()));
            m.put("lineTotal", l.getTotalAmount());
            lines.add(m);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("invoiceNumber", inv.getInvoiceNumber());
        body.put("orderRef", String.valueOf(inv.getOrderId()));
        body.put("invoiceDate", inv.getInvoiceDate() == null ? null : inv.getInvoiceDate().toString());
        body.put("customerName", inv.getBuyerName());
        body.put("customerPhone", phone);
        body.put("customerGstin", blankToNull(inv.getBuyerGstin()));
        body.put("customerAddress", inv.getDeliveryAddress() != null ? inv.getDeliveryAddress() : inv.getBuyerAddress());
        body.put("placeOfSupplyStateCode", placeOfSupply(inv));
        body.put("interState", inter);
        body.put("discount", nz(inv.getDiscountAmount()));
        body.put("taxableValue", inv.getTaxableAmount());
        body.put("cgst", nz(inv.getCgstAmount()));
        body.put("sgst", nz(inv.getSgstAmount()));
        body.put("igst", nz(inv.getIgstAmount()));
        body.put("cess", nz(inv.getTotalCess()));
        body.put("totalTax", inv.getTotalTax());
        body.put("grandTotal", inv.getTotalAmount());
        body.put("lines", lines);
        return body;
    }

    private JsonNode post(Map<String, Object> body) {
        String url = baseUrl.replaceAll("/+$", "") + "/api/connect/orders";
        HttpResponse<String> res;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("X-Api-Key", key.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending to Cauvery Billing");
        } catch (Exception e) {
            throw new IllegalStateException("Could not reach Cauvery Billing: " + e.getMessage());
        }
        JsonNode reply;
        try {
            reply = res.body() == null || res.body().isBlank() ? json.createObjectNode() : json.readTree(res.body());
        } catch (Exception e) {
            reply = json.createObjectNode();
        }
        if (res.statusCode() == 200 || res.statusCode() == 201) return reply;
        StringBuilder why = new StringBuilder("Cauvery Billing said ").append(res.statusCode());
        if (reply.hasNonNull("error")) why.append(": ").append(reply.get("error").asText());
        if (reply.path("problems").isArray()) {
            for (JsonNode p : reply.get("problems")) why.append(" ").append(p.asText());
        }
        throw new IllegalStateException(why.toString());
    }

    /** Two-digit state code: delivery state, then buyer's state, then the start of "33-Tamil Nadu". */
    static String placeOfSupply(GstInvoice inv) {
        for (String s : new String[]{inv.getDeliveryStateCode(), inv.getBuyerStateCode(), inv.getPlaceOfSupply()}) {
            if (s == null) continue;
            String t = s.trim();
            if (t.length() >= 2 && Character.isDigit(t.charAt(0)) && Character.isDigit(t.charAt(1))) {
                return t.substring(0, 2);
            }
        }
        return null;
    }

    Set<Long> storeSellerIds() {
        Set<Long> ids = new HashSet<>();
        if (ownSellerIdsSetting != null) {
            for (String part : ownSellerIdsSetting.split(",")) {
                try { if (!part.isBlank()) ids.add(Long.parseLong(part.trim())); } catch (NumberFormatException ignored) { }
            }
        }
        for (User u : users.findByRole(Role.ADMIN)) ids.add(u.getId());
        for (User u : users.findByRole(Role.SUPER_ADMIN)) ids.add(u.getId());
        return ids;
    }

    public long count(String status) { return records.countByStatus(status); }

    public List<BillingLinkRecord> recentFailures() { return records.findTop50ByStatusOrderByUpdatedAtDesc(BillingLinkRecord.FAILED); }

    private static double nz(Double d) { return d == null ? 0 : d; }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
