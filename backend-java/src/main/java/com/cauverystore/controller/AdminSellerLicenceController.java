package com.cauverystore.controller;

import com.cauverystore.service.AuthorizationService;
import com.cauverystore.service.SellerLicenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Admins checking the licence numbers sellers enter, before shoppers see them. */
@RestController
@RequestMapping("/api/admin/seller-licences")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminSellerLicenceController {

    private final SellerLicenceService licenceService;
    private final AuthorizationService authorizationService;

    public AdminSellerLicenceController(SellerLicenceService licenceService, AuthorizationService authorizationService) {
        this.licenceService = licenceService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam Long sellerId) {
        return ResponseEntity.ok(licenceService.list(sellerId));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<?> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean approved = Boolean.TRUE.equals(body.get("approved")) || "true".equals(String.valueOf(body.get("approved")));
        String reason = body.get("reason") == null ? null : body.get("reason").toString();
        try {
            return ResponseEntity.ok(licenceService.review(authorizationService.getCurrentUserId(),
                    authorizationService.getCurrentUserEmail(), id, approved, reason));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
