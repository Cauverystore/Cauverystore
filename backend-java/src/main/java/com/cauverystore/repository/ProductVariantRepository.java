package com.cauverystore.repository;

import com.cauverystore.entities.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProduct_Id(Long productId);

    // A size has its own stock. Same atomic shape as ProductRepository.decrementStockIfAvailable,
    // so two shoppers cannot both buy the last medium.
    @Modifying
    @Query("UPDATE ProductVariant v SET v.stock = v.stock - :qty WHERE v.id = :variantId AND v.stock >= :qty")
    int decrementStockIfAvailable(@Param("variantId") Long variantId, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE ProductVariant v SET v.stock = v.stock + :qty WHERE v.id = :variantId")
    int incrementStock(@Param("variantId") Long variantId, @Param("qty") int qty);
}
