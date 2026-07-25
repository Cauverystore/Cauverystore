package com.cauverystore.repository;

import com.cauverystore.entities.GstInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GstInvoiceItemRepository extends JpaRepository<GstInvoiceItem, Long> {
    List<GstInvoiceItem> findByInvoiceId(Long invoiceId);
}
