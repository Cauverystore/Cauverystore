package com.cauverystore.service;

import com.cauverystore.repository.*;
import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    /**
     * Every item sold in orders placed between {@code from} and {@code to} (inclusive), for the
     * itemised sheets of the sales report: sorted by category, then order date and order.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSalesItems(LocalDate from, LocalDate to) {
        return itemRows(orderRepo.findByCreatedAtBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
    }

    static final String NO_CATEGORY = "Uncategorised";

    static List<Map<String, Object>> itemRows(Collection<Order> orders) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order o : orders) {
            if (o == null || o.getItems() == null) continue;
            for (OrderItem item : o.getItems()) {
                var product = item.getProduct();
                String category = product != null && product.getCategory() != null
                        && product.getCategory().getName() != null && !product.getCategory().getName().isBlank()
                        ? product.getCategory().getName() : NO_CATEGORY;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orderId", o.getId());
                m.put("orderDate", o.getCreatedAt() != null ? o.getCreatedAt().toLocalDate().toString() : null);
                m.put("orderStatus", o.getStatus());
                m.put("category", category);
                m.put("productId", product != null ? product.getId() : null);
                m.put("productName", product != null ? product.getName() : "(deleted product)");
                m.put("sku", product != null ? product.getSku() : null);
                m.put("quantity", item.getQuantity());
                m.put("unitPrice", item.getPrice());
                m.put("lineTotal", Math.round(item.getPrice() * item.getQuantity() * 100.0) / 100.0);
                rows.add(m);
            }
        }
        rows.sort(Comparator
                .comparing((Map<String, Object> r) -> NO_CATEGORY.equals(r.get("category")) ? 1 : 0)
                .thenComparing(r -> ((String) r.get("category")).toLowerCase())
                .thenComparing(r -> r.get("orderDate") == null ? "" : (String) r.get("orderDate"))
                .thenComparing(r -> r.get("orderId") == null ? 0L : ((Number) r.get("orderId")).longValue()));
        return rows;
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
