package com.cauverystore.repository;

import com.cauverystore.entities.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProduct_Id(Long productId);

    long countByProduct_Id(Long productId);

    @Modifying
    @Transactional
    void deleteByProduct_IdAndId(Long productId, Long id);
}
