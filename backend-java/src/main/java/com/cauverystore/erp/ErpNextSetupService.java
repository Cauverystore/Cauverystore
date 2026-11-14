package com.cauverystore.erp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds to ERPNext what the store link needs and a fresh ERPNext does not have:
 *
 *   - a read-only "Cauvery Store Ref" field on invoices, payments, items, customers and suppliers,
 *     holding the website's own number so a retry finds the document instead of making a second
 *     one, and so anyone in ERPNext can see where a document came from;
 *   - item groups for the store's products and for charges such as delivery;
 *   - a non-stock item for delivery, gift wrap and other charges on a customer invoice;
 *   - "allow negative stock", optional: the store also ships straight from suppliers, so a sale can
 *     be recorded before the goods were ever booked into the ERP's warehouse.
 *
 * Everything here is safe to run again: what exists is left alone. It runs by itself before the
 * first sync after each start, and from the admin screen on demand.
 */
@Service
public class ErpNextSetupService {

    private static final Logger log = LoggerFactory.getLogger(ErpNextSetupService.class);

    public static final String REF_FIELD = "cauvery_ref";
    public static final String PRODUCT_GROUP = "Cauvery Store Products";
    public static final String CHARGES_GROUP = "Cauvery Store Charges";
    public static final String CHARGES_ITEM = "Delivery and Other Charges";

    /** Doctype -> field the reference goes after on the form. */
    private static final Map<String, String> REF_FIELD_PLACES = new LinkedHashMap<>();
    static {
        REF_FIELD_PLACES.put("Sales Invoice", "customer");
        REF_FIELD_PLACES.put("Purchase Invoice", "supplier");
        REF_FIELD_PLACES.put("Payment Entry", "party");
        REF_FIELD_PLACES.put("Item", "item_name");
        REF_FIELD_PLACES.put("Customer", "customer_name");
        REF_FIELD_PLACES.put("Supplier", "supplier_name");
        REF_FIELD_PLACES.put("Stock Reconciliation", "posting_date");
    }

    private final ErpNextClient erp;

    @Value("${erpnext.allow-negative-stock:true}") private boolean allowNegativeStock;

    private volatile boolean done;

    public ErpNextSetupService(ErpNextClient erp) {
        this.erp = erp;
    }

    public boolean isDone() { return done; }

    /** Runs the setup once per start; later calls return at once. */
    public void ensure() {
        if (done) return;
        List<Map<String, String>> report = run();
        boolean failed = report.stream().anyMatch(r -> "failed".equals(r.get("result")));
        if (failed) {
            throw new ErpNextClient.ErpException("ERPNext setup is incomplete: " + report.stream()
                    .filter(r -> "failed".equals(r.get("result")))
                    .map(r -> r.get("step") + " (" + r.get("detail") + ")").toList());
        }
    }

    /** Every step with what happened: "exists", "created", "updated" or "failed". */
    public synchronized List<Map<String, String>> run() {
        List<Map<String, String>> report = new ArrayList<>();
        for (Map.Entry<String, String> e : REF_FIELD_PLACES.entrySet()) {
            step(report, "Store reference field on " + e.getKey(), () -> ensureRefField(e.getKey(), e.getValue()));
        }
        step(report, "Item group " + PRODUCT_GROUP, () -> ensureItemGroup(PRODUCT_GROUP));
        step(report, "Item group " + CHARGES_GROUP, () -> ensureItemGroup(CHARGES_GROUP));
        step(report, "HSN/SAC 996511 (delivery)", () -> ensureHsn("996511", "Road transport of goods"));
        step(report, "Item " + CHARGES_ITEM, this::ensureChargesItem);
        if (allowNegativeStock) step(report, "Allow negative stock", this::ensureNegativeStock);
        done = report.stream().noneMatch(r -> "failed".equals(r.get("result")));
        return report;
    }

    private interface Step { String run(); }

    private static void step(List<Map<String, String>> report, String name, Step s) {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("step", name);
        try {
            r.put("result", s.run());
        } catch (Exception e) {
            log.warn("ERPNext setup step '{}' failed: {}", name, e.getMessage());
            r.put("result", "failed");
            r.put("detail", e.getMessage());
        }
        report.add(r);
    }

    private String ensureRefField(String doctype, String insertAfter) {
        if (erp.exists("Custom Field", doctype + "-" + REF_FIELD)) return "exists";
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("dt", doctype);
        f.put("fieldname", REF_FIELD);
        f.put("label", "Cauvery Store Ref");
        f.put("fieldtype", "Data");
        f.put("insert_after", insertAfter);
        f.put("read_only", 1);
        f.put("no_copy", 1);              // a copied document is a new one, not the website's
        f.put("in_standard_filter", 1);
        f.put("search_index", 1);
        f.put("description", "Number of the matching record on cauverystore.in. Filled by the website link.");
        erp.create("Custom Field", f);
        return "created";
    }

    private String ensureItemGroup(String name) {
        if (erp.exists("Item Group", name)) return "exists";
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("item_group_name", name);
        g.put("parent_item_group", "All Item Groups");
        g.put("is_group", 0);
        erp.create("Item Group", g);
        return "created";
    }

    /** India Compliance links HSN/SAC codes to its own list; a code missing there blocks the item. */
    String ensureHsn(String code, String description) {
        if (code == null || code.isBlank()) return "skipped";
        if (erp.exists("GST HSN Code", code)) return "exists";
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("hsn_code", code);
        h.put("description", description == null || description.isBlank() ? code : truncate(description, 140));
        erp.create("GST HSN Code", h);
        return "created";
    }

    private String ensureChargesItem() {
        if (erp.exists("Item", CHARGES_ITEM)) return "exists";
        Map<String, Object> i = new LinkedHashMap<>();
        i.put("item_code", CHARGES_ITEM);
        i.put("item_name", CHARGES_ITEM);
        i.put("item_group", CHARGES_GROUP);
        i.put("stock_uom", "Nos");
        i.put("is_stock_item", 0);
        i.put("gst_hsn_code", "996511");
        i.put("description", "Delivery, gift wrap and other charges on a customer invoice. "
                + "Each invoice line carries its own HSN/SAC and GST rate from the website.");
        erp.create("Item", i);
        return "created";
    }

    private String ensureNegativeStock() {
        com.fasterxml.jackson.databind.JsonNode s = erp.get("Stock Settings", "Stock Settings");
        if (s != null && s.path("allow_negative_stock").asInt(0) == 1) return "exists";
        erp.update("Stock Settings", "Stock Settings", Map.of("allow_negative_stock", 1));
        return "updated";
    }

    static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
