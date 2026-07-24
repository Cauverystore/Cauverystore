package com.cauverystore.controller;

import com.cauverystore.entities.AuditLog;
import com.cauverystore.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@CrossOrigin("*")
public class AdminAuditController {
    private final AuditService auditService;
    public AdminAuditController(AuditService auditService) { this.auditService = auditService; }

    @GetMapping
    public ResponseEntity<List<AuditLog>> getAll() { return ResponseEntity.ok(auditService.getAll()); }
}
