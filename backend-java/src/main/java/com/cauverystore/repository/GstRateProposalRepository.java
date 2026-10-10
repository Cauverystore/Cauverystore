package com.cauverystore.repository;

import com.cauverystore.entities.GstRateProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GstRateProposalRepository extends JpaRepository<GstRateProposal, Long> {

    List<GstRateProposal> findBySourceIdOrderByIdAsc(Long sourceId);

    List<GstRateProposal> findByStatusOrderByIdAsc(String status);

    boolean existsBySourceId(Long sourceId);

    long countBySourceIdAndStatus(Long sourceId, String status);

    long countByStatus(String status);
}
