package com.cauverystore.repository;

import com.cauverystore.entities.AgentJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AgentJobRepository extends JpaRepository<AgentJob, Long> {

    List<AgentJob> findByAgentNameAndStatusOrderByIdAsc(String agentName, String status, Pageable page);

    List<AgentJob> findByStatusInOrderByIdDesc(Collection<String> statuses, Pageable page);

    boolean existsBySourceUrlAndStatusIn(String sourceUrl, Collection<String> statuses);

    List<AgentJob> findByStatusAndUpdatedAtBefore(String status, LocalDateTime before);

    long countByAgentNameAndStatus(String agentName, String status);

    long countByAgentNameAndStatusAndUpdatedAtBefore(String agentName, String status, LocalDateTime before);

    java.util.Optional<AgentJob> findFirstByProductIdOrderByIdDesc(Long productId);
}
