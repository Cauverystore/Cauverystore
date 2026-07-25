package com.cauverystore.controller;

import com.cauverystore.dto.SellerRegistrationRequest;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.service.SellerRegistrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/seller-registration")
@CrossOrigin("*")
public class SellerRegistrationController {

    private final SellerRegistrationService registrationService;
    private final UserRepository userRepo;

    public SellerRegistrationController(SellerRegistrationService registrationService, UserRepository userRepo) {
        this.registrationService = registrationService;
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

    @PostMapping("/start")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> startRegistration() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(registrationService.startRegistration(userId));
    }

    @PostMapping("/step")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> saveStep(@RequestBody SellerRegistrationRequest req) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(registrationService.saveStep(userId, req));
    }

    @PostMapping("/document")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadDocument(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        try {
            String documentType = (String) body.get("documentType");
            String documentName = (String) body.get("documentName");
            String fileUrl = (String) body.get("fileUrl");
            String originalName = (String) body.get("fileOriginalName");
            Long fileSize = body.get("fileSize") != null ? ((Number) body.get("fileSize")).longValue() : 0L;
            String mimeType = (String) body.get("mimeType");
            java.time.LocalDate expiryDate = body.get("expiryDate") != null
                    ? java.time.LocalDate.parse((String) body.get("expiryDate")) : null;
            return ResponseEntity.ok(registrationService.uploadDocument(userId, documentType, documentName, fileUrl, originalName, fileSize, mimeType, expiryDate));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getStatus() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(registrationService.getRegistrationStatus(userId));
    }

    @GetMapping("/compliance/alerts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getComplianceAlerts() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(registrationService.getComplianceAlerts(userId));
    }

    @GetMapping("/admin/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getPendingVerifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(registrationService.getPendingVerifications(page, size));
    }

    @PostMapping("/admin/verify-document/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> verifyDocument(@PathVariable Long documentId, @RequestBody Map<String, Object> body) {
        Long adminId = getCurrentUserId();
        if (adminId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        String rejectionReason = (String) body.get("rejectionReason");
        return ResponseEntity.ok(registrationService.verifyDocument(adminId, documentId, approved, rejectionReason));
    }
}
