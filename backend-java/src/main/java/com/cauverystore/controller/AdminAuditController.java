package com.cauverystore.controller;

import com.cauverystore.entities.AuditLog;
import com.cauverystore.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
            @RequestParam(defaultValue = "50") int size) {
        Page<AuditLog> logsPage = auditService.getPage(PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "content", logsPage.getContent(),
                "totalElements", logsPage.getTotalElements(),
                "totalPages", logsPage.getTotalPages(),
                "currentPage", logsPage.getNumber()
        ));
    }

    /** Seller-scoped auth/compliance history (login, logout, onboarding steps, password resets). */
    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<Map<String, Object>> getForSeller(@PathVariable Long sellerId) {
        return ResponseEntity.ok(Map.of("sellerId", sellerId, "logs", auditService.getByUser(sellerId)));
    }
}
