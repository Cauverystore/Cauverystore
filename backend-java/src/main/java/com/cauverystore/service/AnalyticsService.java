package com.cauverystore.service;

import com.cauverystore.repository.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AnalyticsService {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final RefundRepository refundRepo;
    private final CouponRepository couponRepo;

    public AnalyticsService(OrderRepository orderRepo, OrderItemRepository orderItemRepo,
                            ProductRepository productRepo, UserRepository userRepo,
                            RefundRepository refundRepo, CouponRepository couponRepo) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.refundRepo = refundRepo;
        this.couponRepo = couponRepo;
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOrders", orderRepo.count());
        stats.put("totalProducts", productRepo.count());
        stats.put("totalUsers", userRepo.count());
        stats.put("totalRevenue", orderRepo.findAll().stream()
                .filter(o -> !"CANCELLED".equals(o.getStatus()) && !"REFUNDED".equals(o.getStatus()))
                .mapToDouble(o -> o.getTotalAmount()).sum());
        stats.put("totalRefunds", refundRepo.count());
        stats.put("lowStockProducts", productRepo.findByStockLessThan(10).size());
        return stats;
    }

    public Map<String, Object> getSalesChart(LocalDate from, LocalDate to, String groupBy) {
        LocalDateTime start = from != null ? from.atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = to != null ? to.plusDays(1).atStartOfDay() : LocalDate.now().plusDays(1).atStartOfDay();
        List<Object[]> raw = orderRepo.findSalesBetween(start, end);
        List<Map<String, Object>> points = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> p = new HashMap<>();
            p.put("date", row[0].toString());
            p.put("sales", row[1]);
            p.put("orders", row[2]);
            points.add(p);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("points", points);
        result.put("total", points.stream().mapToDouble(p -> (Double) p.get("sales")).sum());
        return result;
    }

    public List<Map<String, Object>> getTopProducts(LocalDate from, LocalDate to, int limit) {
        LocalDateTime start = from != null ? from.atStartOfDay() : LocalDate.now().minusDays(90).atStartOfDay();
        LocalDateTime end = to != null ? to.plusDays(1).atStartOfDay() : LocalDate.now().plusDays(1).atStartOfDay();
        List<Object[]> raw = orderItemRepo.findTopProducts(start, end);
        if (raw.size() > limit) raw = raw.subList(0, limit);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> p = new HashMap<>();
            p.put("productId", row[0]);
            p.put("name", row[1]);
            p.put("totalSold", row[2]);
            p.put("totalRevenue", row[3]);
            list.add(p);
        }
        return list;
    }
}
