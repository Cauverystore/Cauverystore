package com.cauverystore.repository;

import com.cauverystore.entities.SellerGstin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerGstinRepository extends JpaRepository<SellerGstin, Long> {

    List<SellerGstin> findBySellerId(Long sellerId);

    Optional<SellerGstin> findBySellerIdAndGstin(Long sellerId, String gstin);

    Optional<SellerGstin> findBySellerIdAndPrimaryFlagTrue(Long sellerId);

    Optional<SellerGstin> findByGstin(String gstin);
}
