package com.cauverystore.repository;

import com.cauverystore.entities.GstRuleUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GstRuleUpdateRepository extends JpaRepository<GstRuleUpdate, Long> {

    boolean existsByKindAndCbicDocumentId(String kind, Long cbicDocumentId);

    List<GstRuleUpdate> findAllByOrderByIssuedOnDescIdDesc();

    long countByStatus(String status);
}
