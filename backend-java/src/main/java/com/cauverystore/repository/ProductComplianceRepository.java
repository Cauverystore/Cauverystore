package com.cauverystore.repository;

import com.cauverystore.entities.ProductCompliance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductComplianceRepository extends JpaRepository<ProductCompliance, Long> {
    Optional<ProductCompliance> findByProductIdAndDeletedFlagFalse(Long productId);
    List<ProductCompliance> findByProductIdInAndDeletedFlagFalse(Collection<Long> productIds);
}
