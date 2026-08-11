package com.cauverystore.controller;

import com.cauverystore.entities.GstConfiguration;
import com.cauverystore.entities.GstInvoice;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.service.CreditNoteService;
import com.cauverystore.service.GstInvoiceService;
import com.cauverystore.service.Gstr1ExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gst")
public class GstInvoiceController {

    private final GstInvoiceService gstService;
    private final Gstr1ExportService gstr1ExportService;
    private final CreditNoteService creditNoteService;
    private final UserRepository userRepo;

    public GstInvoiceController(GstInvoiceService gstService, CreditNoteService creditNoteService, UserRepository userRepo,
                                Gstr1ExportService gstr1ExportService) {
        this.gstr1ExportService = gstr1ExportService;
        this.gstService = gstService;
        this.creditNoteService = creditNoteService;
        this.userRepo = userRepo;
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

    @PostMapping("/invoice/generate")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> generateInvoice(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        Long orderId = body.get("orderId") != null ? ((Number) body.get("orderId")).longValue() : null;
        String gstin = (String) body.get("gstin");
        String buyerGstin = (String) body.get("buyerGstin");
        String deliveryStateCode = (String) body.get("deliveryStateCode");
        if (orderId == null) return ResponseEntity.badRequest().body(Map.of("error", "orderId is required"));
        if (gstin == null) return ResponseEntity.badRequest().body(Map.of("error", "gstin is required"));
        try {
            return ResponseEntity.ok(gstService.generateInvoiceFromOrder(orderId, userId, gstin, buyerGstin, deliveryStateCode));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/invoice")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> generateInvoiceAlias(@RequestBody Map<String, Object> body) {
        return generateInvoice(body);
    }

    @PostMapping("/invoices/bulk-generate")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> bulkGenerateInvoices(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String gstin = (String) body.get("gstin");
        if (gstin == null) return ResponseEntity.badRequest().body(Map.of("error", "gstin is required"));
        Object rawOrderIds = body.get("orderIds");
        if (!(rawOrderIds instanceof List<?>)) {
            return ResponseEntity.badRequest().body(Map.of("error", "orderIds (array of order ids) is required"));
        }
        List<Long> orderIds = new ArrayList<>();
        for (Object o : (List<?>) rawOrderIds) {
            if (o instanceof Number) orderIds.add(((Number) o).longValue());
        }
        try {
            return ResponseEntity.ok(gstService.bulkGenerateInvoices(orderIds, userId, gstin));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/creditnote")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> generateCreditNote(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        Long invoiceId = body.get("invoiceId") != null ? ((Number) body.get("invoiceId")).longValue() : null;
        if (invoiceId == null) return ResponseEntity.badRequest().body(Map.of("error", "invoiceId is required"));
        Double amount = body.get("amount") != null ? ((Number) body.get("amount")).doubleValue() : null;
        String reason = (String) body.get("reason");
        try {
            return ResponseEntity.ok(creditNoteService.generateManualCreditNote(invoiceId, amount, reason, userId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/creditnotes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listCreditNotes(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String role = getCurrentUserRole();
        return ResponseEntity.ok(creditNoteService.listCreditNotes(userId, role, page, size));
    }

    @GetMapping("/creditnote/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCreditNote(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String role = getCurrentUserRole();
        try {
            return ResponseEntity.ok(creditNoteService.getCreditNoteById(id, userId, role));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Access denied")) {
                return ResponseEntity.status(403).body(Map.of("error", "You do not have permission to view this credit note"));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @GetMapping("/creditnote/{id}/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> downloadCreditNotePdf(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String role = getCurrentUserRole();
        try {
            creditNoteService.getCreditNoteById(id, userId, role);
            byte[] pdf = creditNoteService.generateCreditNotePdf(id);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "credit-note-" + id + ".pdf");
            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Access denied")) {
                return ResponseEntity.status(403).body(Map.of("error", "You do not have permission to download this credit note"));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/creditnotes/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> exportCreditNotes(@RequestParam(defaultValue = "csv") String format,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "1000") int size) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String role = getCurrentUserRole();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> data = creditNoteService.listCreditNotes(userId, role, page, size);
        Object content = data.get("content");
        if (content instanceof List<?>) {
            for (Object o : (List<?>) content) {
                rows.add(toRow(o));
            }
        }
        String[] headers = {"creditNoteNumber", "creditNoteDate", "orderId", "invoiceId", "buyerName", "buyerGstin", "taxableAmount", "cgst", "sgst", "igst", "totalTax", "totalAmount", "reason"};
        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(gstService.exportJson(rows));
        }
        if ("xlsx".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=credit-notes.xlsx")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(gstService.exportExcel("Credit Notes", headers, rows));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=credit-notes.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(gstService.exportCsv(headers, rows));
    }

    private Map<String, Object> toRow(Object o) {
        if (o instanceof com.cauverystore.entities.CreditNote cn) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("creditNoteNumber", cn.getCreditNoteNumber());
            m.put("creditNoteDate", cn.getCreditNoteDate() != null ? cn.getCreditNoteDate().toString() : "");
            m.put("orderId", cn.getOrderId());
            m.put("invoiceId", cn.getInvoiceId());
            m.put("buyerName", cn.getBuyerName());
            m.put("buyerGstin", cn.getBuyerGstin());
            m.put("taxableAmount", cn.getTaxableAmount());
            m.put("cgst", cn.getCgstAmount());
            m.put("sgst", cn.getSgstAmount());
            m.put("igst", cn.getIgstAmount());
            m.put("totalTax", cn.getTotalTax());
            m.put("totalAmount", cn.getTotalAmount());
            m.put("reason", cn.getReason());
            return m;
        }
        return Map.of();
    }

    @GetMapping("/returns/gstr1")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getGstr1Alias(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return getGstr1(startDate, endDate);
    }

    @GetMapping("/gstr3b")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getGstr3b(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(creditNoteService.getGstr3bData(userId, startDate, endDate));
    }

    @GetMapping("/gstr3b/export")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> exportGstr3b(@RequestParam(defaultValue = "csv") String format,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        List<Map<String, Object>> rows = creditNoteService.getGstr3bRows(userId, startDate, endDate);
        String[] headers = {"section", "taxableValue", "cgst", "sgst", "igst"};
        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(gstService.exportJson(creditNoteService.getGstr3bData(userId, startDate, endDate)));
        }
        if ("xlsx".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gstr3b.xlsx")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(gstService.exportExcel("GSTR-3B", headers, rows));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gstr3b.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(gstService.exportCsv(headers, rows));
    }

    @GetMapping("/gstr1/export")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> exportGstr1(@RequestParam(defaultValue = "csv") String format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        List<Map<String, Object>> rows = gstService.getGstr1Data(userId, startDate, endDate);
        String[] headers = {"invoiceNumber", "invoiceDate", "buyerGstin", "buyerName", "taxableAmount", "cgst", "sgst", "igst", "totalTax", "totalAmount", "placeOfSupply", "isInterState", "invoiceType", "itcEligible", "irn", "status"};
        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(gstService.exportJson(rows));
        }
        if ("xlsx".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gstr1.xlsx")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(gstService.exportExcel("GSTR-1", headers, rows));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gstr1.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(gstService.exportCsv(headers, rows));
    }

    /**
     * GSTR-1 in the offline utility's format, ready to upload.
     *
     * `section` picks the table: b2b (4A), b2cl (5A), b2cs (7), cdn (9A/9B) or hsn (12). Omit
     * it to get every section as JSON, which is the quickest way to see what a month actually
     * contains before filing any of it.
     */
    @GetMapping("/gstr1/filing")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> gstr1ForFiling(
            @RequestParam(required = false) String section,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));

        Map<String, List<Map<String, Object>>> sections =
                gstService.getGstr1ForFiling(userId, startDate, endDate);
        if (section == null || section.isBlank()) {
            return ResponseEntity.ok(sections);
        }
        List<Map<String, Object>> rows = sections.get(section.toLowerCase());
        if (rows == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unknown GSTR-1 section '" + section + "'. Use b2b, b2cl, b2cs, cdn or hsn."));
        }
        if ("json".equalsIgnoreCase(format)) return ResponseEntity.ok(rows);

        String[] headers = gstr1ExportService.headersFor(section);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=gstr1-" + section.toLowerCase() + ".csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(gstService.exportCsv(headers, rows));
    }

    /** GSTR-1 rows narrowed to a trade type and/or category, for compliance segmentation. */
    @GetMapping("/gstr1/by-business")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> gstr1ByBusiness(
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String businessCategory,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(gstService.getGstr1DataFiltered(
                userId, startDate, endDate, businessType, businessCategory));
    }

    /** Taxable value and tax collected grouped by trade type and category. */
    @GetMapping("/reports/business-segments")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> businessSegments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(gstService.getGstBusinessSegmentSummary(userId, startDate, endDate));
    }

    @GetMapping("/invoice/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getInvoice(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String role = getCurrentUserRole();
        try {
            return ResponseEntity.ok(gstService.getInvoiceById(id, userId, role));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Access denied")) {
                return ResponseEntity.status(403).body(Map.of("error", "You do not have permission to view this invoice"));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @GetMapping("/invoice/by-order/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getInvoiceByOrder(@PathVariable Long orderId) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String role = getCurrentUserRole();
        try {
            return ResponseEntity.ok(gstService.getInvoiceByOrder(orderId, userId, role));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Access denied")) {
                return ResponseEntity.status(403).body(Map.of("error", "You do not have permission to view this invoice"));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @GetMapping("/my-order-invoice/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyOrderInvoice(@PathVariable Long orderId) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        try {
            return ResponseEntity.ok(gstService.getCustomerInvoiceForOrder(orderId, userId));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("not yet generated")) {
                return ResponseEntity.status(404).body(Map.of("error", msg, "notGenerated", true));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @GetMapping("/invoice/{id}/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> downloadInvoicePdf(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String role = getCurrentUserRole();
        try {
            gstService.getInvoiceById(id, userId, role);
            byte[] pdf = gstService.generateInvoicePdf(id);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "invoice-" + id + ".pdf");
            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Access denied")) {
                return ResponseEntity.status(403).body(Map.of("error", "You do not have permission to download this invoice"));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my-order-invoice/{orderId}/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> downloadMyOrderInvoicePdf(@PathVariable Long orderId) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        try {
            var result = gstService.getCustomerInvoiceForOrder(orderId, userId);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> resultMap = (java.util.Map<String, Object>) result;
            Object invObj = resultMap.get("invoice");
            if (invObj instanceof GstInvoice) {
                GstInvoice inv = (GstInvoice) invObj;
                byte[] pdf = gstService.generateInvoicePdf(inv.getId());
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.setContentDispositionFormData("attachment", "invoice-" + inv.getInvoiceNumber() + ".pdf");
                return ResponseEntity.ok().headers(headers).body(pdf);
            }
            return ResponseEntity.badRequest().body(Map.of("error", "Invoice data not found"));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("not yet generated")) {
                return ResponseEntity.status(404).body(Map.of("error", msg, "notGenerated", true));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/invoices")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listInvoices(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        String role = getCurrentUserRole();
        return ResponseEntity.ok(gstService.listInvoices(userId, role, page, size));
    }

    @GetMapping("/admin/invoices")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> listAllInvoices(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(gstService.listAllInvoices(page, size));
    }

    @PostMapping("/invoice/{id}/sync")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> markSynced(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String irn = (String) body.get("irn");
        String qrCode = (String) body.get("qrCode");
        String ackNo = (String) body.get("ackNo");
        String ackDate = (String) body.get("ackDate");
        return ResponseEntity.ok(gstService.markSyncedToGstn(id, irn, qrCode, ackNo, ackDate));
    }

    @PostMapping("/invoice/{id}/sync-failed")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> markSyncFailed(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String error = (String) body.get("error");
        return ResponseEntity.ok(gstService.markSyncFailed(id, error));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getSummary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(gstService.getGstSummary(userId, startDate, endDate));
    }

    @GetMapping("/gstr1")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getGstr1(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(gstService.getGstr1Data(userId, startDate, endDate));
    }

    @GetMapping("/gstr1/segregated")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getGstr1Segregated(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(gstService.getGstr1Segregated(userId, startDate, endDate));
    }

    @GetMapping("/tcs")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getTcsSummary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(gstService.getTcsSummary(userId, startDate, endDate));
    }

    @GetMapping("/configurations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getConfigurations() {
        return ResponseEntity.ok(gstService.getConfigurations());
    }

    @PostMapping("/configurations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> saveConfiguration(@RequestBody GstConfiguration config) {
        return ResponseEntity.ok(gstService.saveConfiguration(config));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getDashboard() {
        return ResponseEntity.ok(gstService.getDashboardStats());
    }

    @PostMapping("/sync/process")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> processSyncQueue(@RequestParam(defaultValue = "10") int batchSize) {
        int processed = gstService.processSyncQueue(batchSize);
        return ResponseEntity.ok(Map.of("processed", processed, "message", "Sync queue processed"));
    }

    @GetMapping("/state-codes")
    public ResponseEntity<?> getStateCodes() {
        return ResponseEntity.ok(gstService.getStateCodes());
    }
}
