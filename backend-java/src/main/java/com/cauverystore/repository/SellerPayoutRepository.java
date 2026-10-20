package com.cauverystore.repository;

import com.cauverystore.entities.SellerPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SellerPayoutRepository extends JpaRepository<SellerPayout, Long> {

    List<SellerPayout> findBySellerIdOrderByPaidOnDescIdDesc(Long sellerId);

    List<SellerPayout> findAllByOrderByPaidOnDescIdDesc();

    /** The same bank reference entered twice for one seller is a double entry, not two payments. */
    boolean existsBySellerIdAndReferenceIgnoreCaseAndVoidedFalse(Long sellerId, String reference);
}
