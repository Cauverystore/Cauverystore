package com.cauverystore.repository;

import com.cauverystore.entities.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    boolean existsByRefKey(String refKey);

    List<LedgerEntry> findByOrderIdOrderByIdAsc(Long orderId);

    List<LedgerEntry> findBySettlementRunId(Long runId);

    /** Lines not yet paid out that are payable on or before the cutoff. */
    List<LedgerEntry> findBySettlementRunIdIsNullAndAvailableOnLessThanEqual(LocalDate cutoff);

    /** Pass 0 for every seller and an empty string for every kind; typed values avoid untyped NULL parameters on PostgreSQL. */
    @Query("SELECT e FROM LedgerEntry e WHERE (:sellerId = 0L OR e.sellerId = :sellerId) "
            + "AND (:type = '' OR e.entryType = :type) ORDER BY e.id DESC")
    Page<LedgerEntry> search(@Param("sellerId") long sellerId, @Param("type") String type, Pageable pageable);

    /** Per seller: [sellerId, available now, still waiting] over lines not yet paid out. */
    @Query("SELECT e.sellerId, "
            + "COALESCE(SUM(CASE WHEN e.availableOn <= :today THEN e.amountPaise ELSE 0L END), 0L), "
            + "COALESCE(SUM(CASE WHEN e.availableOn > :today THEN e.amountPaise ELSE 0L END), 0L) "
            + "FROM LedgerEntry e WHERE e.settlementRunId IS NULL GROUP BY e.sellerId")
    List<Object[]> balances(@Param("today") LocalDate today);

    @Query("SELECT COALESCE(SUM(e.amountPaise), 0L) FROM LedgerEntry e WHERE e.settlementRunId = :runId")
    long sumForRun(@Param("runId") Long runId);

    @Query("SELECT COUNT(DISTINCT e.orderId) FROM LedgerEntry e WHERE e.source = 'ORDER'")
    long countPostedOrders();
}
