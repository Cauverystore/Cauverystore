package com.cauverystore.repository;

import com.cauverystore.entities.SellerApob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerApobRepository extends JpaRepository<SellerApob, Long> {

    List<SellerApob> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    Optional<SellerApob> findByIdAndSellerId(Long id, Long sellerId);

    List<SellerApob> findBySellerIdAndIsWarehouseTrue(Long sellerId);
}
