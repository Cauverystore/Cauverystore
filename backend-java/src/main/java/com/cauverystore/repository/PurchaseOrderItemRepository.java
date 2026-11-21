package com.cauverystore.repository;

import com.cauverystore.entities.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {
    List<PurchaseOrderItem> findByPurchaseOrderId(Long purchaseOrderId);
    List<PurchaseOrderItem> findByProductId(Long productId);
    boolean existsByProductId(Long productId);

    @Modifying
    @Query("UPDATE PurchaseOrderItem i SET i.product = null, i.variant = null WHERE i.product.id = :productId")
    int clearProduct(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE PurchaseOrderItem i SET i.variant = null WHERE i.variant.id = :variantId")
    int clearVariant(@Param("variantId") Long variantId);
}
