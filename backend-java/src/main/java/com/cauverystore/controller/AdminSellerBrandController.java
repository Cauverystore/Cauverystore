package com.cauverystore.controller;

import com.cauverystore.service.AuthorizationService;
import com.cauverystore.service.SellerBrandService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Admins reviewing sellers' brand applications. */
@RestController
@RequestMapping("/api/admin/seller-brands")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminSellerBrandController {

    private final SellerBrandService brandService;
    private final AuthorizationService authorizationService;

    public AdminSellerBrandController(SellerBrandService brandService, AuthorizationService authorizationService) {
        this.brandService = brandService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "true") boolean openOnly) {
        return ResponseEntity.ok(brandService.forAdmin(openOnly));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<?> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(brandService.review(authorizationService.getCurrentUserId(),
                    authorizationService.getCurrentUserEmail(), id,
                    body.get("action") == null ? null : body.get("action").toString(),
                    body.get("reason") == null ? null : body.get("reason").toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
