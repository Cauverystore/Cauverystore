package com.cauverystore.controller;

import com.cauverystore.client.GstnClient;
import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.repository.GstInvoiceRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.repository.TcsRecordRepository;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.service.ComplianceService;
import com.cauverystore.service.DebitNoteService;
import com.cauverystore.service.GstInvoiceService;
import com.cauverystore.service.MarketplaceImportService;
import com.cauverystore.service.ReconciliationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gst")
public class GstComplianceController {

    private final GstInvoiceService gstService;
    private final DebitNoteService debitNoteService;
    private final ComplianceService complianceService;
    private final ReconciliationService reconciliationService;
    private final MarketplaceImportService marketplaceImportService;
    private final GstnClient gstnClient;
    private final GstInvoiceRepository invoiceRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final TcsRecordRepository tcsRepo;
    private final UserRepository userRepo;
    private final com.cauverystore.repository.SellerApobRepository apobRepo;

    public GstComplianceController(GstInvoiceService gstService, DebitNoteService debitNoteService,
                                   ComplianceService complianceService, ReconciliationService reconciliationService,
                                   MarketplaceImportService marketplaceImportService, GstnClient gstnClient,
                                   GstInvoiceRepository invoiceRepo, SellerRegistrationRepository sellerRegRepo,
                                   TcsRecordRepository tcsRepo, UserRepository userRepo,
                                   com.cauverystore.repository.SellerApobRepository apobRepo) {
        this.gstService = gstService;
        this.debitNoteService = debitNoteService;
        this.complianceService = complianceService;
        this.reconciliationService = reconciliationService;
        this.marketplaceImportService = marketplaceImportService;
        this.gstnClient = gstnClient;
        this.invoiceRepo = invoiceRepo;
        this.sellerRegRepo = sellerRegRepo;
        this.tcsRepo = tcsRepo;
        this.userRepo = userRepo;
        this.apobRepo = apobRepo;
    }

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object details = auth.getDetails();
        if (details instanceof Long) return (Long) details;
        String name = auth.getName();
        var user = userRepo.findByEmail(name);
        if (user == null) user = userRepo.findByUsername(name);
        return user != null ? user.getId() : null;
    }

    private String getCurrentUserRole() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "CUSTOMER";
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .findFirst().orElse("CUSTOMER");
    }

    // ------------------------------------------------------------------
    // Debit notes
    // ------------------------------------------------------------------

    @PostMapping("/debitnote")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> generateDebitNote(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        Long invoiceId = body.get("invoiceId") != null ? ((Number) body.get("invoiceId")).longValue() : null;
        if (invoiceId == null) return ResponseEntity.badRequest().body(Map.of("error", "invoiceId is required"));
        Double amount = body.get("amount") != null ? ((Number) body.get("amount")).doubleValue() : null;
        String reason = (String) body.get("reason");
        try {
            return ResponseEntity.ok(debitNoteService.generateManualDebitNote(invoiceId, amount, reason, userId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/debitnotes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listDebitNotes(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String role = getCurrentUserRole();
        return ResponseEntity.ok(debitNoteService.listDebitNotes(userId, role, page, size));
    }

    @GetMapping("/debitnote/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDebitNote(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String role = getCurrentUserRole();
        try {
            return ResponseEntity.ok(debitNoteService.getDebitNoteById(id, userId, role));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Access denied")) {
                return ResponseEntity.status(403).body(Map.of("error", "You do not have permission to view this debit note"));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @GetMapping("/debitnote/{id}/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> downloadDebitNotePdf(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String role = getCurrentUserRole();
        try {
            debitNoteService.getDebitNoteById(id, userId, role);
            byte[] pdf = debitNoteService.generateDebitNotePdf(id);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "debit-note-" + id + ".pdf");
            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Access denied")) {
                return ResponseEntity.status(403).body(Map.of("error", "You do not have permission to download this debit note"));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/debitnotes/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> exportDebitNotes(@RequestParam(defaultValue = "csv") String format,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "1000") int size) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String role = getCurrentUserRole();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> data = debitNoteService.listDebitNotes(userId, role, page, size);
        Object content = data.get("content");
        if (content instanceof List<?>) {
            for (Object o : (List<?>) content) {
                rows.add(debitNoteRow(o));
            }
        }
        String[] headers = {"debitNoteNumber", "debitNoteDate", "orderId", "invoiceId", "buyerName", "buyerGstin", "taxableAmount", "cgst", "sgst", "igst", "totalTax", "totalAmount", "reason"};
        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(gstService.exportJson(rows));
        }
        if ("xlsx".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=debit-notes.xlsx")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(gstService.exportExcel("Debit Notes", headers, rows));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=debit-notes.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(gstService.exportCsv(headers, rows));
    }

    private Map<String, Object> debitNoteRow(Object o) {
        if (o instanceof com.cauverystore.entities.DebitNote dn) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("debitNoteNumber", dn.getDebitNoteNumber());
            m.put("debitNoteDate", dn.getDebitNoteDate() != null ? dn.getDebitNoteDate().toString() : "");
            m.put("orderId", dn.getOrderId());
            m.put("invoiceId", dn.getInvoiceId());
            m.put("buyerName", dn.getBuyerName());
            m.put("buyerGstin", dn.getBuyerGstin());
            m.put("taxableAmount", dn.getTaxableAmount());
            m.put("cgst", dn.getCgstAmount());
            m.put("sgst", dn.getSgstAmount());
            m.put("igst", dn.getIgstAmount());
            m.put("totalTax", dn.getTotalTax());
            m.put("totalAmount", dn.getTotalAmount());
            m.put("reason", dn.getReason());
            return m;
        }
        return Map.of();
    }

    // ------------------------------------------------------------------
    // GSTR-9 / GSTR-9C (annual)
    // ------------------------------------------------------------------

    @GetMapping("/reports/gstr9")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getGstr9(@RequestParam(defaultValue = "0") int fiscalYear) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        if (fiscalYear <= 0) fiscalYear = LocalDate.now().getYear();
        return ResponseEntity.ok(complianceService.getGstr9Data(userId, fiscalYear));
    }

    @GetMapping("/reports/gstr9c")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getGstr9c(@RequestParam(defaultValue = "0") int fiscalYear) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        if (fiscalYear <= 0) fiscalYear = LocalDate.now().getYear();
        return ResponseEntity.ok(complianceService.getGstr9cData(userId, fiscalYear));
    }

    @GetMapping("/reports/gstr9/export")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> exportGstr9(@RequestParam(defaultValue = "0") int fiscalYear,
                                         @RequestParam(defaultValue = "json") String format) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        if (fiscalYear <= 0) fiscalYear = LocalDate.now().getYear();
        Map<String, Object> data = complianceService.getGstr9Data(userId, fiscalYear);
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("section", "Summary");
        row.put("taxableValue", data.get("taxableValue"));
        row.put("cgst", data.get("cgst"));
        row.put("sgst", data.get("sgst"));
        row.put("igst", data.get("igst"));
        row.put("totalTax", data.get("totalTax"));
        row.put("totalTcsCollected", data.get("totalTcsCollected"));
        rows.add(row);
        String[] headers = {"section", "taxableValue", "cgst", "sgst", "igst", "totalTax", "totalTcsCollected"};
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gstr9.csv")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(gstService.exportCsv(headers, rows));
        }
        if ("xlsx".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gstr9.xlsx")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(gstService.exportExcel("GSTR-9", headers, rows));
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(gstService.exportJson(data));
    }

    // ------------------------------------------------------------------
    // GSTR-8 / TCS
    // ------------------------------------------------------------------

    @GetMapping("/reports/gstr8")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getGstr8(@RequestParam(required = false) String period) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        if (period == null || period.isBlank()) period = ComplianceService.previousPeriod();
        return ResponseEntity.ok(complianceService.getGstr8Data(userId, period));
    }

    @GetMapping("/reports/gstr8/export")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> exportGstr8(@RequestParam(required = false) String period,
                                         @RequestParam(defaultValue = "csv") String format) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        if (period == null || period.isBlank()) period = ComplianceService.previousPeriod();
        List<Map<String, Object>> rows = complianceService.getGstr8Rows(userId, period);
        String[] headers = {"customerGstin", "tcsAmount"};
        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(gstService.exportJson(complianceService.getGstr8Data(userId, period)));
        }
        if ("xlsx".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gstr8.xlsx")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(gstService.exportExcel("GSTR-8", headers, rows));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gstr8.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(gstService.exportCsv(headers, rows));
    }

    @GetMapping("/compliance/tcs/{sellerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getTcsForSeller(@PathVariable Long sellerId,
                                             @RequestParam(required = false) String period) {
        List<com.cauverystore.entities.TcsRecord> records = period != null && !period.isBlank()
                ? tcsRepo.findBySellerIdAndPeriod(sellerId, period)
                : tcsRepo.findBySellerIdOrderByTransactionDateDesc(sellerId);
        double totalTcs = records.stream()
                .mapToDouble(r -> r.getTcsAmount() != null ? r.getTcsAmount() : 0.0).sum();
        long pending = records.stream().filter(r -> !"FILED".equals(r.getFilingStatus())).count();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("sellerId", sellerId);
        resp.put("records", records);
        resp.put("totalRecords", records.size());
        resp.put("totalTcsAmount", totalTcs);
        resp.put("pendingFilingCount", pending);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/reports/tcs")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getTcsRecords(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String role = getCurrentUserRole();
        Page<com.cauverystore.entities.TcsRecord> result;
        if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
            result = tcsRepo.findAll(PageRequest.of(page, size));
        } else {
            result = tcsRepo.findBySellerIdOrderByTransactionDateDesc(userId, PageRequest.of(page, size));
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", result.getContent());
        resp.put("totalPages", result.getTotalPages());
        resp.put("totalElements", result.getTotalElements());
        resp.put("page", page);
        resp.put("size", size);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/reports/tcs/export")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> exportTcs(@RequestParam(defaultValue = "csv") String format,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "1000") int size) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String role = getCurrentUserRole();
        Page<com.cauverystore.entities.TcsRecord> result;
        if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
            result = tcsRepo.findAll(PageRequest.of(page, size));
        } else {
            result = tcsRepo.findBySellerIdOrderByTransactionDateDesc(userId, PageRequest.of(page, size));
        }
        String[] headers = {"invoiceNumber", "orderId", "marketplace", "transactionDate", "customerGstin", "taxableAmount", "tcsRate", "tcsAmount", "filingStatus", "period"};
        List<Map<String, Object>> rows = new ArrayList<>();
        for (com.cauverystore.entities.TcsRecord t : result.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("invoiceNumber", t.getInvoiceNumber());
            m.put("orderId", t.getOrderId());
            m.put("marketplace", t.getMarketplace());
            m.put("transactionDate", t.getTransactionDate() != null ? t.getTransactionDate().toString() : "");
            m.put("customerGstin", t.getCustomerGstin());
            m.put("taxableAmount", t.getTaxableAmount());
            m.put("tcsRate", t.getTcsRate());
            m.put("tcsAmount", t.getTcsAmount());
            m.put("filingStatus", t.getFilingStatus());
            m.put("period", t.getPeriod());
            rows.add(m);
        }
        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(gstService.exportJson(rows));
        }
        if ("xlsx".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tcs-records.xlsx")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(gstService.exportExcel("TCS Records", headers, rows));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tcs-records.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(gstService.exportCsv(headers, rows));
    }

    // ------------------------------------------------------------------
    // E-invoice / E-way bill (via GSTN adapter)
    // ------------------------------------------------------------------

    @PostMapping("/einvoice")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> generateEinvoice(@RequestBody Map<String, Object> body) {
        Long invoiceId = body.get("invoiceId") != null ? ((Number) body.get("invoiceId")).longValue() : null;
        if (invoiceId == null) return ResponseEntity.badRequest().body(Map.of("error", "invoiceId is required"));
        GstInvoice inv = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        try {
            return ResponseEntity.ok(gstnClient.generateIrn(inv));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/ewaybill")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> generateEwayBill(@RequestBody Map<String, Object> body) {
        Long invoiceId = body.get("invoiceId") != null ? ((Number) body.get("invoiceId")).longValue() : null;
        if (invoiceId == null) return ResponseEntity.badRequest().body(Map.of("error", "invoiceId is required"));
        GstInvoice inv = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        try {
            return ResponseEntity.ok(gstnClient.generateEwayBill(inv));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/gstn/status")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getGstnStatus() {
        return ResponseEntity.ok(Map.of(
                "adapter", gstnClient.getName(),
                "simulated", gstnClient.isSimulated(),
                "message", gstnClient.isSimulated()
                        ? "Running with simulated GSTN client. Set gstn.simulated=false + credentials for live IRN/EWB/GSTR-2B."
                        : "Live GSTN client configured."));
    }

    // ------------------------------------------------------------------
    // Marketplace sync
    // ------------------------------------------------------------------

    @PostMapping("/sync/marketplace/orders")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> importMarketplaceOrders(@RequestBody Map<String, Object> body) {
        String channel = (String) body.get("channel");
        LocalDate from = body.get("from") != null ? LocalDate.parse(body.get("from").toString()) : null;
        LocalDate to = body.get("to") != null ? LocalDate.parse(body.get("to").toString()) : null;
        int limit = body.get("limit") != null ? ((Number) body.get("limit")).intValue() : 10;
        try {
            return ResponseEntity.ok(marketplaceImportService.importOrders(channel, from, to, limit));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/sync/marketplace/inventory")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> syncMarketplaceInventory(@RequestBody Map<String, Object> body) {
        String channel = (String) body.get("channel");
        Long userId = getCurrentUserId();
        try {
            return ResponseEntity.ok(marketplaceImportService.syncInventory(channel, userId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sync/marketplace/products/{channel}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getMarketplaceProducts(@PathVariable String channel) {
        try {
            return ResponseEntity.ok(marketplaceImportService.fetchProducts(channel));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sync/marketplace/settlements")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getMarketplaceSettlements(@RequestParam String channel,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            return ResponseEntity.ok(marketplaceImportService.fetchSettlements(channel, from, to));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // Seller APOB (Additional Places of Business)
    // ------------------------------------------------------------------

    @GetMapping("/seller/{sellerId}/apob")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getApob(@PathVariable Long sellerId) {
        List<com.cauverystore.entities.SellerApob> apobs = apobRepo.findBySellerIdOrderByCreatedAtDesc(sellerId);
        SellerRegistration sr = sellerRegRepo.findByUserId(sellerId).orElse(null);
        return ResponseEntity.ok(Map.of(
                "sellerId", sellerId,
                "gstin", sr != null ? sr.getGstin() : null,
                "apobs", apobs,
                "apobList", apobs.stream().map(com.cauverystore.entities.SellerApob::getAddress).toList()));
    }

    @PutMapping("/seller/{sellerId}/apob")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> updateApob(@PathVariable Long sellerId, @RequestBody Map<String, Object> body) {
        SellerRegistration sr = sellerRegRepo.findByUserId(sellerId)
                .orElseThrow(() -> new RuntimeException("Seller registration not found for user " + sellerId));
        Object raw = body.get("apobList");
        if (!(raw instanceof List<?>)) {
            return ResponseEntity.badRequest().body(Map.of("error", "apobList (array of strings) is required"));
        }
        // Backwards-compatible upsert: each string in apobList becomes a PENDING SellerApob row.
        List<com.cauverystore.entities.SellerApob> created = new ArrayList<>();
        for (Object o : (List<?>) raw) {
            if (o == null) continue;
            com.cauverystore.entities.SellerApob apob = new com.cauverystore.entities.SellerApob();
            apob.setSellerId(sellerId);
            apob.setGstin(sr.getGstin());
            apob.setAddress(String.valueOf(o));
            apob.setStatus("PENDING");
            created.add(apobRepo.save(apob));
        }
        return ResponseEntity.ok(Map.of(
                "sellerId", sellerId,
                "gstin", sr.getGstin(),
                "apobs", created,
                "message", "APOBs saved"));
    }

    // ------------------------------------------------------------------
    // GSTR-2B reconciliation
    // ------------------------------------------------------------------

    @PostMapping("/reconciliation/generate")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> generateReconciliation(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String period = (String) body.get("period");
        try {
            return ResponseEntity.ok(reconciliationService.generateReconciliation(userId, period));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> listReconciliations() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(reconciliationService.listReconciliations(userId));
    }

    @GetMapping("/reconciliation/{period}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getReconciliation(@PathVariable String period) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        try {
            return ResponseEntity.ok(reconciliationService.getReconciliation(userId, period));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/reconciliation/{id}/status")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> updateReconciliationStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        try {
            return ResponseEntity.ok(reconciliationService.updateReconciliationStatus(id, status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // Compliance dashboard & deadlines
    // ------------------------------------------------------------------

    @GetMapping("/compliance/dashboard")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getComplianceDashboard(@RequestParam(required = false) String period) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(complianceService.getComplianceDashboard(userId, period));
    }

    @PostMapping("/compliance/deadlines/sync")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> syncDeadlines(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String period = (String) body.get("period");
        if (period == null || period.isBlank()) period = ComplianceService.currentPeriod();
        return ResponseEntity.ok(complianceService.syncDeadlines(userId, period));
    }

    @GetMapping("/compliance/deadlines")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> listDeadlines() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(complianceService.listDeadlines(userId));
    }
}
