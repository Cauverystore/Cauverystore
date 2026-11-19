package com.cauverystore.erp;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

import static com.cauverystore.erp.ErpNextSetupService.*;

/**
 * Mirrors the store's OWN trading into ERPNext: what it buys, holds and sells itself.
 *
 *   product sold by the store        -> Item (stock item, HSN, GST rate)
 *   customer tax invoice             -> Sales Invoice, stock taken out of the warehouse
 *   credit note (return/adjustment)  -> Sales Invoice marked as a return, stock put back for returns
 *   order paid (Razorpay or COD)     -> Payment Entry received, set against the invoice
 *   refund paid to a customer        -> Payment Entry paid to the customer
 *   purchase order marked RECEIVED   -> Purchase Invoice, always a DRAFT for checking against the
 *                                       supplier's own bill before it is submitted
 *   stock count on the website       -> nightly Stock Reconciliation where the ERP disagrees
 *
 * Sellers' orders are left out on purpose: those are the sellers' own sales. The store's side of
 * them - commission and payouts - is mirrored by {@link ErpNextSyncService}.
 *
 * Each record is sent once. Its ERPNext name is kept in erp_sync_records; the website number is
 * also written into the "Cauvery Store Ref" field so a retry finds the document instead of making
 * a second one. A record that needs another first (a payment needs its invoice submitted) is
 * marked WAITING, which is not an error and is retried every run.
 */
@Service
public class ErpNextStoreSyncService {

    private static final Logger log = LoggerFactory.getLogger(ErpNextStoreSyncService.class);
    static final int MAX_AUTO_ATTEMPTS = 5;

    private final ErpNextClient erp;
    private final ErpNextSetupService setup;
    private final ErpSyncRecordRepository records;
    private final ProductRepository products;
    private final GstInvoiceRepository invoices;
    private final CreditNoteRepository creditNotes;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final OrderRepository orders;
    private final PurchaseOrderRepository purchaseOrders;
    private final UserRepository users;
    private final GstConfigurationRepository gstConfigs;

    @Value("${erpnext.company:}") private String company;
    @Value("${erpnext.abbr:}") private String abbr;
    @Value("${erpnext.customer-group:Commercial}") private String businessCustomerGroup;
    @Value("${erpnext.individual-customer-group:Individual}") private String individualCustomerGroup;
    @Value("${erpnext.supplier-group:All Supplier Groups}") private String supplierGroup;
    @Value("${erpnext.territory:India}") private String territory;
    @Value("${erpnext.warehouse:}") private String warehouseSetting;
    @Value("${erpnext.account.bank:}") private String bankAccount;
    @Value("${erpnext.account.razorpay:}") private String razorpayAccount;
    @Value("${erpnext.account.cod:}") private String codAccount;
    @Value("${erpnext.submit-invoices:true}") private boolean submitInvoices;
    @Value("${erpnext.require-matching-totals:true}") private boolean requireMatchingTotals;
    @Value("${erpnext.item-tax-template:GST {rate}% - {abbr}}") private String itemTaxTemplate;
    @Value("${erpnext.nil-tax-template:Nil-Rated - {abbr}}") private String nilTaxTemplate;
    @Value("${erpnext.stock-align.enabled:true}") private boolean stockAlignEnabled;

    public ErpNextStoreSyncService(ErpNextClient erp, ErpNextSetupService setup, ErpSyncRecordRepository records,
                                   ProductRepository products, GstInvoiceRepository invoices,
                                   CreditNoteRepository creditNotes, PaymentRepository payments,
                                   RefundRepository refunds, OrderRepository orders,
                                   PurchaseOrderRepository purchaseOrders, UserRepository users,
                                   GstConfigurationRepository gstConfigs) {
        this.erp = erp;
        this.setup = setup;
        this.records = records;
        this.products = products;
        this.invoices = invoices;
        this.creditNotes = creditNotes;
        this.payments = payments;
        this.refunds = refunds;
        this.orders = orders;
        this.purchaseOrders = purchaseOrders;
        this.users = users;
        this.gstConfigs = gstConfigs;
    }

    /** Not a failure: this record needs another one in ERPNext first. */
    static class WaitingException extends RuntimeException {
        WaitingException(String message) { super(message); }
    }

    // ================================================================ run

