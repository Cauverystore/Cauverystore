package com.cauverystore.erp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ErpSyncRecordRepository extends JpaRepository<ErpSyncRecord, Long> {
    Optional<ErpSyncRecord> findByEntityTypeAndEntityId(String entityType, Long entityId);
    long countByStatus(String status);
    List<ErpSyncRecord> findTop50ByStatusOrderByUpdatedAtDesc(String status);
}
