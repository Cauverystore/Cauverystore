package com.cauverystore.repository;

import com.cauverystore.entities.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    List<ReturnRequest> findByUserId(Long userId);
    List<ReturnRequest> findByOrderId(Long orderId);
    List<ReturnRequest> findByStatus(String status);
    List<ReturnRequest> findByProductId(Long productId);

    @Query("SELECT r.status, COUNT(r) FROM ReturnRequest r GROUP BY r.status")
    List<Object[]> countByStatusGrouped();

    @Query("SELECT r.product.sellerId, COUNT(r), COALESCE(SUM(r.refundAmount), 0) FROM ReturnRequest r GROUP BY r.product.sellerId")
    List<Object[]> findReturnStatsBySeller();
}
