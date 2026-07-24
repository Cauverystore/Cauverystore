package com.cauverystore.repository;

import com.cauverystore.entities.ProductReview;
import com.cauverystore.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    List<ProductReview> findByUser(User user);

    List<ProductReview> findByProduct_IdOrderByCreatedAtDesc(Long productId);

    @Modifying
    @Transactional
    void deleteByProduct_IdAndId(Long productId, Long id);

    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.approved = true")
    Double getAverageRating();

    @Query("SELECT r.rating, COUNT(r) FROM ProductReview r WHERE r.approved = true GROUP BY r.rating ORDER BY r.rating")
    List<Object[]> getRatingDistribution();

    Long countByApproved(boolean approved);
}
