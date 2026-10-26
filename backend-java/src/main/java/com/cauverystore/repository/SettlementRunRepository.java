package com.cauverystore.repository;

import com.cauverystore.entities.SettlementRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettlementRunRepository extends JpaRepository<SettlementRun, Long> {
    List<SettlementRun> findAllByOrderByIdDesc();
    List<SettlementRun> findByStatus(String status);
}
