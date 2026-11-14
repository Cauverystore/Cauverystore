package com.cauverystore.erp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** Status, record list and manual controls for the ERPNext link. Admin only. */
@RestController
@RequestMapping("/api/admin/erp")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminErpNextController {

    static final List<String> TYPES = List.of(
            ErpSyncRecord.ITEM, ErpSyncRecord.PURCHASE_BILL, ErpSyncRecord.SALES_INVOICE,
            ErpSyncRecord.SALES_RETURN, ErpSyncRecord.CUSTOMER_PAYMENT, ErpSyncRecord.CUSTOMER_REFUND,
            ErpSyncRecord.COMMISSION_INVOICE, ErpSyncRecord.SELLER_PAYOUT);
    static final List<String> STATUSES = List.of(
            ErpSyncRecord.SYNCED, ErpSyncRecord.WAITING, ErpSyncRecord.FAILED, ErpSyncRecord.CANCELLED);

    private final ErpNextClient client;
    private final ErpNextSyncService sync;
    private final ErpNextStoreSyncService storeSync;
    private final ErpNextSetupService setup;
    private final ErpSyncRecordRepository records;

    @Value("${erpnext.base-url:}") private String baseUrl;
    @Value("${erpnext.public-url:}") private String publicUrl;
    @Value("${erpnext.submit-invoices:true}") private boolean submitInvoices;
    @Value("${erpnext.require-matching-totals:true}") private boolean requireMatchingTotals;

    public AdminErpNextController(ErpNextClient client, ErpNextSyncService sync, ErpNextStoreSyncService storeSync,
                                  ErpNextSetupService setup, ErpSyncRecordRepository records) {
        this.client = client;
        this.sync = sync;
        this.storeSync = storeSync;
        this.setup = setup;
        this.records = records;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", client.isConfigured());
        m.put("setupDone", setup.isDone());
        m.put("draftsOnly", !submitInvoices);
        m.put("totalsMustMatch", requireMatchingTotals);
        m.put("erpUrl", erpUrl());
        m.put("synced", records.countByStatus(ErpSyncRecord.SYNCED));
        m.put("waiting", records.countByStatus(ErpSyncRecord.WAITING));
        m.put("failed", records.countByStatus(ErpSyncRecord.FAILED));
        Map<String, Map<String, Long>> byType = new LinkedHashMap<>();
        for (String t : TYPES) {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (String s : STATUSES) counts.put(s, records.countByEntityTypeAndStatus(t, s));
            byType.put(t, counts);
        }
        m.put("byType", byType);
        m.put("recentFailures", records.findTop50ByStatusOrderByUpdatedAtDesc(ErpSyncRecord.FAILED).stream()
                .map(r -> Map.of("type", r.getEntityType(), "id", r.getEntityId(),
                        "attempts", r.getAttempts(), "error", String.valueOf(r.getErrorMessage())))
                .toList());
        return m;
    }

    /** The latest 200 records, optionally only one type and/or status. */
    @GetMapping("/records")
    public List<Map<String, Object>> list(@RequestParam(required = false) String type,
                                          @RequestParam(required = false) String status) {
        boolean t = type != null && !type.isBlank();
        boolean s = status != null && !status.isBlank();
        List<ErpSyncRecord> rows = t && s ? records.findTop200ByEntityTypeAndStatusOrderByUpdatedAtDesc(type, status)
                : t ? records.findTop200ByEntityTypeOrderByUpdatedAtDesc(type)
                : s ? records.findTop200ByStatusOrderByUpdatedAtDesc(status)
                : records.findTop200ByOrderByUpdatedAtDesc();
        String base = erpUrl();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ErpSyncRecord r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("type", r.getEntityType());
            m.put("entityId", r.getEntityId());
            m.put("status", r.getStatus());
            m.put("erpDocName", r.getErpDocName());
            m.put("erpLink", erpLink(base, r.getEntityType(), r.getErpDocName()));
            m.put("attempts", r.getAttempts());
            m.put("message", r.getErrorMessage());
            m.put("updatedAt", r.getUpdatedAt());
            out.add(m);
        }
        return out;
    }

    /** ?force=true also retries records that used up their automatic attempts. */
    @PostMapping("/sync")
    public Map<String, Object> syncNow(@RequestParam(defaultValue = "false") boolean force) {
        return sync.syncAll(force);
    }

    /** Gives one failed record its automatic attempts back and runs a sync. */
    @PostMapping("/records/{id}/retry")
    public ResponseEntity<?> retry(@PathVariable Long id) {
        ErpSyncRecord r = records.findById(id).orElse(null);
        if (r == null) return ResponseEntity.notFound().build();
        if (ErpSyncRecord.SYNCED.equals(r.getStatus()) || ErpSyncRecord.CANCELLED.equals(r.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "This record is already done"));
        }
        r.setAttempts(0);
        records.save(r);
        Map<String, Object> result = sync.syncAll(false);
        ErpSyncRecord after = records.findById(id).orElse(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", after.getStatus());
        m.put("message", after.getErrorMessage());
        m.put("erpDocName", after.getErpDocName());
        m.put("sync", result);
        return ResponseEntity.ok(m);
    }

    /** Adds what the link needs to ERPNext (custom fields, item groups...). Safe to repeat. */
    @PostMapping("/setup")
    public List<Map<String, String>> runSetup() {
        if (!client.isConfigured()) return List.of(Map.of("step", "ERPNext connection", "result", "failed",
                "detail", "ERPNext is not configured on the server"));
        return setup.run();
    }

    /** Makes the ERP's stock count match the website's now, instead of waiting for the night run. */
    @PostMapping("/stock/align")
    public Map<String, Object> alignStock() {
        return storeSync.alignStock();
    }

    private String erpUrl() {
        String u = publicUrl != null && !publicUrl.isBlank() ? publicUrl : baseUrl;
        return u == null ? "" : u.replaceAll("/+$", "");
    }

    static String erpLink(String base, String type, String docName) {
        if (base == null || base.isBlank() || docName == null || docName.isBlank()) return null;
        String doctype = switch (type) {
            case ErpSyncRecord.ITEM -> "item";
            case ErpSyncRecord.SALES_INVOICE, ErpSyncRecord.SALES_RETURN, ErpSyncRecord.COMMISSION_INVOICE -> "sales-invoice";
            case ErpSyncRecord.CUSTOMER_PAYMENT, ErpSyncRecord.CUSTOMER_REFUND -> "payment-entry";
            case ErpSyncRecord.PURCHASE_BILL -> "purchase-invoice";
            case ErpSyncRecord.SELLER_PAYOUT -> "journal-entry";
            default -> null;
        };
        if (doctype == null) return null;
        return base + "/app/" + doctype + "/" + java.net.URLEncoder.encode(docName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
