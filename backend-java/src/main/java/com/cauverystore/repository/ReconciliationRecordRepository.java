package com.cauverystore.repository;

import com.cauverystore.entities.ReconciliationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, Long> {
    Optional<ReconciliationRecord> findBySellerGstinAndPeriod(String sellerGstin, String period);
    List<ReconciliationRecord> findBySellerGstinOrderByPeriodDesc(String sellerGstin);
    List<ReconciliationRecord> findBySellerGstinAndStatus(String sellerGstin, String status);
}
