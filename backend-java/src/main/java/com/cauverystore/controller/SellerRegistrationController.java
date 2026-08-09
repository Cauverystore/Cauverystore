package com.cauverystore.controller;

import com.cauverystore.dto.SellerRegistrationRequest;
import com.cauverystore.entities.BusinessCategory;
import com.cauverystore.entities.BusinessType;
import com.cauverystore.entities.SellerApob;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.service.BankVerificationService;
import com.cauverystore.service.GstinVerificationService;
import com.cauverystore.service.SellerApprovalService;
import com.cauverystore.service.SellerRegistrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/seller-registration")
public class SellerRegistrationController {

    /**
     * The permitted business types and categories.
     *
     * Served from the enums rather than duplicated in the frontend, so the dropdown cannot
     * drift from what the API will accept and leave a seller picking a value that is then
     * rejected on save.
     */
    /**
     * The seller identity the billing engine consumes, in the agreed payload shape.
     *
     * Deliberately not the SellerRegistration entity, which the status endpoint returns - that
     * carries bank details, Aadhaar and document URLs, none of which belongs in a billing
     * payload.
     */
    @GetMapping("/billing-profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> myBillingProfile() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        return registrationService.getBillingProfile(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.badRequest().body(Map.of(
                        "error", "No seller registration exists for this account.")));
    }

    /** The same payload for any seller, for admins and the billing engine. */
    @GetMapping("/{sellerId}/billing-profile")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<?> billingProfile(@PathVariable Long sellerId) {
        return registrationService.getBillingProfile(sellerId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.badRequest().body(Map.of(
                        "error", "No seller registration exists for seller " + sellerId + ".")));
    }

    @GetMapping("/business-options")
    public ResponseEntity<Map<String, Object>> businessOptions() {
        return ResponseEntity.ok(Map.of(
                "businessTypes", BusinessType.labels(),
                "businessCategories", BusinessCategory.labels()));
    }

    private final SellerRegistrationService registrationService;
    private final SellerApprovalService approvalService;
    private final GstinVerificationService gstinVerificationService;
    private final BankVerificationService bankVerificationService;
    private final UserRepository userRepo;

    public SellerRegistrationController(SellerRegistrationService registrationService,
                                        SellerApprovalService approvalService,
                                        GstinVerificationService gstinVerificationService,
                                        BankVerificationService bankVerificationService,
                                        UserRepository userRepo) {
        this.registrationService = registrationService;
        this.approvalService = approvalService;
        this.gstinVerificationService = gstinVerificationService;
        this.bankVerificationService = bankVerificationService;
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

    @PostMapping("/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> submit() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(registrationService.submitRegistration(userId));
    }

    @PostMapping("/verify/gstin")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> verifyGstin(@RequestBody Map<String, String> body) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(gstinVerificationService.verifyGstin(userId, body.get("gstin")));
    }

    @PostMapping("/verify/bank")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> verifyBank(@RequestBody Map<String, String> body) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(bankVerificationService.verifyBank(
                userId, body.get("accountNumber"), body.get("ifsc"), body.get("accountName")));
    }

    @GetMapping("/apob")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listApobs() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(registrationService.listApobs(userId));
    }

    @PostMapping("/apob")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addApob(@RequestBody SellerApob apob) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(registrationService.saveApob(userId, apob));
    }

    @DeleteMapping("/apob/{apobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteApob(@PathVariable Long apobId) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(registrationService.deleteApob(userId, apobId));
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

    /**
     * @param status SUBMITTED, APPROVED, REJECTED, SUSPENDED, or ALL. Defaults to submitted, which
     *               is what this endpoint used to return unconditionally despite its name.
     */
    @GetMapping("/admin/registrations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> listRegistrations(
            @RequestParam(required = false, defaultValue = "SUBMITTED") String status) {
        return ResponseEntity.ok(Map.of("content", approvalService.listByStatus(status)));
    }

    @GetMapping("/admin/registrations/{registrationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> registrationDetail(@PathVariable Long registrationId) {
        return ResponseEntity.ok(approvalService.getDetail(registrationId));
    }

    @PostMapping("/admin/registrations/{registrationId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> approveRegistration(@PathVariable Long registrationId) {
        Long adminId = getCurrentUserId();
        if (adminId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(approvalService.approve(adminId, registrationId, isSuperAdmin()));
    }

    @PostMapping("/admin/registrations/{registrationId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> rejectRegistration(@PathVariable Long registrationId, @RequestBody Map<String, String> body) {
        Long adminId = getCurrentUserId();
        if (adminId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(approvalService.reject(adminId, registrationId, body.get("reason"), isSuperAdmin()));
    }

    /**
     * Suspends a trading seller. Super admin only - an admin approves and rejects applications,
     * but stopping a live business is final authority.
     */
    @PostMapping("/admin/registrations/{registrationId}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> suspendSeller(@PathVariable Long registrationId,
                                           @RequestBody(required = false) Map<String, String> body) {
        Long id = getCurrentUserId();
        if (id == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(approvalService.suspend(id, registrationId,
                body == null ? null : body.get("reason")));
    }

    @PostMapping("/admin/registrations/{registrationId}/reinstate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> reinstateSeller(@PathVariable Long registrationId) {
        Long id = getCurrentUserId();
        if (id == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(approvalService.reinstate(id, registrationId));
    }

    /**
     * An admin overreaching gets a plain 403 saying so, rather than a 500 from an unhandled
     * RuntimeException - the difference between "you may not" and "something broke".
     */
    @ExceptionHandler(SellerApprovalService.OverrideNotPermittedException.class)
    public ResponseEntity<?> handleOverrideRefused(SellerApprovalService.OverrideNotPermittedException e) {
        return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }
}

