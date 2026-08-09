package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.entities.SellerStore;
import com.cauverystore.entities.User;
import com.cauverystore.repository.ProductRepository;
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
import java.util.stream.Collectors;

/**
 * Admin approval workflow. Registration becomes SUBMITTED after the seller
 * completes step 5 (terms); the seller only gains the SELLER role and an
 * ACTIVE account after an admin approves. Approval requires a VERIFIED GSTIN,
 * a VERIFIED bank account, and no pending mandatory compliance items.
 *
 * <h2>Who may undo a decision</h2>
 *
 * An admin decides once. Approve applies to a submitted registration and reject to one not yet
 * approved, which is the whole of day-to-day work and is deliberately one-way: an admin who has
 * rejected somebody cannot quietly reconsider, and an admin cannot revoke a colleague's approval.
 *
 * A super admin can undo a decision - approve a rejected applicant, or withdraw an approval.
 * That asymmetry is the point of having the two roles, and it is enforced here rather than only
 * in the endpoint annotations, because these methods are reachable from more than one controller.
 *
 * Suspension is deliberately not part of that asymmetry. Either role can suspend and reinstate,
 * because stopping a seller who is doing harm is day-to-day work and waiting for a super admin
 * would leave the listings up in the meantime. It is also reversible, which is what makes it
 * safe to hand to an admin: nothing is destroyed, and reinstating puts back exactly what was
 * taken down.
 */
@Service
public class SellerApprovalService {

    /** Raised when an admin attempts something reserved to a super admin. */
    public static class OverrideNotPermittedException extends RuntimeException {
        public OverrideNotPermittedException(String message) { super(message); }
    }

    private final SellerRegistrationRepository regRepo;
    private final SellerDocumentRepository docRepo;
    private final SellerComplianceRepository complianceRepo;
    private final SellerStoreRepository storeRepo;
    private final UserRepository userRepo;
    private final ProductRepository productRepo;
    private final AuditService auditService;
    private final EmailService emailService;
    private final AccountRestrictionService accountRestrictionService;

    public SellerApprovalService(SellerRegistrationRepository regRepo,
                                 SellerDocumentRepository docRepo,
                                 SellerComplianceRepository complianceRepo,
                                 SellerStoreRepository storeRepo,
                                 UserRepository userRepo,
                                 ProductRepository productRepo,
                                 AuditService auditService,
                                 EmailService emailService,
                                 AccountRestrictionService accountRestrictionService) {
        this.regRepo = regRepo;
        this.docRepo = docRepo;
        this.complianceRepo = complianceRepo;
        this.storeRepo = storeRepo;
        this.userRepo = userRepo;
        this.productRepo = productRepo;
        this.auditService = auditService;
        this.emailService = emailService;
        this.accountRestrictionService = accountRestrictionService;
    }

    public List<Map<String, Object>> listPending() {
        return listByStatus("SUBMITTED");
    }

