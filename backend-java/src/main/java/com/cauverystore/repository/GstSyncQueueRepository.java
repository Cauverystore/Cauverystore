package com.cauverystore.repository;

import com.cauverystore.entities.GstSyncQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GstSyncQueueRepository extends JpaRepository<GstSyncQueue, Long> {
    List<GstSyncQueue> findByStatusOrderByCreatedAtAsc(String status);
    List<GstSyncQueue> findByStatusInOrderByCreatedAtAsc(List<String> statuses);
    long countByStatus(String status);
    List<GstSyncQueue> findByInvoiceIdAndSyncType(Long invoiceId, String syncType);
}
