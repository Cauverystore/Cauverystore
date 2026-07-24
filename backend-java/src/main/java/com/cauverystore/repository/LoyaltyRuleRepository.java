package com.cauverystore.repository;

import com.cauverystore.entities.LoyaltyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoyaltyRuleRepository extends JpaRepository<LoyaltyRule, Long> {
}
