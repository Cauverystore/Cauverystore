package com.cauverystore.repository;

import com.cauverystore.entities.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId);
    List<StockMovement> findByVariantIdOrderByCreatedAtDesc(Long variantId);
    List<StockMovement> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId);
    List<StockMovement> findByType(String type);
    List<StockMovement> findTop500ByOrderByIdDesc();
    List<StockMovement> findTop500ByTypeOrderByIdDesc(String type);

    @Modifying
    @Query("UPDATE StockMovement m SET m.referenceId = :referenceId WHERE m.id IN :ids")
    int setReference(@Param("ids") Collection<Long> ids, @Param("referenceId") Long referenceId);
}
