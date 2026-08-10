package com.cauverystore.repository;

import com.cauverystore.entities.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    /**
     * Refunds already raised against a return.
     *
     * A list rather than an Optional on purpose: a first attempt that failed at the gateway is
     * still a row, and a retry has to be able to see it went FAILED and try again rather than
     * find "a refund exists" and stop.
     */
    java.util.List<Refund> findByReturnRequestId(Long returnRequestId);

    @Query("SELECT r.status, COUNT(r), SUM(r.amount) FROM Refund r GROUP BY r.status")
    List<Object[]> getRefundAnalytics();

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r")
    Double getTotalRefundAmount();

    @Query("SELECT COUNT(r) FROM Refund r")
    Long getTotalRefundCount();
}
