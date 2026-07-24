package com.cauverystore.repository;

import com.cauverystore.entities.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder_Id(Long orderId);

    @Query("SELECT oi.product.id, oi.product.name, SUM(oi.quantity) as totalSold, SUM(oi.price * oi.quantity) as totalRevenue FROM OrderItem oi WHERE oi.order.createdAt BETWEEN :start AND :end AND oi.order.status <> 'CANCELLED' GROUP BY oi.product.id, oi.product.name ORDER BY totalSold DESC")
    List<Object[]> findTopProducts(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
