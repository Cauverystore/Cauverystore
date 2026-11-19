package com.cauverystore.controller;

import com.cauverystore.entities.*;
import com.cauverystore.repository.CategoryRepository;
import com.cauverystore.repository.ProductComplianceRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Product compliance: the check, the compliance details, the approval workflow, and which
 * category type a category belongs to.
 *
 * Sellers reach only their own products, and only with the products.manage permission when
 * acting as a team member. Approve, reject from admin review, and publish are admin-only.
 */
@RestController
@RequestMapping("/api/compliance")
@RequiredArgsConstructor
public class ProductComplianceController {

    private final ProductComplianceService engine;
    private final ProductComplianceWorkflowService workflow;
    private final ProductComplianceRepository complianceRepo;
    private final ProductRepository productRepo;
    private final CategoryRepository categoryRepo;
    private final ProductService productService;
    private final AuthorizationService auth;
    private final SellerAccessService sellerAccess;

    private Product accessibleProduct(Long productId) {
        Product p = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        if (auth.hasRole("SELLER") && !auth.hasAnyRole("ADMIN", "SUPER_ADMIN", "EXECUTIVE")) {
            sellerAccess.require(SellerPermissions.PRODUCTS_MANAGE);
            Long acting = sellerAccess.actingSellerId();
            productService.checkSellerOwnership(productId, acting != null ? acting : auth.getCurrentUserId());
        }
        return p;
    }

    /** Runs the compliance check: {compliant, score, missingFields, ...}. */
    @PostMapping("/check/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE', 'SELLER')")
    public ResponseEntity<ProductComplianceService.Result> check(@PathVariable Long productId) {
        accessibleProduct(productId);
        return ResponseEntity.ok(engine.check(productId));
    }

