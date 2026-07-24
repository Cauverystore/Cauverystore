package com.cauverystore.repository;

import com.cauverystore.entities.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId);
    List<StockMovement> findByVariantIdOrderByCreatedAtDesc(Long variantId);
    List<StockMovement> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId);
    List<StockMovement> findByType(String type);
}
