package com.cauverystore.repository;

import com.cauverystore.entities.SellerStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerStoreRepository extends JpaRepository<SellerStore, Long> {
    Optional<SellerStore> findBySellerId(Long sellerId);
    Optional<SellerStore> findByStoreSlug(String slug);
}