    /** The compliance details, a fresh result, and the workflow history. */
    @GetMapping("/products/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE', 'SELLER')")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long productId) {
        Product p = accessibleProduct(productId);
        ProductCompliance record = engine.findRecord(productId).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productId", productId);
        m.put("categoryType", ProductComplianceService.typeOf(p).name());
        m.put("status", record != null ? record.getStatus()
                : (ProductComplianceService.visibleToShoppers(p) ? "PUBLISHED" : "DRAFT"));
        m.put("product", basics(p));
        m.put("details", record);
        m.put("compliance", engine.evaluate(p, record));
        m.put("history", workflow.history(productId));
        return ResponseEntity.ok(m);
    }

    private static Map<String, Object> basics(Product p) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("id", p.getId());
        b.put("name", p.getName());
        b.put("sku", p.getSku());
        b.put("brand", p.getBrand());
        b.put("category", p.getCategory() != null ? p.getCategory().getName() : null);
        b.put("sellerId", p.getSellerId());
        b.put("price", p.getPrice());
        b.put("offerPrice", p.getOfferPrice());
        b.put("mrp", p.getMrp());
        b.put("hsnCode", p.getHsnCode());
        b.put("countryOfOrigin", p.getCountryOfOrigin());
        b.put("manufacturer", p.getManufacturer());
        b.put("warranty", p.getWarranty());
        b.put("returnPolicy", p.getReturnPolicy());
        b.put("live", ProductComplianceService.visibleToShoppers(p));
        return b;
    }

    /**
     * The product fields the compliance check reads and the shop shows (MRP, country of origin,
     * manufacturer, warranty, return policy). Saved through the normal product update, so
     * ownership checks and the publish gate apply as they do on the product form.
     */
    @PutMapping("/products/{productId}/basics")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE', 'SELLER')")
    public ResponseEntity<Map<String, Object>> saveBasics(@PathVariable Long productId, @RequestBody Map<String, Object> body) {
        Product existing = accessibleProduct(productId);
        Product change = new Product();
        change.setActive(existing.isActive());   // a bare Product is active; never flip it from here
        if (body.containsKey("mrp")) {
            Object v = body.get("mrp");
            Double mrp = v == null || String.valueOf(v).isBlank() ? null : Double.valueOf(String.valueOf(v));
            if (mrp != null && mrp <= 0) throw new IllegalArgumentException("MRP must be more than zero.");
            change.setMrp(mrp);
        }
        change.setCountryOfOrigin(str(body.get("countryOfOrigin")));
        change.setManufacturer(str(body.get("manufacturer")));
        change.setWarranty(str(body.get("warranty")));
        change.setReturnPolicy(str(body.get("returnPolicy")));
        Product saved = productService.updateProduct(productId, change);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("product", basics(saved));
        m.put("compliance", engine.check(productId));
        return ResponseEntity.ok(m);
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    /** Saves the compliance details (bean-validated) and returns the new result. */
    @PutMapping("/products/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE', 'SELLER')")
    @Transactional
    public ResponseEntity<Map<String, Object>> save(@PathVariable Long productId, @Valid @RequestBody ProductCompliance body) {
        Product p = accessibleProduct(productId);
        ProductCompliance record = engine.recordFor(p);
        record.copyEditableFrom(body);
        complianceRepo.save(record);
        workflow.onEdited(p, record);
        ProductComplianceService.Result result = engine.check(productId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("details", record);
        m.put("status", record.getStatus());
        m.put("compliance", result);
        return ResponseEntity.ok(m);
    }

    /** submit | confirm | approve | reject | publish, with an optional note (required for reject). */
    @PostMapping("/products/{productId}/workflow/{action}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE', 'SELLER')")
    public ResponseEntity<Map<String, Object>> transition(@PathVariable Long productId, @PathVariable String action,
                                                          @RequestBody(required = false) Map<String, String> body) {
        accessibleProduct(productId);
        String a = action.toLowerCase(Locale.ROOT);
        boolean adminOnly = a.equals("approve") || a.equals("publish");
        if (adminOnly && !auth.hasAnyRole("ADMIN", "SUPER_ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Only an admin may " + a + " a listing.");
        }
        if (a.equals("reject") && !auth.hasAnyRole("ADMIN", "SUPER_ADMIN")) {
            // A seller may withdraw their own listing from their own review step, nothing later.
            Optional<ProductCompliance> r = engine.findRecord(productId);
            if (r.isEmpty() || !ProductWorkflowStatus.SELLER_REVIEW.name().equals(r.get().getStatus())) {
                throw new org.springframework.security.access.AccessDeniedException("Only an admin may send back a listing under admin review.");
            }
        }
        String note = body == null ? null : body.get("note");
        return ResponseEntity.ok(workflow.transition(productId, a, note));
    }

    @GetMapping("/products/{productId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE', 'SELLER')")
    public ResponseEntity<List<ProductComplianceEvent>> history(@PathVariable Long productId) {
        accessibleProduct(productId);
        return ResponseEntity.ok(workflow.history(productId));
    }

    /** Admin compliance dashboard: totals and one row per product. */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE')")
    public ResponseEntity<Map<String, Object>> dashboard() {
        return ResponseEntity.ok(engine.dashboard());
    }

    /** The category types and the checks each one adds, for forms and the seller wizard. */
    @GetMapping("/requirements")
    public ResponseEntity<List<Map<String, Object>>> requirements() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ComplianceCategoryType t : ComplianceCategoryType.values()) {
            Category c = new Category();
            c.setComplianceType(t.name());
            Product blank = new Product();
            blank.setCategory(c);
            blank.setSellerId(-1L);   // so the seller checks are listed too
            List<ProductComplianceService.Issue> fields = engine.evaluate(blank, null).issues().stream()
                    .filter(i -> !i.field().equals("category")).toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", t.name());
            m.put("label", t.getLabel());
            m.put("fields", fields);
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    /** Sets which compliance type a category's products follow. */
    @PutMapping("/categories/{categoryId}/type")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> setCategoryType(@PathVariable Long categoryId, @RequestBody Map<String, String> body) {
        Category c = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + categoryId));
        String raw = body.get("complianceType");
        String type = raw == null || raw.isBlank() ? null : ComplianceCategoryType.from(raw).name();
        c.setComplianceType("GENERAL".equals(type) ? null : type);
        categoryRepo.save(c);
        productService.evictProductCache();
        return ResponseEntity.ok(Map.of("categoryId", categoryId, "complianceType", c.getComplianceType() == null ? "GENERAL" : c.getComplianceType()));
    }
}
