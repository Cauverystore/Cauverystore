package com.cauverystore.service;

import com.cauverystore.entities.ProductReview;
import com.cauverystore.repository.ProductReviewRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ReviewService {
    private final ProductReviewRepository reviewRepo;
    public ReviewService(ProductReviewRepository reviewRepo) { this.reviewRepo = reviewRepo; }

    public List<ProductReview> getAll() { return reviewRepo.findAll(); }
    public ProductReview getById(Long id) { return reviewRepo.findById(id).orElseThrow(() -> new RuntimeException("Review not found")); }
    public ProductReview approve(Long id) {
        ProductReview r = getById(id); r.setApproved(true); return reviewRepo.save(r);
    }
    public ProductReview reject(Long id) {
        ProductReview r = getById(id); r.setApproved(false); return reviewRepo.save(r);
    }
    public void delete(Long id) { reviewRepo.deleteById(id); }
}
