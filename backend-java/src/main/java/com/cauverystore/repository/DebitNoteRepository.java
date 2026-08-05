package com.cauverystore.repository;

import com.cauverystore.entities.DebitNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DebitNoteRepository extends JpaRepository<DebitNote, Long> {
    Optional<DebitNote> findByDebitNoteNumber(String debitNoteNumber);
    Optional<DebitNote> findByOrderIdAndNoteType(Long orderId, String noteType);
    Optional<DebitNote> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);
    Page<DebitNote> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);
    Page<DebitNote> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
    List<DebitNote> findBySellerGstinAndDebitNoteDateBetween(String gstin, LocalDate start, LocalDate end);
    List<DebitNote> findByDebitNoteDateBetween(LocalDate start, LocalDate end);

    @Query("SELECT MAX(d.debitNoteNumber) FROM DebitNote d WHERE d.debitNoteNumber LIKE :prefix%")
    String findMaxDebitNoteNumberByPrefix(String prefix);
}
