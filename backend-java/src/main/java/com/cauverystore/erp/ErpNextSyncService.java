package com.cauverystore.erp;

import com.cauverystore.entities.CommissionInvoice;
import com.cauverystore.entities.SellerPayout;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.repository.CommissionInvoiceRepository;
import com.cauverystore.repository.SellerPayoutRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mirrors records this backend has already issued into ERPNext, for the books.
 *
 * IMPORTANT - one source of truth for GST filing. This backend issues the commission invoices and
 * produces the GSTR-1 / GSTR-8 data. ERPNext receives copies so the accounts, bank reconciliation
 * and seller payables live in one ledger. The returns must be filed from ONE of the two systems,
 * never both, or the same invoice is declared twice. Agree that with your CA before going live.
 *
 * What is mirrored:
 *   commission invoice -> ERPNext Sales Invoice to the seller (as Customer), 18% GST on SAC 998599
 *   seller payout      -> ERPNext Journal Entry: Dr seller payable (party Supplier), Cr bank
 *
 * The store's own sales, purchases, stock and customer money are mirrored by
 * {@link ErpNextStoreSyncService}, which this runs first.
 */
@Service
public class ErpNextSyncService {

    private static final Logger log = LoggerFactory.getLogger(ErpNextSyncService.class);
    private static final int MAX_AUTO_ATTEMPTS = 5;
    private static final String COMMISSION_ITEM = "Marketplace Commission";

    private final ErpNextClient erp;
    private final ErpSyncRecordRepository records;
    private final CommissionInvoiceRepository invoices;
    private final SellerPayoutRepository payouts;
    private final SellerRegistrationRepository registrations;
    private final ErpNextStoreSyncService storeSync;

    @Value("${erpnext.company:}") private String company;
    @Value("${erpnext.abbr:}") private String abbr;
    @Value("${erpnext.customer-group:Commercial}") private String customerGroup;
    @Value("${erpnext.supplier-group:All Supplier Groups}") private String supplierGroup;
    @Value("${erpnext.territory:India}") private String territory;
    @Value("${erpnext.account.seller-payable:}") private String sellerPayableAccount;
    @Value("${erpnext.account.bank:}") private String bankAccount;
    @Value("${erpnext.submit-invoices:true}") private boolean submitInvoices;
    /**
     * Keep this true for real books. Set false ONLY for plumbing tests on an ERPNext company that has
     * no GSTIN yet: ERPNext then posts the invoice without GST and the totals differ by design.
     */
    @Value("${erpnext.require-matching-totals:true}") private boolean requireMatchingTotals;

    public ErpNextSyncService(ErpNextClient erp, ErpSyncRecordRepository records,
                              CommissionInvoiceRepository invoices, SellerPayoutRepository payouts,
                              SellerRegistrationRepository registrations, ErpNextStoreSyncService storeSync) {
        this.erp = erp;
        this.records = records;
        this.invoices = invoices;
        this.payouts = payouts;
        this.registrations = registrations;
        this.storeSync = storeSync;
    }

