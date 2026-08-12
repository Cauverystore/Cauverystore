package com.cauverystore.repository;

import com.cauverystore.entities.Order;
import com.cauverystore.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUser(User user);

    Page<Order> findByStatus(String status, Pageable pageable);

    List<Order> findByUserOrderByCreatedAtDesc(User user);

    // Address management: how often an address has been used, and whether any still-active order
    // (anything other than a terminal CANCELLED/REFUNDED/DELIVERED) is tied to it - an address
    // in use by a live order must not be removed.
    long countByAddress_Id(Long addressId);

    long countByAddress_IdAndStatusNotIn(Long addressId, Collection<String> statuses);

    @Modifying
    @Query("UPDATE Order o SET o.address = :canonical WHERE o.address = :dupe")
    int redirectOrders(@Param("dupe") com.cauverystore.entities.Address dupe,
                       @Param("canonical") com.cauverystore.entities.Address canonical);

    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC")
    List<Order> getRecentOrders();

    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end")
    List<Order> findByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT CAST(o.createdAt AS date) as date, SUM(o.totalAmount) as sales, COUNT(o.id) as orders FROM Order o WHERE o.createdAt BETWEEN :start AND :end AND o.status <> 'CANCELLED' GROUP BY CAST(o.createdAt AS date) ORDER BY CAST(o.createdAt AS date)")
    List<Object[]> findSalesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Order> findByStatusIn(List<String> statuses);

    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countOrdersByStatus();

    @Query("SELECT o.sellerId, COUNT(o), COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = 'DELIVERED' GROUP BY o.sellerId")
    List<Object[]> findSalesGroupedBySeller();

    @Query("SELECT c.name, COALESCE(SUM(oi.price * oi.quantity), 0) FROM OrderItem oi JOIN oi.product p JOIN p.category c JOIN oi.order o WHERE o.status = 'DELIVERED' GROUP BY c.name ORDER BY SUM(oi.price * oi.quantity) DESC")
    List<Object[]> findSalesGroupedByCategory();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = 'DELIVERED' AND o.createdAt BETWEEN :start AND :end")
    Double getRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = 'DELIVERED'")
    Double getTotalRevenueDelivered();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    Long countByOrderStatus(@Param("status") String status);

    @Query("SELECT p.name, p.productCode, SUM(oi.quantity) as totalSold FROM OrderItem oi JOIN oi.product p JOIN oi.order o WHERE o.status = 'DELIVERED' GROUP BY p.id, p.name, p.productCode ORDER BY totalSold DESC")
    List<Object[]> findTopSellingProducts(Pageable pageable);

    @Query("SELECT o.region, COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = 'DELIVERED' AND (:state IS NULL OR o.region = :state) GROUP BY o.region")
    List<Object[]> findSalesByState(@Param("state") String state);
}
