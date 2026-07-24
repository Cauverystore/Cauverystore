package com.cauverystore.service;

import com.cauverystore.repository.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReportService {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final RefundRepository refundRepo;
    private final CouponRepository couponRepo;

    public ReportService(OrderRepository orderRepo, OrderItemRepository orderItemRepo,
                         ProductRepository productRepo, UserRepository userRepo,
                         RefundRepository refundRepo, CouponRepository couponRepo) {
        this.orderRepo = orderRepo; this.orderItemRepo = orderItemRepo;
        this.productRepo = productRepo; this.userRepo = userRepo;
        this.refundRepo = refundRepo; this.couponRepo = couponRepo;
    }

    public Map<String, Object> getSalesReport(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        var orders = orderRepo.findByCreatedAtBetween(start, end);
        double totalRevenue = orders.stream().filter(o -> !"CANCELLED".equals(o.getStatus()))
                .mapToDouble(o -> o.getTotalAmount()).sum();
        long totalOrders = orders.size();
        long cancelled = orders.stream().filter(o -> "CANCELLED".equals(o.getStatus())).count();
        Map<String, Object> r = new HashMap<>();
        r.put("totalOrders", totalOrders); r.put("totalRevenue", totalRevenue);
        r.put("cancelledOrders", cancelled); r.put("from", from); r.put("to", to);
        return r;
    }

    public List<Map<String, Object>> getProductReport() {
        var products = productRepo.findAll();
        List<Map<String, Object>> list = new ArrayList<>();
        for (var p : products) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId()); m.put("name", p.getName()); m.put("price", p.getPrice());
            m.put("stock", p.getStock()); m.put("active", p.isActive());
            list.add(m);
        }
        return list;
    }
}
