package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.ProductCompliance;
import com.cauverystore.entities.ProductComplianceEvent;
import com.cauverystore.entities.ProductWorkflowStatus;
import com.cauverystore.repository.ProductComplianceEventRepository;
import com.cauverystore.repository.ProductComplianceRepository;
import com.cauverystore.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.cauverystore.entities.ProductWorkflowStatus.*;

/**
 * Moves a product through DRAFT -> COMPLIANCE_CHECK -> SELLER_REVIEW -> ADMIN_REVIEW -> APPROVED
 * -> PUBLISHED, recording every step.
 *
 * submit:  DRAFT -> COMPLIANCE_CHECK, then automatically SELLER_REVIEW if the engine allows it, else back to DRAFT.
 * confirm: SELLER_REVIEW -> ADMIN_REVIEW (the seller has checked their listing).
 * approve: ADMIN_REVIEW -> APPROVED (admin only; re-checked).
 * reject:  SELLER_REVIEW / ADMIN_REVIEW / APPROVED -> DRAFT, with a reason.
 * publish: APPROVED -> PUBLISHED (admin only; re-checked; puts the product on sale).
 *
 * Who may call which action is decided by the controller; this class enforces the order.
 */
@Service
public class ProductComplianceWorkflowService {

    public static class InvalidTransitionException extends RuntimeException {
        public InvalidTransitionException(String message) { super(message); }
    }

    private final ProductRepository productRepo;
    private final ProductComplianceRepository complianceRepo;
    private final ProductComplianceEventRepository eventRepo;
    private final ProductComplianceService engine;

    public ProductComplianceWorkflowService(ProductRepository productRepo, ProductComplianceRepository complianceRepo,
                                            ProductComplianceEventRepository eventRepo, ProductComplianceService engine) {
        this.productRepo = productRepo;
        this.complianceRepo = complianceRepo;
        this.eventRepo = eventRepo;
        this.engine = engine;
    }

    @Transactional
    @CacheEvict(value = {"products", "categories"}, allEntries = true)
    public Map<String, Object> transition(Long productId, String action, String note) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        ProductCompliance record = engine.recordFor(product);
        ProductWorkflowStatus from = ProductWorkflowStatus.from(record.getStatus());
        String a = action == null ? "" : action.trim().toLowerCase();
        ProductComplianceService.Result result = engine.evaluate(product, record);
        engine.store(record, result);

        ProductWorkflowStatus to;
        switch (a) {
            case "submit" -> {
                require(from, a, DRAFT);
                log(record, product, a, from, COMPLIANCE_CHECK, result.score(), note);
                to = result.publishable() ? SELLER_REVIEW : DRAFT;
                record.setStatusNote(result.publishable() ? null : summary(result));
                log(record, product, "compliance_check", COMPLIANCE_CHECK, to, result.score(), record.getStatusNote());
                record.setStatus(to.name());
                complianceRepo.save(record);
                return response(record, result);
            }
            case "confirm" -> {
                require(from, a, SELLER_REVIEW);
                to = result.publishable() ? ADMIN_REVIEW : DRAFT;
                note = result.publishable() ? note : summary(result);
            }
            case "approve" -> {
                require(from, a, ADMIN_REVIEW);
                if (!result.publishable()) throw new ProductComplianceService.ComplianceBlockedException(result);
                to = APPROVED;
            }
            case "reject" -> {
                require(from, a, SELLER_REVIEW, ADMIN_REVIEW, APPROVED);
                if (note == null || note.isBlank()) throw new InvalidTransitionException("Give a reason for sending the listing back.");
                to = DRAFT;
            }
            case "publish" -> {
                require(from, a, APPROVED);
                if (!result.publishable()) throw new ProductComplianceService.ComplianceBlockedException(result);
                product.setApprovalStatus("approved");
                product.setProductStatus("published");
                product.setActive(true);
                productRepo.save(product);
                to = PUBLISHED;
            }
            default -> throw new InvalidTransitionException("Unknown action '" + action
                    + "'. Use submit, confirm, approve, reject or publish.");
        }
        log(record, product, a, from, to, result.score(), note);
        record.setStatus(to.name());
        record.setStatusNote(note);
        complianceRepo.save(record);
        return response(record, result);
    }

    /**
     * Brings the record into line when a product reaches the shop by the older routes (the
     * product approval screen, or an admin saving it as published).
     */
    @Transactional
    public void markPublished(Product product, String via) {
        ProductCompliance record = engine.recordFor(product);
        ProductWorkflowStatus from = ProductWorkflowStatus.from(record.getStatus());
        if (from == PUBLISHED) return;
        log(record, product, via, from, PUBLISHED, record.getComplianceScore(), null);
        record.setStatus(PUBLISHED.name());
        complianceRepo.save(record);
    }

    /** Records that the publish gate kept a product off sale. */
    @Transactional
    public void markHeld(Product product, ProductComplianceService.Result result) {
        if (product.getId() == null) return;
        ProductCompliance record = engine.recordFor(product);
        ProductWorkflowStatus from = ProductWorkflowStatus.from(record.getStatus());
        engine.store(record, result);
        log(record, product, "held", from, DRAFT, result.score(), summary(result));
        record.setStatus(DRAFT.name());
        record.setStatusNote(summary(result));
        complianceRepo.save(record);
    }

    /** An edit after review invalidates the review: back to DRAFT. A live product stays live. */
    @Transactional
    public void onEdited(Product product, ProductCompliance record) {
        ProductWorkflowStatus from = ProductWorkflowStatus.from(record.getStatus());
        if (from == ADMIN_REVIEW || from == APPROVED) {
            log(record, product, "edited", from, DRAFT, record.getComplianceScore(), "Changed after review; submit again.");
            record.setStatus(DRAFT.name());
            record.setStatusNote("Changed after review; submit again.");
            complianceRepo.save(record);
        }
    }

    public List<ProductComplianceEvent> history(Long productId) {
        return eventRepo.findByProductIdOrderByCreatedAtAsc(productId);
    }

    private static void require(ProductWorkflowStatus from, String action, ProductWorkflowStatus... allowed) {
        for (ProductWorkflowStatus s : allowed) if (s == from) return;
        throw new InvalidTransitionException("Cannot " + action + " a listing that is " + from + ".");
    }

    private void log(ProductCompliance record, Product product, String action, ProductWorkflowStatus from,
                     ProductWorkflowStatus to, Integer score, String note) {
        ProductComplianceEvent e = new ProductComplianceEvent();
        e.setProductId(product.getId());
        e.setAction(action);
        e.setFromStatus(from.name());
        e.setToStatus(to.name());
        e.setScore(score);
        e.setNote(note);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        e.setActor(auth != null && auth.isAuthenticated() ? auth.getName() : "system");
        eventRepo.save(e);
    }

    static String summary(ProductComplianceService.Result r) {
        return "Score " + r.score() + ". Missing or invalid: "
                + String.join(", ", r.issues().stream().map(ProductComplianceService.Issue::label).distinct().toList()) + ".";
    }

    private static Map<String, Object> response(ProductCompliance record, ProductComplianceService.Result result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productId", record.getProductId());
        m.put("status", record.getStatus());
        m.put("statusNote", record.getStatusNote());
        m.put("compliance", result);
        return m;
    }
}
