package com.cauverystore.controller;

import com.cauverystore.entities.GstConfiguration;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.service.GstInvoiceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/gst")
@CrossOrigin("*")
public class GstInvoiceController {

    private final GstInvoiceService gstService;
    private final UserRepository userRepo;

    public GstInvoiceController(GstInvoiceService gstService, UserRepository userRepo) {
        this.gstService = gstService;
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

    @PostMapping("/invoice/generate")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> generateInvoice(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        Long orderId = body.get("orderId") != null ? ((Number) body.get("orderId")).longValue() : null;
        String gstin = (String) body.get("gstin");
        String buyerGstin = (String) body.get("buyerGstin");
        if (orderId == null) return ResponseEntity.badRequest().body(Map.of("error", "orderId is required"));
        if (gstin == null) return ResponseEntity.badRequest().body(Map.of("error", "gstin is required"));
        try {
            return ResponseEntity.ok(gstService.generateInvoiceFromOrder(orderId, userId, gstin, buyerGstin));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/invoice/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getInvoice(@PathVariable Long id) {
        return ResponseEntity.ok(gstService.getInvoiceById(id));
    }

    @GetMapping("/invoice/by-order/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getInvoiceByOrder(@PathVariable Long orderId) {
        try {
            return ResponseEntity.ok(gstService.getInvoiceByOrder(orderId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> listInvoices(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(gstService.listInvoices(userId, page, size));
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
