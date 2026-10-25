package com.cauverystore.repository;

import com.cauverystore.entities.SettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {
    List<SettlementItem> findByRunIdOrderByIdAsc(Long runId);
    List<SettlementItem> findBySellerIdOrderByIdDesc(Long sellerId);
}