    /**
     * Registrations in one state, or all of them when status is null or "ALL".
     *
     * The admin screen could previously only ever see submitted applications - the endpoint was
     * named for registrations but returned pending ones - so there was no way to look at who had
     * been approved, who had been turned down and why, or who is currently suspended.
     */
    public List<Map<String, Object>> listByStatus(String status) {
        boolean all = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status);
        String wanted = all ? null : status.trim().toUpperCase();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SellerRegistration r : regRepo.findAll()) {
            if (all || wanted.equals(r.getStatus())) {
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
                // A list that shows only names and dates cannot answer the question an admin
                // actually opens it with - what happened to this application, and why.
                row.put("status", r.getStatus());
                row.put("approvedAt", r.getApprovedAt());
                row.put("rejectedAt", r.getRejectedAt());
                row.put("rejectionReason", r.getRejectionReason());
                row.put("suspendedAt", r.getSuspendedAt());
                row.put("suspensionReason", r.getSuspensionReason());
                // Only for a suspended seller, and only then: a suspension is not over when the
                // button is pressed but when nobody is still waiting for goods. Computing it for
                // every row would mean walking every seller's orders to answer a question nobody
                // asked about the ones still trading.
                if ("SUSPENDED".equals(r.getStatus()) && r.getUser() != null) {
                    row.put("windDown", accountRestrictionService.windDown(r.getUser()));
                }
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

    /** Kept so existing callers keep an admin's - not a super admin's - rights. */
    public Map<String, Object> approve(Long adminId, Long registrationId) {
        return approve(adminId, registrationId, false);
    }

    // Must sit on the method that does the work, not on the overload above: an annotation binds
    // to the next declaration, and a call from inside this bean bypasses the proxy anyway.
    @Transactional
    public Map<String, Object> approve(Long adminId, Long registrationId, boolean superAdmin) {
        SellerRegistration r = regRepo.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Registration not found"));
        boolean overturningARejection = "REJECTED".equals(r.getStatus());
        if (overturningARejection && !superAdmin) {
            throw new OverrideNotPermittedException(
                    "This registration was rejected. Only a super admin can overturn a rejection.");
        }
        if (!"SUBMITTED".equals(r.getStatus()) && !overturningARejection) {
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
        // An overturned rejection must not keep reading as rejected in the seller's own status
        // screen, or in any list keyed on these fields.
        r.setRejectedAt(null);
        r.setRejectionReason(null);
        regRepo.save(r);

        createSellerStore(user, r);
        String action = overturningARejection ? "SELLER_APPROVAL_OVERRIDDEN" : "SELLER_APPROVED";
        auditService.log(adminId, actor(superAdmin, adminId), action, "SellerRegistration", registrationId,
                (overturningARejection ? "Overturned an earlier rejection and approved " : "Approved registration for ")
                        + user.getEmail() + " (" + r.getBusinessName() + ")", null);
        emailService.sendSellerApproved(user.getEmail(), r.getBusinessName());
        return Map.of("status", "APPROVED", "message", "Seller approved and activated");
    }

    /** Kept so existing callers keep an admin's - not a super admin's - rights. */
    public Map<String, Object> reject(Long adminId, Long registrationId, String reason) {
        return reject(adminId, registrationId, reason, false);
    }

    @Transactional
    public Map<String, Object> reject(Long adminId, Long registrationId, String reason, boolean superAdmin) {
        SellerRegistration r = regRepo.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Registration not found"));
        boolean withdrawingAnApproval = "APPROVED".equals(r.getStatus());
        if (withdrawingAnApproval && !superAdmin) {
            throw new OverrideNotPermittedException(
                    "This seller is already approved. Only a super admin can withdraw an approval.");
        }
        if (withdrawingAnApproval) {
            // The seller is trading. Rejecting them has to actually stop that, not merely relabel
            // the registration - the account and the listings both have to come down.
            takeOffSale(r, "rejected");
        }
        r.setStatus("REJECTED");
        r.setRejectedAt(LocalDateTime.now());
        r.setRejectionReason(reason);
        r.setApprovedAt(null);
        regRepo.save(r);
        String action = withdrawingAnApproval ? "SELLER_APPROVAL_WITHDRAWN" : "SELLER_REJECTED";
        auditService.log(adminId, actor(superAdmin, adminId), action, "SellerRegistration", registrationId,
                (withdrawingAnApproval ? "Withdrew approval for " : "Rejected registration for ")
                        + (r.getUser() != null ? r.getUser().getEmail() : "?")
                        + (reason != null ? ": " + reason : ""), null);
        if (r.getUser() != null) {
            emailService.sendSellerRejected(r.getUser().getEmail(), r.getBusinessName(), reason);
        }
        return Map.of("status", "REJECTED", "message", "Registration rejected");
    }

    /**
     * Suspends a trading seller. Super admin only.
     *
     * Distinct from rejection: the registration and its approval survive, so reinstatement is a
     * single action rather than a fresh application.
     */
    @Transactional
    public Map<String, Object> suspend(Long superAdminId, Long registrationId, String reason) {
        SellerRegistration r = regRepo.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Registration not found"));
        if (!"APPROVED".equals(r.getStatus())) {
            throw new RuntimeException("Only an approved seller can be suspended - this one is "
                    + r.getStatus() + ".");
        }
        takeOffSale(r, "suspended");
        r.setStatus("SUSPENDED");
        r.setSuspendedAt(LocalDateTime.now());
        r.setSuspensionReason(reason);
        regRepo.save(r);
        auditService.log(superAdminId, "super-admin:" + superAdminId, "SELLER_SUSPENDED",
                "SellerRegistration", registrationId,
                "Suspended " + (r.getUser() != null ? r.getUser().getEmail() : "?")
                        + " (" + r.getBusinessName() + ")" + (reason != null ? ": " + reason : ""), null);
        if (r.getUser() != null) {
            emailService.sendSellerSuspended(r.getUser().getEmail(), r.getBusinessName(), reason);
        }
        return Map.of("status", "SUSPENDED", "message", "Seller suspended and listings withdrawn");
    }

    /** Lifts a suspension, restoring exactly the listings the suspension took down. */
    @Transactional
    public Map<String, Object> reinstate(Long superAdminId, Long registrationId) {
        SellerRegistration r = regRepo.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Registration not found"));
        if (!"SUSPENDED".equals(r.getStatus())) {
            throw new RuntimeException("Only a suspended seller can be reinstated - this one is "
                    + r.getStatus() + ".");
        }
        int restored = putBackOnSale(r);
        r.setStatus("APPROVED");
        r.setSuspendedAt(null);
        r.setSuspensionReason(null);
        regRepo.save(r);

        User user = r.getUser();
        if (user != null) {
            user.setStatus("ACTIVE");
            user.setActive(true);
            userRepo.save(user);
        }
        auditService.log(superAdminId, "super-admin:" + superAdminId, "SELLER_REINSTATED",
                "SellerRegistration", registrationId,
                "Reinstated " + (user != null ? user.getEmail() : "?") + " (" + r.getBusinessName()
                        + "), " + restored + " listing(s) restored", null);
        if (user != null) {
            emailService.sendSellerReinstated(user.getEmail(), r.getBusinessName());
        }
        return Map.of("status", "APPROVED", "message", "Seller reinstated", "listingsRestored", restored);
    }

    /**
     * Takes a seller's account and listings out of service.
     *
     * Nothing in the catalogue reads seller status - products are listed on their own active
     * flag - so leaving the listings alone would mean a suspended seller carried on taking
     * orders. Which products were deactivated is recorded, because a seller may have delisted
     * some themselves and those must not reappear when the suspension is lifted.
     */
    private void takeOffSale(SellerRegistration r, String because) {
        User user = r.getUser();
        if (user == null) return;
        user.setStatus("SUSPENDED");
        user.setActive(false);
        user.setSuspensionReason(because);
        // Kept signed in on purpose. The listings are down so no new order can arrive, but the
        // orders already paid for still have to be picked, shipped and seen through their return
        // window - and signing the seller out would strand exactly those.
        user.setRefreshToken(null);
        userRepo.save(user);

        List<Long> taken = new ArrayList<>();
        for (Product p : productRepo.findBySellerId(user.getId())) {
            if (p.isActive()) {
                p.setActive(false);
                productRepo.save(p);
                taken.add(p.getId());
            }
        }
        r.setSuspendedProductIds(taken.isEmpty() ? null
                : taken.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    private int putBackOnSale(SellerRegistration r) {
        String stored = r.getSuspendedProductIds();
        r.setSuspendedProductIds(null);
        if (stored == null || stored.isBlank()) return 0;
        int restored = 0;
        for (String raw : stored.split(",")) {
            try {
                Product p = productRepo.findById(Long.parseLong(raw.trim())).orElse(null);
                // Silently skipped if the product has since been deleted - a missing product is
                // not a reason to leave the rest of the seller's catalogue down.
                if (p != null && !p.isActive()) {
                    p.setActive(true);
                    productRepo.save(p);
                    restored++;
                }
            } catch (NumberFormatException ignored) {
                // A malformed id cannot be restored and must not abort the reinstatement.
            }
        }
        return restored;
    }

    private String actor(boolean superAdmin, Long id) {
        return (superAdmin ? "super-admin:" : "admin:") + id;
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
