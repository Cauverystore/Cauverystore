package com.cauverystore.repository;

import com.cauverystore.entities.TcsRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TcsRecordRepository extends JpaRepository<TcsRecord, Long> {
    Optional<TcsRecord> findByOrderId(Long orderId);
    Page<TcsRecord> findBySellerIdOrderByTransactionDateDesc(Long sellerId, Pageable pageable);
    List<TcsRecord> findBySellerIdOrderByTransactionDateDesc(Long sellerId);
    List<TcsRecord> findBySellerIdAndPeriod(Long sellerId, String period);
    List<TcsRecord> findBySellerGstinAndTransactionDateBetween(String gstin, LocalDate start, LocalDate end);
    List<TcsRecord> findBySellerGstinAndPeriod(String gstin, String period);
    List<TcsRecord> findBySellerGstinAndFilingStatus(String gstin, String filingStatus);
    List<TcsRecord> findByFilingStatus(String filingStatus);
}
