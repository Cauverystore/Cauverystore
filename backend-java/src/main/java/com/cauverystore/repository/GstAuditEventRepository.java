package com.cauverystore.repository;

import com.cauverystore.entities.GstAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GstAuditEventRepository extends JpaRepository<GstAuditEvent, Long> {

    Optional<GstAuditEvent> findTopByOrderByIdDesc();

    Page<GstAuditEvent> findAllByOrderByIdAsc(Pageable pageable);

    Page<GstAuditEvent> findAllByOrderByIdDesc(Pageable pageable);

    List<GstAuditEvent> findByEntityTypeAndEntityRefOrderByIdAsc(String entityType, String entityRef);
}
