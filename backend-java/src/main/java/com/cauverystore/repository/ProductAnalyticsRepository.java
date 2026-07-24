package com.cauverystore.repository;

import com.cauverystore.entities.ProductAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductAnalyticsRepository extends JpaRepository<ProductAnalytics, Long> {
    Optional<ProductAnalytics> findByProductIdAndDate(Long productId, LocalDate date);
    List<ProductAnalytics> findByProductIdOrderByDateDesc(Long productId);
    List<ProductAnalytics> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);

    Optional<ProductAnalytics> findTopByProductIdOrderByDateDesc(Long productId);
}
