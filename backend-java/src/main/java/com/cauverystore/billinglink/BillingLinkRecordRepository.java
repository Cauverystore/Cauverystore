package com.cauverystore.billinglink;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingLinkRecordRepository extends JpaRepository<BillingLinkRecord, Long> {
    Optional<BillingLinkRecord> findByInvoiceId(Long invoiceId);
    long countByStatus(String status);
    List<BillingLinkRecord> findTop50ByStatusOrderByUpdatedAtDesc(String status);
}