    /** Pushes everything not yet in ERPNext. force=true also retries items that gave up. */
    public Map<String, Object> syncAll(boolean force) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!erp.isConfigured()) {
            result.put("skipped", "ERPNext is not configured");
            return result;
        }
        // The store's own trading first (products, purchases, sales, returns, money), then the
        // marketplace side. A problem in one must not stop the other.
        Map<String, Object> store;
        try {
            store = storeSync.syncAll(force);
        } catch (Exception e) {
            log.warn("ERPNext store sync stopped: {}", e.getMessage());
            store = Map.of("error", String.valueOf(e.getMessage()));
        }
        result.put("store", store);

        int ok = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        for (CommissionInvoice inv : invoices.findAll()) {
            Outcome o = runOne(ErpSyncRecord.COMMISSION_INVOICE, inv.getId(), force, () -> pushCommissionInvoice(inv));
            if (o == Outcome.OK) ok++; else if (o == Outcome.FAILED) { failed++; errors.add("invoice " + inv.getInvoiceNumber()); }
        }
        for (SellerPayout p : payouts.findAll()) {
            if (Boolean.TRUE.equals(p.getVoided())) continue;
            Outcome o = runOne(ErpSyncRecord.SELLER_PAYOUT, p.getId(), force, () -> pushPayout(p));
            if (o == Outcome.OK) ok++; else if (o == Outcome.FAILED) { failed++; errors.add("payout " + p.getId()); }
        }
        result.put("synced", ok);
        result.put("failed", failed);
        result.put("failedItems", errors);
        return result;
    }

    private enum Outcome { OK, FAILED, SKIPPED }

    private Outcome runOne(String type, Long id, boolean force, java.util.function.Supplier<String> action) {
        Optional<ErpSyncRecord> existing = records.findByEntityTypeAndEntityId(type, id);
        if (existing.isPresent()) {
            ErpSyncRecord r = existing.get();
            if (ErpSyncRecord.SYNCED.equals(r.getStatus())) return Outcome.SKIPPED;
            if (!force && r.getAttempts() >= MAX_AUTO_ATTEMPTS) return Outcome.SKIPPED;
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
            records.save(rec);
            return Outcome.OK;
        } catch (Exception e) {
            log.warn("ERPNext sync failed for {} {}: {}", type, id, e.getMessage());
            rec.setStatus(ErpSyncRecord.FAILED);
            rec.setAttempts(rec.getAttempts() + 1);
            rec.setErrorMessage(e.getMessage());
            records.save(rec);
            return Outcome.FAILED;
        }
    }

    // ---------------------------------------------------------------- commission invoice

    private String pushCommissionInvoice(CommissionInvoice inv) {
        requireSetting(company, "erpnext.company");
        requireSetting(abbr, "erpnext.abbr");

        // A retry must not create a second invoice: look for one already carrying our number.
        String already = erp.findNameByField("Sales Invoice", "po_no", inv.getInvoiceNumber());
        if (already != null) {
            // An earlier attempt may have left a draft behind (e.g. the totals disagreed). Never treat
            // that as done: re-check it, and only a submitted invoice counts as synced.
            JsonNode existing = erp.get("Sales Invoice", already);
            if (existing != null && existing.path("docstatus").asInt(0) == 1) return already;
            if (existing != null && existing.path("docstatus").asInt(0) == 2) {
                throw new ErpNextClient.ErpException("ERPNext invoice " + already + " for " + inv.getInvoiceNumber()
                        + " is cancelled; resolve it in ERPNext before syncing again");
            }
            checkTotals(inv, already, existing != null ? existing.path("grand_total").asDouble(Double.NaN) : Double.NaN);
            if (submitInvoices) erp.submit("Sales Invoice", already);
            return already;
        }

        SellerRegistration reg = registrations.findByUserId(inv.getSellerId()).orElse(null);
        String customer = ensureCustomer(inv, reg);
        String address = ensureAddress(customer, inv, reg);
        ensureCommissionItem();

        boolean inter = Boolean.TRUE.equals(inv.getIsInterState());
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("customer", customer);
        doc.put("company", company);
        doc.put("posting_date", inv.getInvoiceDate().toString());
        doc.put("set_posting_time", 1);
        doc.put("due_date", inv.getInvoiceDate().plusDays(15).toString());
        doc.put("currency", "INR");
        doc.put("po_no", inv.getInvoiceNumber());          // our number, also the idempotency key
        doc.put("po_date", inv.getInvoiceDate().toString());
        doc.put("remarks", "Marketplace commission " + inv.getPeriod() + " - backend invoice " + inv.getInvoiceNumber());
        doc.put("taxes_and_charges", (inter ? "Output GST Out-state - " : "Output GST In-state - ") + abbr);
        doc.put("place_of_supply", placeOfSupply(inv.getSellerStateCode()));
        if (address != null) doc.put("customer_address", address);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("item_code", COMMISSION_ITEM);
        item.put("qty", 1);
        item.put("rate", inv.getTaxableAmount());
        doc.put("items", List.of(item));

        JsonNode created = erp.create("Sales Invoice", doc);
        String name = created.get("name").asText();

        // The two systems must agree to the paisa before anything is posted to the ledger.
        checkTotals(inv, name, created.path("grand_total").asDouble(Double.NaN));
        if (submitInvoices) erp.submit("Sales Invoice", name);
        return name;
    }

    private void checkTotals(CommissionInvoice inv, String erpName, double erpTotal) {
        if (!requireMatchingTotals) {
            if (Double.isNaN(erpTotal) || Math.abs(erpTotal - inv.getTotalAmount()) > 0.05) {
                log.warn("TEST MODE: totals differ for {} (backend {} vs ERPNext {}), posting {} anyway",
                        inv.getInvoiceNumber(), inv.getTotalAmount(), erpTotal, erpName);
            }
            return;
        }
        if (Double.isNaN(erpTotal) || Math.abs(erpTotal - inv.getTotalAmount()) > 0.05) {
            throw new ErpNextClient.ErpException("Total mismatch for " + inv.getInvoiceNumber() + ": backend "
                    + inv.getTotalAmount() + " vs ERPNext " + erpTotal + " (left as draft " + erpName + ")");
        }
    }

    private String ensureCustomer(CommissionInvoice inv, SellerRegistration reg) {
        String base = firstNonBlank(inv.getSellerLegalName(), reg != null ? reg.getBusinessName() : null, "Seller");
        String name = base + " [S" + inv.getSellerId() + "]";   // unique even if two sellers share a name
        if (erp.exists("Customer", name)) return name;
        boolean registered = inv.getSellerGstin() != null && !inv.getSellerGstin().isBlank();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("customer_name", name);
        c.put("customer_type", "Company");
        c.put("customer_group", customerGroup);
        c.put("territory", territory);
        c.put("gst_category", registered ? "Registered Regular" : "Unregistered");
        if (reg != null && reg.getPanNumber() != null) c.put("pan", reg.getPanNumber());
        erp.create("Customer", c);
        return name;
    }

    /** India Compliance reads the buyer's GSTIN and state from the billing address. */
    private String ensureAddress(String customer, CommissionInvoice inv, SellerRegistration reg) {
        boolean registered = inv.getSellerGstin() != null && !inv.getSellerGstin().isBlank();
        String stateName = stateName(inv.getSellerStateCode());
        if (stateName == null) return null;
        String title = customer;
        String existing = erp.findNameByField("Address", "address_title", title);
        if (existing != null) return existing;
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("address_title", title);
        a.put("address_type", "Billing");
        a.put("address_line1", firstNonBlank(reg != null ? reg.getBusinessAddress() : null, "Address not recorded"));
        a.put("city", firstNonBlank(reg != null ? reg.getCity() : null, stateName));
        a.put("state", stateName);
        a.put("country", "India");
        if (reg != null && reg.getPincode() != null && !reg.getPincode().isBlank()) a.put("pincode", reg.getPincode());
        a.put("gst_category", registered ? "Registered Regular" : "Unregistered");
        if (registered) a.put("gstin", inv.getSellerGstin());
        a.put("links", List.of(Map.of("link_doctype", "Customer", "link_name", customer)));
        JsonNode created = erp.create("Address", a);
        return created.get("name").asText();
    }

    private void ensureCommissionItem() {
        if (erp.exists("Item", COMMISSION_ITEM)) return;
        Map<String, Object> i = new LinkedHashMap<>();
        i.put("item_code", COMMISSION_ITEM);
        i.put("item_name", COMMISSION_ITEM);
        i.put("item_group", "Services");
        i.put("stock_uom", "Nos");
        i.put("is_stock_item", 0);
        i.put("gst_hsn_code", "998599");   // SAC for other support services, as the backend uses
        erp.create("Item", i);
    }

    // ---------------------------------------------------------------- seller payout

    private String pushPayout(SellerPayout p) {
        requireSetting(company, "erpnext.company");
        requireSetting(sellerPayableAccount, "erpnext.account.seller-payable");
        requireSetting(bankAccount, "erpnext.account.bank");

        String marker = "backend-payout-" + p.getId();
        String already = erp.findNameByField("Journal Entry", "cheque_no", marker);
        if (already != null) return already;

        String supplier = ensureSupplier(p.getSellerId());
        LocalDate date = p.getPaidOn() != null ? p.getPaidOn() : LocalDate.now();
        double amt = p.getAmount();

        Map<String, Object> debit = new LinkedHashMap<>();
        debit.put("account", sellerPayableAccount);
        debit.put("party_type", "Supplier");
        debit.put("party", supplier);
        debit.put("debit_in_account_currency", amt);
        Map<String, Object> credit = new LinkedHashMap<>();
        credit.put("account", bankAccount);
        credit.put("credit_in_account_currency", amt);

        Map<String, Object> je = new LinkedHashMap<>();
        je.put("voucher_type", "Bank Entry");
        je.put("company", company);
        je.put("posting_date", date.toString());
        je.put("cheque_no", marker);                       // idempotency key
        je.put("cheque_date", date.toString());
        je.put("user_remark", "Seller payout " + p.getId() + (p.getReference() != null ? " ref " + p.getReference() : ""));
        je.put("accounts", List.of(debit, credit));

        JsonNode created = erp.create("Journal Entry", je);
        String name = created.get("name").asText();
        if (submitInvoices) erp.submit("Journal Entry", name);
        return name;
    }

    private String ensureSupplier(Long sellerId) {
        SellerRegistration reg = registrations.findByUserId(sellerId).orElse(null);
        String name = firstNonBlank(reg != null ? reg.getBusinessName() : null, "Seller") + " [S" + sellerId + "]";
        if (erp.exists("Supplier", name)) return name;
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("supplier_name", name);
        s.put("supplier_group", supplierGroup);
        s.put("supplier_type", "Company");
        if (reg != null && reg.getPanNumber() != null) s.put("pan", reg.getPanNumber());
        erp.create("Supplier", s);
        return name;
    }

    // ---------------------------------------------------------------- helpers

    private static void requireSetting(String v, String key) {
        if (v == null || v.isBlank()) throw new ErpNextClient.ErpException("Missing setting " + key);
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static final Map<String, String> STATES = Map.ofEntries(
            Map.entry("01", "Jammu and Kashmir"), Map.entry("02", "Himachal Pradesh"), Map.entry("03", "Punjab"),
            Map.entry("04", "Chandigarh"), Map.entry("05", "Uttarakhand"), Map.entry("06", "Haryana"),
            Map.entry("07", "Delhi"), Map.entry("08", "Rajasthan"), Map.entry("09", "Uttar Pradesh"),
            Map.entry("10", "Bihar"), Map.entry("11", "Sikkim"), Map.entry("12", "Arunachal Pradesh"),
            Map.entry("13", "Nagaland"), Map.entry("14", "Manipur"), Map.entry("15", "Mizoram"),
            Map.entry("16", "Tripura"), Map.entry("17", "Meghalaya"), Map.entry("18", "Assam"),
            Map.entry("19", "West Bengal"), Map.entry("20", "Jharkhand"), Map.entry("21", "Odisha"),
            Map.entry("22", "Chhattisgarh"), Map.entry("23", "Madhya Pradesh"), Map.entry("24", "Gujarat"),
            Map.entry("26", "Dadra and Nagar Haveli and Daman and Diu"), Map.entry("27", "Maharashtra"),
            Map.entry("29", "Karnataka"), Map.entry("30", "Goa"), Map.entry("31", "Lakshadweep"),
            Map.entry("32", "Kerala"), Map.entry("33", "Tamil Nadu"), Map.entry("34", "Puducherry"),
            Map.entry("35", "Andaman and Nicobar Islands"), Map.entry("36", "Telangana"),
            Map.entry("37", "Andhra Pradesh"), Map.entry("38", "Ladakh"));

    static String stateName(String code) { return code == null ? null : STATES.get(code); }

    /** ERPNext/India Compliance format: "33-Tamil Nadu". */
    static String placeOfSupply(String code) {
        String n = stateName(code);
        return n == null ? null : code + "-" + n;
    }
}
