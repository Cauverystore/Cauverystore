package com.cauverystore.repository;

import com.cauverystore.entities.CommissionRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommissionRateRepository extends JpaRepository<CommissionRate, Long> {

    /**
     * Candidates for a seller: their own rates, category rates and the platform default. The
     * most specific in-force row wins, which is decided in the service rather than in SQL so
     * the precedence rule is readable.
     */
    List<CommissionRate> findBySellerIdIsNullOrSellerId(Long sellerId);
}
