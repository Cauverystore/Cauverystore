package com.cauverystore.repository;

import com.cauverystore.entities.GstInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GstInvoiceRepository extends JpaRepository<GstInvoice, Long> {
    Optional<GstInvoice> findByInvoiceNumber(String invoiceNumber);
    Optional<GstInvoice> findByOrderId(Long orderId);
    Optional<GstInvoice> findByIrn(String irn);
    Page<GstInvoice> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);
    List<GstInvoice> findByStatus(String status);
    List<GstInvoice> findByStatusIn(List<String> statuses);
    long countByStatus(String status);

    @Query("SELECT MAX(i.invoiceNumber) FROM GstInvoice i WHERE i.invoiceNumber LIKE :prefix%")
    String findMaxInvoiceNumberByPrefix(String prefix);

    List<GstInvoice> findByInvoiceDateBetween(LocalDate start, LocalDate end);
    List<GstInvoice> findBySellerGstinAndInvoiceDateBetween(String gstin, LocalDate start, LocalDate end);
}
