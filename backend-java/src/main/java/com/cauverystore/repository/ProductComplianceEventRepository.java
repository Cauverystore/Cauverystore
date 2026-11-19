package com.cauverystore.repository;

import com.cauverystore.entities.ProductComplianceEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductComplianceEventRepository extends JpaRepository<ProductComplianceEvent, Long> {
    List<ProductComplianceEvent> findByProductIdOrderByCreatedAtAsc(Long productId);
}
