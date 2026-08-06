package com.cauverystore.repository;

import com.cauverystore.entities.MasterUpdateLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MasterUpdateLogRepository extends JpaRepository<MasterUpdateLog, Long> {
    List<MasterUpdateLog> findTop50ByOrderByCreatedAtDesc();
}
