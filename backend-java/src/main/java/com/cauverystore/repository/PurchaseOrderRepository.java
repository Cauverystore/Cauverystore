package com.cauverystore.repository;

import com.cauverystore.entities.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByStatus(String status);
    List<PurchaseOrder> findBySupplierId(Long supplierId);
    List<PurchaseOrder> findByWarehouseId(Long warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PurchaseOrder p WHERE p.id = :id")
    Optional<PurchaseOrder> findForUpdate(@Param("id") Long id);
}
