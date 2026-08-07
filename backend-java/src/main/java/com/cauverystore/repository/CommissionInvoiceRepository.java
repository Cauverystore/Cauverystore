package com.cauverystore.repository;

import com.cauverystore.entities.CommissionInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionInvoiceRepository extends JpaRepository<CommissionInvoice, Long> {

    /** One commission invoice per seller per month - this is what makes a re-run idempotent. */
    Optional<CommissionInvoice> findBySellerIdAndPeriod(Long sellerId, String period);

    List<CommissionInvoice> findByPeriod(String period);

    List<CommissionInvoice> findBySellerIdOrderByInvoiceDateDesc(Long sellerId);

    List<CommissionInvoice> findByInvoiceDateBetween(LocalDate start, LocalDate end);

    Optional<CommissionInvoice> findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(String prefix);
}
