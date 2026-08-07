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
    /**
     * All TCS rows for an order - a collection, plus a reversal for each credit note against it.
     * Returns a list rather than an Optional because a returned order legitimately has more than
     * one row, and the single-result form would throw once the first return happened.
     */
    List<TcsRecord> findByOrderId(Long orderId);

    Optional<TcsRecord> findByOrderIdAndEntryType(Long orderId, String entryType);

    /** Guards against a second reversal for the same credit note if the flow is retried. */
    Optional<TcsRecord> findByCreditNoteId(Long creditNoteId);
    Page<TcsRecord> findBySellerIdOrderByTransactionDateDesc(Long sellerId, Pageable pageable);
    List<TcsRecord> findBySellerIdOrderByTransactionDateDesc(Long sellerId);
    List<TcsRecord> findBySellerIdAndPeriod(Long sellerId, String period);
    List<TcsRecord> findBySellerGstinAndTransactionDateBetween(String gstin, LocalDate start, LocalDate end);
    List<TcsRecord> findBySellerGstinAndPeriod(String gstin, String period);
    List<TcsRecord> findBySellerGstinAndFilingStatus(String gstin, String filingStatus);
    List<TcsRecord> findByFilingStatus(String filingStatus);
}
