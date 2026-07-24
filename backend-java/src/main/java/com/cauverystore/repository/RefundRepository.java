package com.cauverystore.repository;

import com.cauverystore.entities.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    @Query("SELECT r.status, COUNT(r), SUM(r.amount) FROM Refund r GROUP BY r.status")
    List<Object[]> getRefundAnalytics();

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r")
    Double getTotalRefundAmount();

    @Query("SELECT COUNT(r) FROM Refund r")
    Long getTotalRefundCount();
}
