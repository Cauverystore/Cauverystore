package com.cauverystore.controller;

import com.cauverystore.entities.Product;
import com.cauverystore.service.AuthorizationService;
import com.cauverystore.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final AuthorizationService authorizationService;

    @GetMapping
    public ResponseEntity<List<Product>> getActiveProducts() {
        return ResponseEntity.ok(productService.getActiveProducts());
    }

    /**
     * Assigns an HSN code to one product, on its own.
     *
     * Body: {@code hsnCode}, optional {@code prePackagedAndLabelled} for the staples whose rate
     * turns on packaging, and optional {@code gstRateSelectionId} where the heading carries more
     * than one published rate and the seller has chosen between them.
     *
     * Rejected if the code is not in the official GSTN master, or if the product is published
     * and the assignment would leave its rate undeterminable - a published product that cannot
     * be taxed is one that cannot be lawfully invoiced.
     */
    @PostMapping("/{id}/assign-hsn")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Product> assignHsn(@PathVariable Long id,
                                             @RequestBody Map<String, Object> body) {
        authorizationService.requireProductAccess(id);
        Object rateSelection = body.get("gstRateSelectionId");
        return ResponseEntity.ok(productService.assignHsnCode(
                id,
                body.get("hsnCode") == null ? null : String.valueOf(body.get("hsnCode")),
                body.get("prePackagedAndLabelled") == null ? null
                        : Boolean.valueOf(String.valueOf(body.get("prePackagedAndLabelled"))),
                rateSelection == null ? null : Long.valueOf(String.valueOf(rateSelection).trim())));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<Product>> searchProducts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {
        return ResponseEntity.ok(productService.searchProducts(name, category, minPrice, maxPrice, page, size, sortBy, direction));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/{id}/similar")
    public ResponseEntity<List<Product>> getSimilarProducts(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getSimilarProducts(id));
    }

    @GetMapping("/featured")
    public ResponseEntity<List<Product>> getFeaturedProducts() {
        return ResponseEntity.ok(productService.getFeaturedProducts());
    }

    @GetMapping("/{id}/discounted-price")
    public ResponseEntity<Map<String, Object>> getDiscountedPrice(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getDiscountedPrice(id));
    }

    @PostMapping("/{id}/discounts")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> addDiscount(@PathVariable Long id, @RequestBody Map<String, Object> discount) {
        if (authorizationService.hasRole("SELLER")) {
            productService.checkSellerOwnership(id, authorizationService.getCurrentUserId());
        }
        return ResponseEntity.ok(productService.addDiscount(id, discount));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Product> updateActiveStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        if (authorizationService.hasRole("SELLER")) {
            productService.checkSellerOwnership(id, authorizationService.getCurrentUserId());
        }
        return ResponseEntity.ok(productService.updateActiveStatus(id, body.get("active")));
    }
}
