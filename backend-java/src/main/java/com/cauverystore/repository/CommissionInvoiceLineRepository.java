package com.cauverystore.repository;

import com.cauverystore.entities.CommissionInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommissionInvoiceLineRepository extends JpaRepository<CommissionInvoiceLine, Long> {
    List<CommissionInvoiceLine> findByOrderId(Long orderId);
}