    /** Sends everything not yet in ERPNext, in the order the documents depend on each other. */
    public Map<String, Object> syncAll(boolean force) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!erp.isConfigured()) {
            result.put("skipped", "ERPNext is not configured");
            return result;
        }
        setup.ensure();
        Tally t = new Tally();
        Set<Long> store = storeSellerIds();

        for (Product p : products.findAll()) {
            if (!isStore(p.getSellerId(), store)) continue;
            String fp = fingerprint(p);
            t.add(runOne(ErpSyncRecord.ITEM, p.getId(), force, fp, () -> pushItem(p)), "product " + p.getId());
        }
        for (PurchaseOrder po : purchaseOrders.findAll()) {
            if (!"RECEIVED".equalsIgnoreCase(po.getStatus())) continue;
            t.add(runOne(ErpSyncRecord.PURCHASE_BILL, po.getId(), force, null, () -> pushPurchaseBill(po)),
                    "purchase order " + po.getPoNumber());
        }
        List<GstInvoice> storeInvoices = new ArrayList<>();
        for (GstInvoice inv : invoices.findAll()) {
            if (!isStore(inv.getSellerId(), store)) continue;
            storeInvoices.add(inv);
            if (isCancelled(inv.getStatus()) || "DRAFT".equalsIgnoreCase(inv.getStatus())) continue;
            t.add(runOne(ErpSyncRecord.SALES_INVOICE, inv.getId(), force, null, () -> pushSalesInvoice(inv)),
                    "invoice " + inv.getInvoiceNumber());
        }
        for (CreditNote cn : creditNotes.findAll()) {
            if (!"CREDIT".equalsIgnoreCase(cn.getNoteType()) || !isStore(cn.getSellerId(), store)) continue;
            if (isCancelled(cn.getStatus())) continue;
            t.add(runOne(ErpSyncRecord.SALES_RETURN, cn.getId(), force, null, () -> pushReturn(cn)),
                    "credit note " + cn.getCreditNoteNumber());
        }
        for (GstInvoice inv : storeInvoices) {
            if (isCancelled(inv.getStatus()) || inv.getOrderId() == null) continue;
            Order order = orders.findById(inv.getOrderId()).orElse(null);
            if (order == null || !order.isPaid()) continue;
            t.add(runOne(ErpSyncRecord.CUSTOMER_PAYMENT, order.getId(), force, null, () -> pushPayment(order, inv)),
                    "payment for order " + order.getId());
        }
        for (Refund r : refunds.findAll()) {
            if (!"COMPLETED".equalsIgnoreCase(r.getStatus()) || r.getOrderId() == null) continue;
            GstInvoice inv = invoices.findByOrderId(r.getOrderId()).orElse(null);
            if (inv == null || !isStore(inv.getSellerId(), store)) continue;
            t.add(runOne(ErpSyncRecord.CUSTOMER_REFUND, r.getId(), force, null, () -> pushRefund(r, inv)),
                    "refund " + r.getId());
        }
        for (GstInvoice inv : storeInvoices) {
            if (isCancelled(inv.getStatus())) t.cancelled += cancelInErp(inv) ? 1 : 0;
        }

        result.put("synced", t.ok);
        result.put("failed", t.failed);
        result.put("waiting", t.waiting);
        result.put("cancelledInErp", t.cancelled);
        result.put("failedItems", t.errors);
        return result;
    }

    private static class Tally {
        int ok, failed, waiting, cancelled;
        List<String> errors = new ArrayList<>();
        void add(Outcome o, String what) {
            switch (o) {
                case OK -> ok++;
                case FAILED -> { failed++; errors.add(what); }
                case WAITING -> waiting++;
                default -> { }
            }
        }
    }

    enum Outcome { OK, FAILED, WAITING, SKIPPED }

    /**
     * Runs one record. A synced record is skipped unless its fingerprint changed (products); a
     * failed one is retried up to MAX_AUTO_ATTEMPTS times unless forced; a waiting one every time.
     */
    Outcome runOne(String type, Long id, boolean force, String fingerprint, Supplier<String> action) {
        Optional<ErpSyncRecord> existing = records.findByEntityTypeAndEntityId(type, id);
        if (existing.isPresent()) {
            ErpSyncRecord r = existing.get();
            if (ErpSyncRecord.SYNCED.equals(r.getStatus())
                    && (fingerprint == null || fingerprint.equals(r.getFingerprint()))) return Outcome.SKIPPED;
            if (ErpSyncRecord.CANCELLED.equals(r.getStatus())) return Outcome.SKIPPED;
            if (ErpSyncRecord.FAILED.equals(r.getStatus()) && !force && r.getAttempts() >= MAX_AUTO_ATTEMPTS) {
                return Outcome.SKIPPED;
            }
        }
        ErpSyncRecord rec = existing.orElseGet(() -> {
            ErpSyncRecord n = new ErpSyncRecord();
            n.setEntityType(type);
            n.setEntityId(id);
            return n;
        });
        try {
            String docName = action.get();
            rec.setErpDocName(docName);
            rec.setStatus(ErpSyncRecord.SYNCED);
            rec.setErrorMessage(null);
            rec.setFingerprint(fingerprint);
            records.save(rec);
            return Outcome.OK;
        } catch (WaitingException w) {
            rec.setStatus(ErpSyncRecord.WAITING);
            rec.setErrorMessage(w.getMessage());
            records.save(rec);
            return Outcome.WAITING;
        } catch (Exception e) {
            log.warn("ERPNext sync failed for {} {}: {}", type, id, e.getMessage());
            rec.setStatus(ErpSyncRecord.FAILED);
            rec.setAttempts(rec.getAttempts() + 1);
            rec.setErrorMessage(e.getMessage());
            records.save(rec);
            return Outcome.FAILED;
        }
    }

    // ================================================================ items

    static String itemCode(Long productId) { return "CS-P" + productId; }

    String pushItem(Product p) {
        String code = itemCode(p.getId());
        String hsn = p.getHsnCode() == null ? null : p.getHsnCode().trim();
        if (hsn == null || hsn.isEmpty()) {
            throw new ErpNextClient.ErpException("Product " + p.getId() + " (" + p.getName()
                    + ") has no HSN code. Add one on the product page; the ERP needs it for GST.");
        }
        setup.ensureHsn(hsn, p.getBillDescription() != null ? p.getBillDescription() : p.getName());

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("item_name", truncate(firstNonBlank(p.getName(), code), 140));
        fields.put("gst_hsn_code", hsn);
        fields.put("description", truncate(firstNonBlank(p.getShortDescription(), p.getName(), code), 1000));
        fields.put("disabled", p.isActive() ? 0 : 1);
        fields.put(REF_FIELD, "P" + p.getId());

        if (erp.exists("Item", code)) {
            erp.update("Item", code, fields);
            return code;
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("item_code", code);
        doc.putAll(fields);
        doc.put("item_group", PRODUCT_GROUP);
        doc.put("stock_uom", "Nos");
        doc.put("is_stock_item", 1);
        if (p.getPrice() != null) doc.put("standard_rate", p.getPrice());
        double valuation = valuationRate(p);
        if (valuation > 0) doc.put("valuation_rate", valuation);
        erp.create("Item", doc);
        return code;
    }

    /** Changes only when something the ERP item shows changes. */
    static String fingerprint(Product p) {
        String s = String.join("|", String.valueOf(p.getName()), String.valueOf(p.getHsnCode()),
                String.valueOf(p.getShortDescription()), String.valueOf(p.isActive()));
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /** Cost of one unit for stock value: the trade price if known, else the price without GST. */
    static double valuationRate(Product p) {
        if (p.getWholesalePrice() != null && p.getWholesalePrice() > 0) return p.getWholesalePrice();
        if (p.getPrice() == null || p.getPrice() <= 0) return 0;
        double gst = p.getGstPercentage() == null ? 0 : p.getGstPercentage();
        return round2(p.getPrice() / (1 + gst / 100.0));
    }

    /** Makes sure the product's item is in ERPNext before a document uses it. */
    private String requireItem(Product p) {
        ErpSyncRecord r = records.findByEntityTypeAndEntityId(ErpSyncRecord.ITEM, p.getId()).orElse(null);
        if (r != null && ErpSyncRecord.SYNCED.equals(r.getStatus())) return r.getErpDocName();
        throw new WaitingException("Waiting for product " + p.getId() + " (" + p.getName()
                + ") to reach the ERP as an item" + (r != null && r.getErrorMessage() != null
                ? ": " + r.getErrorMessage() : ""));
    }

    // ================================================================ customer invoices

    String pushSalesInvoice(GstInvoice inv) {
        requireSetting(company, "erpnext.company");
        requireSetting(abbr, "erpnext.abbr");
        String ref = "INV-" + inv.getInvoiceNumber();

        String already = erp.findNameByField("Sales Invoice", REF_FIELD, ref);
        if (already != null) return finishExisting("Sales Invoice", already, inv.getTotalAmount(), ref);

        String customer = ensureCustomer(inv.getCustomerId(), inv.getBuyerName(), inv.getCustomerEmail(),
                inv.getBuyerGstin(), inv.getBuyerAddress(), stateCodeOf(inv.getBuyerStateCode(), inv.getPlaceOfSupply()));
        String address = ensureCustomerAddress(customer, inv.getBuyerGstin(), inv.getBuyerAddress(),
                stateCodeOf(inv.getBuyerStateCode(), inv.getPlaceOfSupply()));

        boolean inter = Boolean.TRUE.equals(inv.getIsInterState());
        Map<String, Product> byName = productsOfOrder(inv.getOrderId());
        List<Map<String, Object>> items = new ArrayList<>();
        boolean anyStock = false;
        for (GstInvoiceItem line : inv.getItems()) {
            Map<String, Object> row = lineRow(line.getProductName(), line.getBillDescription(), line.getHsnCode(),
                    line.getSacCode(), qtyOf(line.getQuantity()), line.getTaxableValue(),
                    rateOf(inter, line.getIgstRate(), line.getCgstRate(), line.getSgstRate()), byName, false);
            anyStock |= row.containsKey("warehouse");
            items.add(row);
        }
        if (items.isEmpty()) throw new ErpNextClient.ErpException("Invoice " + inv.getInvoiceNumber() + " has no lines");

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("customer", customer);
        doc.put("company", company);
        doc.put("posting_date", inv.getInvoiceDate().toString());
        doc.put("set_posting_time", 1);
        doc.put("due_date", inv.getInvoiceDate().toString());
        doc.put("currency", "INR");
        doc.put(REF_FIELD, ref);
        doc.put("po_no", inv.getInvoiceNumber());
        doc.put("remarks", "Website invoice " + inv.getInvoiceNumber() + " for order " + inv.getOrderId());
        doc.put("taxes_and_charges", (inter ? "Output GST Out-state - " : "Output GST In-state - ") + abbr);
        if (inv.getPlaceOfSupply() != null) doc.put("place_of_supply", inv.getPlaceOfSupply());
        if (address != null) doc.put("customer_address", address);
        doc.put("disable_rounded_total", 1);   // the website does not round; the totals must agree
        if (anyStock) {
            doc.put("update_stock", 1);
            doc.put("set_warehouse", warehouse());
        }
        doc.put("items", items);

        JsonNode created = erp.create("Sales Invoice", doc);
        String name = created.get("name").asText();
        checkTotals(ref, inv.getTotalAmount(), created.path("grand_total").asDouble(Double.NaN), name, totalQty(items));
        if (submitInvoices) erp.submit("Sales Invoice", name);
        return name;
    }

    // ================================================================ credit notes

    String pushReturn(CreditNote cn) {
        requireSetting(company, "erpnext.company");
        requireSetting(abbr, "erpnext.abbr");
        String ref = "CN-" + cn.getCreditNoteNumber();

        String already = erp.findNameByField("Sales Invoice", REF_FIELD, ref);
        if (already != null) return finishExisting("Sales Invoice", already, -Math.abs(nz(cn.getTotalAmount())), ref);

        if (cn.getInvoiceId() == null) {
            throw new ErpNextClient.ErpException("Credit note " + cn.getCreditNoteNumber()
                    + " is not linked to an invoice, so the ERP cannot tell which sale it reverses");
        }
        ErpSyncRecord original = records.findByEntityTypeAndEntityId(ErpSyncRecord.SALES_INVOICE, cn.getInvoiceId())
                .filter(r -> ErpSyncRecord.SYNCED.equals(r.getStatus())).orElse(null);
        if (original == null) {
            throw new WaitingException("Waiting for invoice " + cn.getOriginalInvoiceNumber() + " to reach the ERP");
        }
        JsonNode originalDoc = erp.get("Sales Invoice", original.getErpDocName());
        if (originalDoc == null) {
            throw new ErpNextClient.ErpException("ERPNext invoice " + original.getErpDocName() + " for "
                    + cn.getOriginalInvoiceNumber() + " no longer exists");
        }
        boolean originalSubmitted = originalDoc.path("docstatus").asInt(0) == 1;
        if (submitInvoices && !originalSubmitted) {
            throw new WaitingException("Waiting for ERP invoice " + original.getErpDocName() + " to be submitted");
        }

        boolean inter = Boolean.TRUE.equals(cn.getIsInterState());
        boolean goodsBack = "RETURN".equalsIgnoreCase(cn.getReferenceType());
        Map<String, Product> byName = productsOfOrder(cn.getOrderId());
        List<Map<String, Object>> items = new ArrayList<>();
        boolean anyStock = false;
        for (CreditNoteItem line : cn.getItems()) {
            Map<String, Object> row = lineRow(line.getProductName(), null, line.getHsnCode(), line.getSacCode(),
                    -qtyOf(line.getQuantity()), -Math.abs(nz(line.getTaxableValue())),
                    rateOf(inter, line.getIgstRate(), line.getCgstRate(), line.getSgstRate()), byName, !goodsBack);
            anyStock |= row.containsKey("warehouse");
            items.add(row);
        }
        if (items.isEmpty()) throw new ErpNextClient.ErpException("Credit note " + cn.getCreditNoteNumber() + " has no lines");

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("customer", originalDoc.path("customer").asText());
        doc.put("company", company);
        doc.put("is_return", 1);
        if (originalSubmitted) doc.put("return_against", original.getErpDocName());
        LocalDate date = cn.getCreditNoteDate() != null ? cn.getCreditNoteDate() : LocalDate.now();
        doc.put("posting_date", date.toString());
        doc.put("set_posting_time", 1);
        doc.put("currency", "INR");
        doc.put(REF_FIELD, ref);
        doc.put("po_no", cn.getCreditNoteNumber());
        doc.put("remarks", "Website credit note " + cn.getCreditNoteNumber() + " against "
                + cn.getOriginalInvoiceNumber() + (cn.getReason() != null ? " - " + cn.getReason() : ""));
        doc.put("taxes_and_charges", (inter ? "Output GST Out-state - " : "Output GST In-state - ") + abbr);
        if (cn.getPlaceOfSupply() != null) doc.put("place_of_supply", cn.getPlaceOfSupply());
        String address = originalDoc.path("customer_address").asText(null);
        if (address != null && !address.isBlank()) doc.put("customer_address", address);
        doc.put("disable_rounded_total", 1);
        if (anyStock) {
            doc.put("update_stock", 1);
            doc.put("set_warehouse", warehouse());
        }
        doc.put("items", items);

        JsonNode created = erp.create("Sales Invoice", doc);
        String name = created.get("name").asText();
        checkTotals(ref, -Math.abs(nz(cn.getTotalAmount())), created.path("grand_total").asDouble(Double.NaN),
                name, totalQty(items));
        if (submitInvoices) erp.submit("Sales Invoice", name);
        return name;
    }

    // ================================================================ money in and out

    String pushPayment(Order order, GstInvoice inv) {
        requireSetting(company, "erpnext.company");
        String ref = "PAY-O" + order.getId();
        String already = erp.findNameByField("Payment Entry", REF_FIELD, ref);
        if (already != null) return already;

        ErpSyncRecord si = records.findByEntityTypeAndEntityId(ErpSyncRecord.SALES_INVOICE, inv.getId())
                .filter(r -> ErpSyncRecord.SYNCED.equals(r.getStatus())).orElse(null);
        if (si == null) throw new WaitingException("Waiting for invoice " + inv.getInvoiceNumber() + " to reach the ERP");
        JsonNode siDoc = erp.get("Sales Invoice", si.getErpDocName());
        if (siDoc == null) throw new ErpNextClient.ErpException("ERPNext invoice " + si.getErpDocName() + " no longer exists");
        if (siDoc.path("docstatus").asInt(0) != 1) {
            throw new WaitingException("Waiting for ERP invoice " + si.getErpDocName()
                    + " to be submitted (payments can only be set against a submitted invoice)");
        }

        Payment payment = payments.findByOrderId(order.getId()).orElse(null);
        boolean cod = isCod(order, payment);
        String account = cod ? firstNonBlank(codAccount, bankAccount) : firstNonBlank(razorpayAccount, bankAccount);
        requireSetting(account, cod ? "erpnext.account.cod (or erpnext.account.bank)"
                : "erpnext.account.razorpay (or erpnext.account.bank)");

        double outstanding = siDoc.path("outstanding_amount").asDouble(0);
        double amount = round2(nz(inv.getTotalAmount()));
        if (outstanding <= 0) {
            throw new ErpNextClient.ErpException("ERP invoice " + si.getErpDocName()
                    + " has nothing outstanding; was this payment entered by hand?");
        }
        double allocated = Math.min(amount, outstanding);
        LocalDate date = payment != null && payment.getPaidAt() != null ? payment.getPaidAt().toLocalDate()
                : order.getDeliveredAt() != null ? order.getDeliveredAt().toLocalDate() : inv.getInvoiceDate();
        String refNo = payment != null && payment.getRazorpayPaymentId() != null
                ? payment.getRazorpayPaymentId() : (cod ? "COD-ORDER-" : "ORDER-") + order.getId();

        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("reference_doctype", "Sales Invoice");
        reference.put("reference_name", si.getErpDocName());
        reference.put("allocated_amount", allocated);

        Map<String, Object> pe = new LinkedHashMap<>();
        pe.put("payment_type", "Receive");
        pe.put("company", company);
        pe.put("party_type", "Customer");
        pe.put("party", siDoc.path("customer").asText());
        pe.put("posting_date", date.toString());
        pe.put("paid_to", account);
        pe.put("paid_amount", amount);
        pe.put("received_amount", amount);
        pe.put("reference_no", refNo);
        pe.put("reference_date", date.toString());
        pe.put(REF_FIELD, ref);
        pe.put("remarks", (cod ? "Cash on delivery" : "Razorpay") + " payment for website order " + order.getId()
                + ", invoice " + inv.getInvoiceNumber());
        pe.put("references", List.of(reference));

        JsonNode created = erp.create("Payment Entry", pe);
        String name = created.get("name").asText();
        if (submitInvoices) erp.submit("Payment Entry", name);
        return name;
    }

    /**
     * Money paid back to the customer. Left unallocated: it settles the customer's credit from the
     * return in their account, and ERPNext's Payment Reconciliation can pair the two if wanted.
     */
    String pushRefund(Refund r, GstInvoice inv) {
        requireSetting(company, "erpnext.company");
        String ref = "REF-" + r.getId();
        String already = erp.findNameByField("Payment Entry", REF_FIELD, ref);
        if (already != null) return already;

        ErpSyncRecord si = records.findByEntityTypeAndEntityId(ErpSyncRecord.SALES_INVOICE, inv.getId())
                .filter(x -> ErpSyncRecord.SYNCED.equals(x.getStatus())).orElse(null);
        if (si == null) throw new WaitingException("Waiting for invoice " + inv.getInvoiceNumber() + " to reach the ERP");
        JsonNode siDoc = erp.get("Sales Invoice", si.getErpDocName());
        if (siDoc == null) throw new ErpNextClient.ErpException("ERPNext invoice " + si.getErpDocName() + " no longer exists");

        Order order = orders.findById(r.getOrderId()).orElse(null);
        Payment payment = payments.findByOrderId(r.getOrderId()).orElse(null);
        boolean cod = order != null && isCod(order, payment);
        String account = cod ? firstNonBlank(codAccount, bankAccount) : firstNonBlank(razorpayAccount, bankAccount);
        requireSetting(account, "erpnext.account.razorpay / erpnext.account.cod (or erpnext.account.bank)");

        LocalDate date = r.getRefundDate() != null ? r.getRefundDate() : LocalDate.now();
        double amount = round2(nz(r.getAmount()));
        if (amount <= 0) throw new ErpNextClient.ErpException("Refund " + r.getId() + " has no amount");

        Map<String, Object> pe = new LinkedHashMap<>();
        pe.put("payment_type", "Pay");
        pe.put("company", company);
        pe.put("party_type", "Customer");
        pe.put("party", siDoc.path("customer").asText());
        pe.put("posting_date", date.toString());
        pe.put("paid_from", account);
        pe.put("paid_amount", amount);
        pe.put("received_amount", amount);
        pe.put("reference_no", firstNonBlank(r.getGatewayRefundId(), "REFUND-" + r.getId()));
        pe.put("reference_date", date.toString());
        pe.put(REF_FIELD, ref);
        pe.put("remarks", "Refund " + r.getId() + " for website order " + r.getOrderId()
                + (r.getReason() != null ? " - " + r.getReason() : ""));

        JsonNode created = erp.create("Payment Entry", pe);
        String name = created.get("name").asText();
        if (submitInvoices) erp.submit("Payment Entry", name);
        return name;
    }

    private static boolean isCod(Order order, Payment payment) {
        if (payment != null && payment.getStatus() != null && payment.getStatus().startsWith("COD")) return true;
        String m = order.getPaymentMethod();
        return m != null && m.toUpperCase().contains("COD");
    }

    // ================================================================ purchases

    /** Always a draft: the supplier's own bill number and GST must be checked before it is submitted. */
    String pushPurchaseBill(PurchaseOrder po) {
        requireSetting(company, "erpnext.company");
        requireSetting(abbr, "erpnext.abbr");
        String ref = "PO-" + po.getPoNumber();
        String already = erp.findNameByField("Purchase Invoice", REF_FIELD, ref);
        if (already != null) return already;
        if (po.getSupplier() == null) throw new ErpNextClient.ErpException("Purchase order " + po.getPoNumber() + " has no supplier");

        com.cauverystore.entities.Supplier sup = po.getSupplier();
        String supplierState = supplierStateCode(sup);
        String storeState = storeStateCode();
        boolean inter = supplierState != null && storeState != null && !supplierState.equals(storeState);
        String supplier = ensureSupplier(sup, supplierState);

        List<Map<String, Object>> items = new ArrayList<>();
        for (PurchaseOrderItem line : po.getItems()) {
            if (line.getProduct() == null) continue;
            int qty = line.getReceivedQuantity() != null && line.getReceivedQuantity() > 0
                    ? line.getReceivedQuantity() : qtyOf(line.getQuantity());
            if (qty <= 0) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item_code", requireItem(line.getProduct()));
            row.put("qty", qty);
            row.put("rate", round2(nz(line.getUnitPrice())));
            row.put("warehouse", warehouse());
            String tpl = taxTemplate(line.getProduct().getGstPercentage());
            if (tpl != null) row.put("item_tax_template", tpl);
            items.add(row);
        }
        if (items.isEmpty()) throw new ErpNextClient.ErpException("Purchase order " + po.getPoNumber() + " has no received items");

        LocalDate date = po.getActualDelivery() != null ? po.getActualDelivery()
                : po.getOrderDate() != null ? po.getOrderDate() : LocalDate.now();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("supplier", supplier);
        doc.put("company", company);
        doc.put("posting_date", date.toString());
        doc.put("set_posting_time", 1);
        doc.put("bill_date", date.toString());
        doc.put("currency", "INR");
        doc.put(REF_FIELD, ref);
        doc.put("update_stock", 1);
        doc.put("set_warehouse", warehouse());
        doc.put("taxes_and_charges", (inter ? "Input GST Out-state - " : "Input GST In-state - ") + abbr);
        doc.put("remarks", "Website purchase order " + po.getPoNumber() + ". CHECK BEFORE SUBMITTING: enter the "
                + "supplier's bill number and compare the rates and GST with their actual bill. The website "
                + "records unit prices before GST.");
        doc.put("items", items);

        JsonNode created = erp.create("Purchase Invoice", doc);
        return created.get("name").asText();
    }

    private String ensureSupplier(com.cauverystore.entities.Supplier s, String stateCode) {
        String ref = "SUP" + s.getId();
        String found = erp.findNameByField("Supplier", REF_FIELD, ref);
        if (found != null) return found;
        boolean registered = s.getGstin() != null && !s.getGstin().isBlank();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("supplier_name", truncate(firstNonBlank(s.getName(), "Supplier " + s.getId()), 140));
        doc.put("supplier_group", supplierGroup);
        doc.put("supplier_type", "Company");
        doc.put("gst_category", registered ? "Registered Regular" : "Unregistered");
        if (registered) doc.put("gstin", s.getGstin().trim().toUpperCase());
        doc.put(REF_FIELD, ref);
        String name = erp.create("Supplier", doc).get("name").asText();

        String stateName = ErpNextSyncService.stateName(stateCode);
        if (stateName != null) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("address_title", name);
            a.put("address_type", "Billing");
            a.put("address_line1", firstNonBlank(s.getAddress(), "Address not recorded"));
            a.put("city", firstNonBlank(s.getCity(), stateName));
            a.put("state", stateName);
            a.put("country", "India");
            if (s.getPincode() != null && !s.getPincode().isBlank()) a.put("pincode", s.getPincode());
            a.put("gst_category", registered ? "Registered Regular" : "Unregistered");
            if (registered) a.put("gstin", s.getGstin().trim().toUpperCase());
            a.put("is_primary_address", 1);
            a.put("links", List.of(Map.of("link_doctype", "Supplier", "link_name", name)));
            erp.create("Address", a);
        }
        return name;
    }

    // ================================================================ stock

    /**
     * Brings the ERP's stock count in line with the website's, which is where stock is kept up to
     * date (orders, returns and manual changes all happen there). Only when documents are being
     * submitted: draft invoices do not move stock, so aligning against them would count twice.
     */
    public Map<String, Object> alignStock() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!erp.isConfigured()) { result.put("skipped", "ERPNext is not configured"); return result; }
        if (!stockAlignEnabled) { result.put("skipped", "Stock alignment is turned off"); return result; }
        if (!submitInvoices) {
            result.put("skipped", "Documents are kept as drafts (test mode), so ERP stock is not aligned yet");
            return result;
        }
        String wh = warehouse();
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<Long> store = storeSellerIds();
        for (Product p : products.findAll()) {
            if (!isStore(p.getSellerId(), store)) continue;
            ErpSyncRecord r = records.findByEntityTypeAndEntityId(ErpSyncRecord.ITEM, p.getId()).orElse(null);
            if (r == null || !ErpSyncRecord.SYNCED.equals(r.getStatus())) continue;
            int site = Math.max(0, p.getStock() == null ? 0 : p.getStock());
            JsonNode bins = erp.list("Bin", Map.of("item_code", r.getErpDocName(), "warehouse", wh),
                    List.of("actual_qty"), 1);
            double inErp = bins.isEmpty() ? 0 : bins.get(0).path("actual_qty").asDouble(0);
            if (Math.abs(inErp - site) < 0.0001) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item_code", r.getErpDocName());
            row.put("warehouse", wh);
            row.put("qty", site);
            double rate = valuationRate(p);
            if (rate > 0) row.put("valuation_rate", rate);
            rows.add(row);
        }
        result.put("itemsChanged", rows.size());
        if (rows.isEmpty()) return result;

        String ref = "STOCK-" + LocalDate.now();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("company", company);
        doc.put("purpose", "Stock Reconciliation");
        doc.put("posting_date", LocalDate.now().toString());
        doc.put(REF_FIELD, ref);
        doc.put("items", rows);
        String name = erp.create("Stock Reconciliation", doc).get("name").asText();
        erp.submit("Stock Reconciliation", name);
        result.put("document", name);
        return result;
    }

    // ================================================================ cancellations

    /** An invoice cancelled on the website after it reached the ERP is cancelled there too. */
    private boolean cancelInErp(GstInvoice inv) {
        ErpSyncRecord r = records.findByEntityTypeAndEntityId(ErpSyncRecord.SALES_INVOICE, inv.getId()).orElse(null);
        if (r == null || !ErpSyncRecord.SYNCED.equals(r.getStatus())) return false;
        try {
            JsonNode doc = erp.get("Sales Invoice", r.getErpDocName());
            if (doc != null) {
                int status = doc.path("docstatus").asInt(0);
                if (status == 0) erp.delete("Sales Invoice", r.getErpDocName());
                else if (status == 1) erp.cancel("Sales Invoice", r.getErpDocName());
            }
            r.setStatus(ErpSyncRecord.CANCELLED);
            r.setErrorMessage("Cancelled on the website; cancelled in the ERP too");
            records.save(r);
            return true;
        } catch (Exception e) {
            log.warn("Could not cancel ERPNext invoice {} for {}: {}", r.getErpDocName(), inv.getInvoiceNumber(), e.getMessage());
            r.setErrorMessage("Cancelled on the website, but cancelling " + r.getErpDocName()
                    + " in the ERP failed: " + e.getMessage() + ". Cancel it in ERPNext by hand.");
            records.save(r);
            return false;
        }
    }

    // ================================================================ customers

    private String ensureCustomer(Long customerId, String buyerName, String email, String gstin,
                                  String address, String stateCode) {
        boolean registered = gstin != null && !gstin.isBlank();
        String ref = customerId != null ? "C" + customerId : "E-" + firstNonBlank(email, buyerName, "unknown");
        if (registered) ref = ref + "-" + gstin.trim().toUpperCase();   // a business buyer is its own account
        String found = erp.findNameByField("Customer", REF_FIELD, ref);
        if (found != null) return found;

        String label = firstNonBlank(buyerName, email, "Customer");
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("customer_name", truncate(label, 120) + " [" + ref + "]");
        c.put("customer_type", registered ? "Company" : "Individual");
        c.put("customer_group", registered ? businessCustomerGroup : individualCustomerGroup);
        c.put("territory", territory);
        c.put("gst_category", registered ? "Registered Regular" : "Unregistered");
        if (registered) c.put("gstin", gstin.trim().toUpperCase());
        if (email != null && !email.isBlank()) c.put("email_id", email);
        c.put(REF_FIELD, ref);
        return erp.create("Customer", c).get("name").asText();
    }

    /** One billing address per customer and state; India Compliance reads GSTIN and state from it. */
    private String ensureCustomerAddress(String customer, String gstin, String address, String stateCode) {
        String stateName = ErpNextSyncService.stateName(stateCode);
        if (stateName == null) return null;
        String title = customer + " - " + stateCode;
        String existing = erp.findNameByField("Address", "address_title", title);
        if (existing != null) return existing;
        boolean registered = gstin != null && !gstin.isBlank();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("address_title", title);
        a.put("address_type", "Billing");
        a.put("address_line1", truncate(firstNonBlank(address, "Address not recorded"), 140));
        a.put("city", stateName);
        a.put("state", stateName);
        a.put("country", "India");
        a.put("gst_category", registered ? "Registered Regular" : "Unregistered");
        if (registered) a.put("gstin", gstin.trim().toUpperCase());
        a.put("links", List.of(Map.of("link_doctype", "Customer", "link_name", customer)));
        return erp.create("Address", a).get("name").asText();
    }

    // ================================================================ invoice lines

    /**
     * One ERPNext line. A product line uses the product's item and moves stock (unless noStock); a
     * charge such as delivery or gift wrap uses the shared non-stock charges item. Either way the
     * line keeps the website's HSN/SAC and GST rate.
     */
    private Map<String, Object> lineRow(String productName, String billDescription, String hsn, String sac,
                                        int qty, Double taxableValue, double gstRate,
                                        Map<String, Product> byName, boolean noStock) {
        Product product = productName == null ? null : byName.get(productName.trim().toLowerCase());
        if (product == null && billDescription != null) product = byName.get(billDescription.trim().toLowerCase());
        double taxable = nz(taxableValue);
        Map<String, Object> row = new LinkedHashMap<>();
        if (product != null) {
            row.put("item_code", requireItem(product));
            row.put("qty", qty);
            row.put("rate", qty == 0 ? 0 : round2(taxable / qty));
            if (!noStock) row.put("warehouse", warehouse());
        } else {
            int sign = qty < 0 || taxable < 0 ? -1 : 1;
            row.put("item_code", CHARGES_ITEM);
            row.put("item_name", truncate(firstNonBlank(productName, CHARGES_ITEM), 140));
            row.put("description", firstNonBlank(productName, CHARGES_ITEM));
            row.put("qty", sign);
            row.put("rate", round2(Math.abs(taxable)));
        }
        String code = firstNonBlank(hsn, sac);
        if (code != null) {
            setup.ensureHsn(code.trim(), productName);
            row.put("gst_hsn_code", code.trim());
        }
        String tpl = taxTemplate(gstRate);
        if (tpl != null) row.put("item_tax_template", tpl);
        return row;
    }

    private Map<String, Product> productsOfOrder(Long orderId) {
        Map<String, Product> m = new HashMap<>();
        if (orderId == null) return m;
        orders.findById(orderId).ifPresent(o -> {
            if (o.getItems() == null) return;
            for (OrderItem i : o.getItems()) {
                Product p = i.getProduct();
                if (p == null) continue;
                if (p.getName() != null) m.putIfAbsent(p.getName().trim().toLowerCase(), p);
                if (p.getBillDescription() != null) m.putIfAbsent(p.getBillDescription().trim().toLowerCase(), p);
            }
        });
        return m;
    }

    /** "GST 18% - CS" style name, as India Compliance creates them; "Nil-Rated - CS" for 0%. */
    String taxTemplate(Double rate) {
        if (rate == null) return null;
        if (rate <= 0) return nilTaxTemplate.replace("{abbr}", abbr);
        String r = BigDecimal.valueOf(rate).stripTrailingZeros().toPlainString();
        return itemTaxTemplate.replace("{rate}", r).replace("{abbr}", abbr);
    }

    static double rateOf(boolean inter, Double igst, Double cgst, Double sgst) {
        return inter ? nz(igst) : nz(cgst) + nz(sgst);
    }

    // ================================================================ checks and helpers

    /** Re-uses a document a previous attempt already made; only a submitted one counts as done. */
    private String finishExisting(String doctype, String name, double expectedTotal, String ref) {
        JsonNode existing = erp.get(doctype, name);
        if (existing == null) return name;
        int status = existing.path("docstatus").asInt(0);
        if (status == 1 || !submitInvoices) return name;
        if (status == 2) {
            throw new ErpNextClient.ErpException("ERPNext " + doctype + " " + name + " for " + ref
                    + " is cancelled; resolve it in ERPNext before syncing again");
        }
        checkTotals(ref, expectedTotal, existing.path("grand_total").asDouble(Double.NaN), name,
                Math.abs(existing.path("total_qty").asDouble(1)));
        erp.submit(doctype, name);
        return name;
    }

    /**
     * The two systems must agree before anything is posted. Each line's rate is rounded to the
     * paisa, so up to half a paisa per unit of difference is allowed.
     */
    void checkTotals(String ref, double expected, double inErp, String erpName, double units) {
        double tolerance = 0.05 + 0.005 * Math.max(units, 1);
        boolean ok = !Double.isNaN(inErp) && Math.abs(inErp - expected) <= tolerance;
        if (ok) return;
        if (!requireMatchingTotals) {
            log.warn("TEST MODE: totals differ for {} (website {} vs ERPNext {}), keeping {} anyway",
                    ref, expected, inErp, erpName);
            return;
        }
        throw new ErpNextClient.ErpException("Total mismatch for " + ref + ": website " + expected
                + " vs ERPNext " + inErp + " (left as draft " + erpName + " to check)");
    }

    private static double totalQty(List<Map<String, Object>> items) {
        double q = 0;
        for (Map<String, Object> i : items) q += Math.abs(((Number) i.getOrDefault("qty", 0)).doubleValue());
        return q;
    }

    String warehouse() {
        if (warehouseSetting != null && !warehouseSetting.isBlank()) return warehouseSetting;
        requireSetting(abbr, "erpnext.abbr");
        return "Stores - " + abbr;
    }

    /** The store itself sells under the accounts of its admins; their user ids are its seller ids. */
    Set<Long> storeSellerIds() {
        Set<Long> ids = new HashSet<>();
        for (User u : users.findByRole(Role.ADMIN)) ids.add(u.getId());
        for (User u : users.findByRole(Role.SUPER_ADMIN)) ids.add(u.getId());
        return ids;
    }

    static boolean isStore(Long sellerId, Set<Long> storeIds) {
        return sellerId == null || storeIds.contains(sellerId);
    }

    private String storeStateCode() {
        return gstConfigs.findByIsActiveTrue().stream()
                .filter(c -> c.getSellerId() == null)
                .map(GstConfiguration::getStateCode)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    /** From the GSTIN's first two digits if there is one, else from the state name. */
    static String supplierStateCode(com.cauverystore.entities.Supplier s) {
        if (s.getGstin() != null && s.getGstin().trim().length() >= 2
                && Character.isDigit(s.getGstin().trim().charAt(0))) {
            return s.getGstin().trim().substring(0, 2);
        }
        if (s.getState() == null) return null;
        String want = s.getState().trim().toLowerCase();
        for (int i = 1; i <= 38; i++) {
            String code = String.format("%02d", i);
            String name = ErpNextSyncService.stateName(code);
            if (name != null && name.toLowerCase().equals(want)) return code;
        }
        return null;
    }

    static String stateCodeOf(String stateCode, String placeOfSupply) {
        String c = firstNonBlank(stateCode, placeOfSupply);
        if (c == null) return null;
        c = c.trim();
        return c.length() >= 2 && Character.isDigit(c.charAt(0)) && Character.isDigit(c.charAt(1)) ? c.substring(0, 2) : null;
    }

    private static boolean isCancelled(String status) {
        return status != null && status.toUpperCase().startsWith("CANCEL");
    }

    private static int qtyOf(Integer q) { return q == null ? 1 : q; }

    static double nz(Double d) { return d == null ? 0 : d; }

    static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private static void requireSetting(String v, String key) {
        if (v == null || v.isBlank()) throw new ErpNextClient.ErpException("Missing setting " + key);
    }

    static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
