package com.cauverystore.repository;

import com.cauverystore.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    @Query("select distinct a.action from AuditLog a where a.action is not null order by a.action")
    List<String> findDistinctActions();
    List<AuditLog> findAllByOrderByTimestampDesc();
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime from, LocalDateTime to);
    List<AuditLog> findByUserIdOrderByTimestampDesc(Long userId);
}
