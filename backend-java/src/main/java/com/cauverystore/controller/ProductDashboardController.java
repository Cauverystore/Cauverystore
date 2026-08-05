package com.cauverystore.controller;

import com.cauverystore.entities.Product;
import com.cauverystore.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/admin/product-dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE')")
public class ProductDashboardController {
    private final ProductRepository productRepo;
    public ProductDashboardController(ProductRepository productRepo) { this.productRepo = productRepo; }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDashboard() {
        List<Product> all = productRepo.findAll();
        long total = all.size();
        long active = all.stream().filter(Product::isActive).count();
        long outOfStock = all.stream().filter(p -> p.getStock() != null && p.getStock() <= 0).count();
        long lowStock = all.stream().filter(p -> p.getStock() != null && p.getStock() > 0 && p.getStock() < 10).count();
        long pendingApproval = all.stream().filter(p -> "pending".equalsIgnoreCase(p.getApprovalStatus())).count();
        long rejected = all.stream().filter(p -> "rejected".equalsIgnoreCase(p.getApprovalStatus())).count();
        long draft = all.stream().filter(p -> "draft".equalsIgnoreCase(p.getProductStatus())).count();
        long featured = all.stream().filter(p -> Boolean.TRUE.equals(p.getFeatured())).count();
        long trending = all.stream().filter(p -> Boolean.TRUE.equals(p.getTrending())).count();
        long bestSeller = all.stream().filter(p -> Boolean.TRUE.equals(p.getBestSeller())).count();
        long newArrival = all.stream().filter(p -> Boolean.TRUE.equals(p.getNewArrival())).count();
        long recentlyAdded = all.stream().filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30))).count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalProducts", total); stats.put("active", active); stats.put("draft", draft);
        stats.put("outOfStock", outOfStock); stats.put("lowStock", lowStock);
        stats.put("pendingApproval", pendingApproval); stats.put("rejected", rejected);
        stats.put("recentlyAdded", recentlyAdded); stats.put("featured", featured);
        stats.put("trending", trending); stats.put("bestSeller", bestSeller);
        stats.put("newArrival", newArrival);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Product>> search(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) String brand,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(required = false) String seller,
                                                 @RequestParam(required = false) String stockStatus) {
        List<Product> all = productRepo.findAll();
        Stream<Product> stream = all.stream();
        if (q != null && !q.isEmpty()) stream = stream.filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(q.toLowerCase())) || (p.getSku() != null && p.getSku().toLowerCase().contains(q.toLowerCase())) || (p.getBrand() != null && p.getBrand().toLowerCase().contains(q.toLowerCase())));
        if (brand != null && !brand.isEmpty()) stream = stream.filter(p -> brand.equals(p.getBrand()));
        if (category != null && !category.isEmpty()) stream = stream.filter(p -> p.getCategory() != null && category.equals(p.getCategory().getName()));
        if (seller != null && !seller.isEmpty()) try { Long sid = Long.parseLong(seller); stream = stream.filter(p -> sid.equals(p.getSellerId())); } catch (Exception e) {}
        if (stockStatus != null) { if ("in_stock".equals(stockStatus)) stream = stream.filter(p -> p.getStock() != null && p.getStock() > 0); else if ("out_of_stock".equals(stockStatus)) stream = stream.filter(p -> p.getStock() == null || p.getStock() <= 0); else if ("low_stock".equals(stockStatus)) stream = stream.filter(p -> p.getStock() != null && p.getStock() > 0 && p.getStock() < 10); }
        return ResponseEntity.ok(stream.collect(Collectors.toList()));
    }
}
