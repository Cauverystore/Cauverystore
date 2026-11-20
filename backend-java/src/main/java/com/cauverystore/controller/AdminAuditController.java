package com.cauverystore.controller;

import com.cauverystore.entities.AuditLog;
import com.cauverystore.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminAuditController {
    private final AuditService auditService;
    public AdminAuditController(AuditService auditService) { this.auditService = auditService; }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        Page<AuditLog> logsPage = auditService.search(action, email, from, to, page, size);
        return ResponseEntity.ok(Map.of(
                "content", logsPage.getContent(),
                "totalElements", logsPage.getTotalElements(),
                "totalPages", logsPage.getTotalPages(),
                "currentPage", logsPage.getNumber()
        ));
    }

    /** The different kinds of entry in the log, for the filter list on the Audit Logs page. */
    @GetMapping("/actions")
    public ResponseEntity<java.util.List<String>> actions() {
        return ResponseEntity.ok(auditService.listActions());
    }

    /** Seller-scoped auth/compliance history (login, logout, onboarding steps, password resets). */
    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<Map<String, Object>> getForSeller(@PathVariable Long sellerId) {
        return ResponseEntity.ok(Map.of("sellerId", sellerId, "logs", auditService.getByUser(sellerId)));
    }
}
