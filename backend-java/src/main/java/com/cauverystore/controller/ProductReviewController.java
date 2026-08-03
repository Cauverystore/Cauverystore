package com.cauverystore.controller;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.ProductReview;
import com.cauverystore.entities.User;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.ProductReviewRepository;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.service.AuthorizationService;
import com.cauverystore.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class ProductReviewController {

    private final ProductReviewRepository reviewRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final ProductService productService;
    private final AuthorizationService authorizationService;

    public ProductReviewController(ProductReviewRepository rr, ProductRepository pr, UserRepository ur,
                                    ProductService productService, AuthorizationService authorizationService) {
        this.reviewRepo = rr; this.productRepo = pr; this.userRepo = ur;
        this.productService = productService; this.authorizationService = authorizationService;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    @GetMapping("/api/admin/products/{productId}/reviews")
    public ResponseEntity<?> getAdminReviews(@PathVariable Long productId) {
        if (!productRepo.existsById(productId)) return ResponseEntity.badRequest().body(Map.of("error","Product not found"));
        if (authorizationService.hasRole("SELLER")) {
            productService.checkSellerOwnership(productId, authorizationService.getCurrentUserId());
        }
        List<Map<String,Object>> result = reviewRepo.findByProduct_IdOrderByCreatedAtDesc(productId).stream().map(r -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",r.getId()); m.put("rating",r.getRating());
            m.put("comment",r.getComment()!=null?r.getComment():"");
            m.put("userName",r.getUser()!=null?(r.getUser().getFullName()!=null?r.getUser().getFullName():r.getUser().getUsername()):"—");
            m.put("createdAt",r.getCreatedAt()!=null?r.getCreatedAt().toString():null);
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    @DeleteMapping("/api/admin/products/{productId}/reviews/{reviewId}")
    public ResponseEntity<?> deleteReview(@PathVariable Long productId, @PathVariable Long reviewId) {
        ProductReview review = reviewRepo.findById(reviewId).orElse(null);
        if (review == null || review.getProduct() == null || !review.getProduct().getId().equals(productId)) {
            return ResponseEntity.badRequest().body(Map.of("error","Review not found"));
        }
        if (authorizationService.hasRole("SELLER")) {
            productService.checkSellerOwnership(productId, authorizationService.getCurrentUserId());
        }
        reviewRepo.deleteById(reviewId);
        return ResponseEntity.ok(Map.of("message","Review deleted"));
    }

    @GetMapping("/api/products/{productId}/reviews")
    public ResponseEntity<?> getProductReviews(@PathVariable Long productId) {
        List<Map<String,Object>> result = reviewRepo.findByProduct_IdOrderByCreatedAtDesc(productId).stream().map(r -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",r.getId()); m.put("rating",r.getRating());
            m.put("comment",r.getComment()!=null?r.getComment():"");
            m.put("userName",r.getUser()!=null?(r.getUser().getFullName()!=null?r.getUser().getFullName():r.getUser().getUsername()):"Anonymous");
            m.put("createdAt",r.getCreatedAt()!=null?r.getCreatedAt().toString():null);
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/products/{productId}/reviews")
    public ResponseEntity<?> addReview(@PathVariable Long productId, @RequestBody Map<String,Object> body) {
        Product product = productRepo.findById(productId).orElse(null);
        if (product==null) return ResponseEntity.badRequest().body(Map.of("error","Product not found"));
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepo.findByUsername(username);
        if (user==null) return ResponseEntity.status(401).body(Map.of("error","User not found"));
        int rating; try { rating=Integer.parseInt(body.getOrDefault("rating","0").toString()); if(rating<1||rating>5) throw new NumberFormatException(); } catch(Exception e){ return ResponseEntity.badRequest().body(Map.of("error","Rating must be 1-5")); }
        String comment = (String)body.getOrDefault("comment","");
        boolean already=reviewRepo.findByProduct_IdOrderByCreatedAtDesc(productId).stream().anyMatch(r->r.getUser()!=null&&r.getUser().getId().equals(user.getId()));
        if(already) return ResponseEntity.badRequest().body(Map.of("error","Already reviewed"));
        ProductReview r = new ProductReview(); r.setRating(rating); r.setComment(comment); r.setProduct(product); r.setUser(user);
        reviewRepo.save(r);
        return ResponseEntity.ok(Map.of("id",r.getId(),"rating",r.getRating(),"comment",r.getComment(),"userName",user.getFullName()!=null?user.getFullName():user.getUsername(),"createdAt",r.getCreatedAt()!=null?r.getCreatedAt().toString():null));
    }
}
