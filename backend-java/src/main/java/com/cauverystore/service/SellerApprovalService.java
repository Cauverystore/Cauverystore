package com.cauverystore.service;

import com.cauverystore.entities.Role;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.entities.SellerStore;
import com.cauverystore.entities.User;
import com.cauverystore.repository.SellerComplianceRepository;
import com.cauverystore.repository.SellerDocumentRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.repository.SellerStoreRepository;
import com.cauverystore.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin approval workflow. Registration becomes SUBMITTED after the seller
 * completes step 5 (terms); the seller only gains the SELLER role and an
 * ACTIVE account after an admin approves. Approval requires a VERIFIED GSTIN,
 * a VERIFIED bank account, and no pending mandatory compliance items.
 */
@Service
public class SellerApprovalService {

    private final SellerRegistrationRepository regRepo;
    private final SellerDocumentRepository docRepo;
    private final SellerComplianceRepository complianceRepo;
    private final SellerStoreRepository storeRepo;
    private final UserRepository userRepo;
    private final AuditService auditService;
    private final EmailService emailService;

    public SellerApprovalService(SellerRegistrationRepository regRepo,
                                 SellerDocumentRepository docRepo,
                                 SellerComplianceRepository complianceRepo,
                                 SellerStoreRepository storeRepo,
                                 UserRepository userRepo,
                                 AuditService auditService,
                                 EmailService emailService) {
        this.regRepo = regRepo;
        this.docRepo = docRepo;
        this.complianceRepo = complianceRepo;
        this.storeRepo = storeRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
        this.emailService = emailService;
    }

    public List<Map<String, Object>> listPending() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SellerRegistration r : regRepo.findAll()) {
            if ("SUBMITTED".equals(r.getStatus())) {
                Long sellerId = r.getUser() != null ? r.getUser().getId() : null;
                long docsTotal = docRepo.findByUserId(sellerId).size();
                long docsVerified = docRepo.findByUserId(sellerId).stream().filter(d -> "VERIFIED".equals(d.getStatus())).count();
                long complianceTotal = complianceRepo.findByUserId(sellerId).size();
                long complianceIncomplete = complianceRepo.findByUserIdAndIsCompletedFalse(sellerId).size();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("registrationId", r.getId());
                row.put("sellerId", sellerId);
                row.put("sellerEmail", r.getUser() != null ? r.getUser().getEmail() : null);
                row.put("sellerName", r.getUser() != null ? r.getUser().getFullName() : null);
                row.put("businessName", r.getBusinessName());
                row.put("businessType", r.getBusinessType());
                row.put("gstin", r.getGstin());
                row.put("gstinStatus", r.getGstinStatus());
                row.put("bankStatus", r.getBankStatus());
                row.put("submittedAt", r.getSubmittedAt());
                row.put("documentsVerified", docsVerified);
                row.put("documentsTotal", docsTotal);
                row.put("complianceCompleted", complianceTotal - complianceIncomplete);
                row.put("complianceTotal", complianceTotal);
                row.put("readiness", readiness(sellerId));
                rows.add(row);
            }
        }
        return rows;
    }

    public Map<String, Object> getDetail(Long registrationId) {
        SellerRegistration r = regRepo.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Registration not found"));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("registration", r);
        if (r.getUser() != null) {
            detail.put("documents", docRepo.findByUserId(r.getUser().getId()));
            detail.put("compliance", complianceRepo.findByUserId(r.getUser().getId()));
        }
        detail.put("readiness", readiness(r.getUser() != null ? r.getUser().getId() : null));
        return detail;
    }

    @Transactional
    public Map<String, Object> approve(Long adminId, Long registrationId) {
        SellerRegistration r = regRepo.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Registration not found"));
        if (!"SUBMITTED".equals(r.getStatus())) {
            throw new RuntimeException("Only submitted registrations can be approved");
        }
        Map<String, Boolean> readiness = readiness(r.getUser().getId());
        if (!Boolean.TRUE.equals(readiness.get("gstinVerified"))) {
            throw new RuntimeException("GSTIN is not verified. Ask the seller to verify it first.");
        }
        if (!Boolean.TRUE.equals(readiness.get("bankVerified"))) {
            throw new RuntimeException("Bank account is not verified.");
        }
        if (!Boolean.TRUE.equals(readiness.get("complianceComplete"))) {
            throw new RuntimeException("Mandatory compliance items are still pending.");
        }

        User user = r.getUser();
        user.setRole(Role.SELLER);
        user.setStatus("ACTIVE");
        user.setActive(true);
        userRepo.save(user);

        r.setStatus("APPROVED");
        r.setApprovedAt(LocalDateTime.now());
        regRepo.save(r);

        createSellerStore(user, r);
        auditService.log(adminId, "admin:" + adminId, "SELLER_APPROVED", "SellerRegistration", registrationId,
                "Approved registration for " + user.getEmail() + " (" + r.getBusinessName() + ")", null);
        emailService.sendSellerApproved(user.getEmail(), r.getBusinessName());
        return Map.of("status", "APPROVED", "message", "Seller approved and activated");
    }

    @Transactional
    public Map<String, Object> reject(Long adminId, Long registrationId, String reason) {
        SellerRegistration r = regRepo.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Registration not found"));
        if ("APPROVED".equals(r.getStatus())) {
            throw new RuntimeException("An approved registration cannot be rejected");
        }
        r.setStatus("REJECTED");
        r.setRejectedAt(LocalDateTime.now());
        r.setRejectionReason(reason);
        regRepo.save(r);
        auditService.log(adminId, "admin:" + adminId, "SELLER_REJECTED", "SellerRegistration", registrationId,
                "Rejected registration for " + (r.getUser() != null ? r.getUser().getEmail() : "?") + (reason != null ? ": " + reason : ""), null);
        if (r.getUser() != null) {
            emailService.sendSellerRejected(r.getUser().getEmail(), r.getBusinessName(), reason);
        }
        return Map.of("status", "REJECTED", "message", "Registration rejected");
    }

    private Map<String, Boolean> readiness(Long userId) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        SellerRegistration r = regRepo.findByUserId(userId).orElse(null);
        map.put("gstinVerified", r != null && "VERIFIED".equals(r.getGstinStatus()));
        map.put("bankVerified", r != null && "VERIFIED".equals(r.getBankStatus()));
        long mandatoryIncomplete = complianceRepo.findByUserIdAndIsCompletedFalse(userId).stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsMandatory()))
                .count();
        map.put("complianceComplete", mandatoryIncomplete == 0);
        return map;
    }

    private void createSellerStore(User user, SellerRegistration reg) {
        SellerStore store = storeRepo.findBySellerId(user.getId()).orElseGet(() -> {
            SellerStore ns = new SellerStore();
            ns.setSeller(user);
            return ns;
        });
        store.setStoreName(reg.getBusinessName());
        store.setStoreSlug(reg.getBusinessName().toLowerCase().replaceAll("[^a-z0-9]+", "-"));
        store.setContactEmail(reg.getBusinessEmail());
        store.setContactPhone(reg.getBusinessPhone());
        store.setAddress(reg.getBusinessAddress());
        store.setStatus("ACTIVE");
        storeRepo.save(store);
    }
}
