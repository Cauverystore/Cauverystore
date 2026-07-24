package com.cauverystore.controller;

import com.cauverystore.entities.ProductReview;
import com.cauverystore.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reviews")
@PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'SUPER_ADMIN')")
@CrossOrigin("*")
public class AdminReviewController {
    private final ReviewService reviewService;
    public AdminReviewController(ReviewService reviewService) { this.reviewService = reviewService; }

    @GetMapping
    public ResponseEntity<List<ProductReview>> getAll() { return ResponseEntity.ok(reviewService.getAll()); }
    @PutMapping("/{id}/approve")
    public ResponseEntity<ProductReview> approve(@PathVariable Long id) { return ResponseEntity.ok(reviewService.approve(id)); }
    @PutMapping("/{id}/reject")
    public ResponseEntity<ProductReview> reject(@PathVariable Long id) { return ResponseEntity.ok(reviewService.reject(id)); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { reviewService.delete(id); return ResponseEntity.noContent().build(); }
}
