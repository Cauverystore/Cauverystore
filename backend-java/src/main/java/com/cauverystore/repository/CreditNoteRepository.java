package com.cauverystore.repository;

import com.cauverystore.entities.CreditNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {
    Optional<CreditNote> findByCreditNoteNumber(String creditNoteNumber);
    Optional<CreditNote> findByOrderIdAndNoteType(Long orderId, String noteType);
    Optional<CreditNote> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);
    Page<CreditNote> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);
    Page<CreditNote> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
    List<CreditNote> findBySellerGstinAndCreditNoteDateBetween(String gstin, LocalDate start, LocalDate end);
    List<CreditNote> findByCreditNoteDateBetween(LocalDate start, LocalDate end);

    @Query("SELECT MAX(c.creditNoteNumber) FROM CreditNote c WHERE c.creditNoteNumber LIKE :prefix%")
    String findMaxCreditNoteNumberByPrefix(String prefix);
}
