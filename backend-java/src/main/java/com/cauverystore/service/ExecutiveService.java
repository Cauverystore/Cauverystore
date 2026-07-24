package com.cauverystore.service;

import com.cauverystore.entities.Order;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.ProductAnalytics;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.entities.Warehouse;
import com.cauverystore.repository.InventoryRepository;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.ProductAnalyticsRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.ProductReviewRepository;
import com.cauverystore.repository.RefundRepository;
import com.cauverystore.repository.ReturnRequestRepository;
import com.cauverystore.repository.StockMovementRepository;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.repository.WarehouseRepository;
import com.cauverystore.repository.WishlistRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ExecutiveService {

    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final ReturnRequestRepository returnRepo;
    private final ProductReviewRepository reviewRepo;
    private final ProductAnalyticsRepository analyticsRepo;
    private final WarehouseRepository warehouseRepo;
    private final RefundRepository refundRepo;
    private final WishlistRepository wishlistRepo;
    private final InventoryRepository inventoryRepo;
    private final StockMovementRepository stockMovementRepo;
    private final AuditService auditService;
    private final AuthorizationService authService;

    public ExecutiveService(OrderRepository orderRepo, ProductRepository productRepo, UserRepository userRepo, ReturnRequestRepository returnRepo, ProductReviewRepository reviewRepo, ProductAnalyticsRepository analyticsRepo, WarehouseRepository warehouseRepo, RefundRepository refundRepo, WishlistRepository wishlistRepo, InventoryRepository inventoryRepo, StockMovementRepository stockMovementRepo, AuditService auditService, AuthorizationService authService) {
        this.orderRepo = orderRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.returnRepo = returnRepo;
        this.reviewRepo = reviewRepo;
        this.analyticsRepo = analyticsRepo;
        this.warehouseRepo = warehouseRepo;
        this.refundRepo = refundRepo;
        this.wishlistRepo = wishlistRepo;
        this.inventoryRepo = inventoryRepo;
        this.stockMovementRepo = stockMovementRepo;
        this.auditService = auditService;
        this.authService = authService;
    }

    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalProducts", productRepo.count());
        summary.put("activeProducts", (long) productRepo.findByActiveTrue().size());
        summary.put("totalOrders", orderRepo.count());
        summary.put("totalCustomers", userRepo.countByRole(Role.CUSTOMER));
        summary.put("totalSellers", userRepo.countByRole(Role.SELLER));
        summary.put("totalRevenue", orderRepo.getTotalRevenueDelivered());
        long inProgress = orderRepo.countByOrderStatus("PLACED") + orderRepo.countByOrderStatus("PROCESSING") + orderRepo.countByOrderStatus("SHIPPED");
        summary.put("inProgressOrders", inProgress);
        summary.put("lowStockAlerts", productRepo.countLowStockProducts(10));
        summary.put("pendingApprovals", (long) productRepo.findByApprovalStatus("PENDING").size());
        return summary;
    }

    public Map<String, Object> getSalesOverview() {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.atTime(LocalTime.MAX);
        LocalDateTime weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dailySales", orderRepo.getRevenueBetween(dayStart, dayEnd));
        result.put("weeklySales", orderRepo.getRevenueBetween(weekStart, dayEnd));
        result.put("monthlySales", orderRepo.getRevenueBetween(monthStart, dayEnd));
        result.put("totalRevenue", orderRepo.getTotalRevenueDelivered());
        LocalDateTime thirtyDaysAgo = today.minusDays(30).atStartOfDay();
        List<Object[]> rawTrends = orderRepo.findSalesBetween(thirtyDaysAgo, dayEnd);
        List<Map<String, Object>> trends = new ArrayList<>();
        for (Object[] row : rawTrends) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", row[0] != null ? row[0].toString() : "");
            entry.put("sales", row[1] != null ? row[1] : 0);
            entry.put("orders", row[2] != null ? row[2] : 0);
            trends.add(entry);
        }
        result.put("revenueTrends", trends);
        return result;
    }

    public List<Map<String, Object>> getTopSellingProducts(int limit) {
        List<Object[]> raw = orderRepo.findTopSellingProducts(PageRequest.of(0, limit));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", row[0]);
            item.put("productCode", row[1]);
            item.put("totalSold", row[2]);
            list.add(item);
        }
        return list;
    }

    public List<Map<String, Object>> getSellerSalesBreakdown() {
        List<Object[]> raw = orderRepo.findSalesGroupedBySeller();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> item = new LinkedHashMap<>();
            Long sellerId = (Long) row[0];
            item.put("sellerId", sellerId);
            Optional<User> seller = sellerId != null ? Optional.ofNullable(userRepo.findById(sellerId).orElse(null)) : Optional.empty();
            item.put("sellerName", seller.map(User::getFullName).orElse("Unknown"));
            item.put("orderCount", row[1]);
            item.put("totalSales", row[2]);
            list.add(item);
        }
        return list;
    }

    public List<Order> getOrdersInProgress() {
        return orderRepo.findByStatusIn(Arrays.asList("PLACED", "PROCESSING", "SHIPPED"));
    }

    public List<Order> getCompletedOrders() {
        return orderRepo.findByStatusIn(Collections.singletonList("DELIVERED"));
    }

    public Map<String, Object> getReturnRefundStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRefunds", refundRepo.getTotalRefundCount());
        stats.put("totalRefundAmount", refundRepo.getTotalRefundAmount());
        List<Object[]> returnStatusCounts = returnRepo.countByStatusGrouped();
        List<Map<String, Object>> returnBreakdown = new ArrayList<>();
        for (Object[] row : returnStatusCounts) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("status", row[0]);
            entry.put("count", row[1]);
            returnBreakdown.add(entry);
        }
        stats.put("returnBreakdown", returnBreakdown);
        List<com.cauverystore.entities.ReturnRequest> allReturns = returnRepo.findAll();
        long replacements = allReturns.stream().filter(r -> r.getReplacementOrderId() != null).count();
        stats.put("replacements", replacements);
        return stats;
    }

    public List<Product> getPendingApprovals() {
        return productRepo.findByApprovalStatus("PENDING");
    }

    public Map<String, Object> getInventoryInsights() {
        Map<String, Object> insights = new LinkedHashMap<>();
        int threshold = 10;
        insights.put("lowStockThreshold", threshold);
        insights.put("lowStockCount", productRepo.countLowStockProducts(threshold));
        insights.put("outOfStockCount", productRepo.countByStock(0));
        List<Product> lowStock = productRepo.findByStockLessThan(threshold);
        List<Map<String, Object>> lowStockList = new ArrayList<>();
        for (Product p : lowStock) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("name", p.getName());
            item.put("stock", p.getStock());
            item.put("sku", p.getSku());
            lowStockList.add(item);
        }
        insights.put("lowStockProducts", lowStockList);
        List<Product> outOfStock = productRepo.findByStockLessThan(1);
        List<Map<String, Object>> oosList = new ArrayList<>();
        for (Product p : outOfStock) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("name", p.getName());
            item.put("sku", p.getSku());
            oosList.add(item);
        }
        insights.put("outOfStockProducts", oosList);
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        List<ProductAnalytics> recentAnalytics = analyticsRepo.findByDateBetweenOrderByDateDesc(thirtyDaysAgo, LocalDate.now());
        Map<Long, Map<String, Object>> productStats = new LinkedHashMap<>();
        for (ProductAnalytics pa : recentAnalytics) {
            Long pid = pa.getProduct().getId();
            productStats.putIfAbsent(pid, new LinkedHashMap<>());
            Map<String, Object> ps = productStats.get(pid);
            ps.put("productId", pid);
            ps.put("productName", pa.getProduct().getName());
            ps.merge("totalOrders", pa.getOrders() != null ? pa.getOrders() : 0L, (a, b) -> (Long) a + (Long) b);
            ps.merge("totalViews", pa.getViews() != null ? pa.getViews() : 0L, (a, b) -> (Long) a + (Long) b);
        }
        List<Map<String, Object>> fastMoving = productStats.values().stream()
            .sorted((a, b) -> Long.compare((Long) b.getOrDefault("totalOrders", 0L), (Long) a.getOrDefault("totalOrders", 0L)))
            .limit(10).collect(Collectors.toList());
        List<Map<String, Object>> slowMoving = productStats.values().stream()
            .filter(m -> (Long) m.getOrDefault("totalOrders", 0L) < 5L)
            .sorted((a, b) -> Long.compare((Long) a.getOrDefault("totalOrders", 0L), (Long) b.getOrDefault("totalOrders", 0L)))
            .limit(10).collect(Collectors.toList());
        insights.put("fastMoving", fastMoving);
        insights.put("slowMoving", slowMoving);
        return insights;
    }

    public List<Warehouse> getWarehouseStockSummary() {
        return warehouseRepo.findByActiveTrue();
    }

    public List<Map<String, Object>> getSellerPerformance() {
        List<User> sellers = userRepo.findByRole(Role.SELLER);
        Map<Long, Map<String, Object>> sellerStats = new LinkedHashMap<>();
        for (User seller : sellers) {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("sellerId", seller.getId());
            stats.put("sellerName", seller.getFullName());
            stats.put("email", seller.getEmail());
            stats.put("status", seller.getStatus());
            stats.put("joinedAt", seller.getCreatedAt() != null ? seller.getCreatedAt().toString() : "");
            stats.put("productCount", productRepo.countBySellerId(seller.getId()));
            sellerStats.put(seller.getId(), stats);
        }
        List<Object[]> salesData = orderRepo.findSalesGroupedBySeller();
        for (Object[] row : salesData) {
            Long sellerId = (Long) row[0];
            if (sellerId != null && sellerStats.containsKey(sellerId)) {
                sellerStats.get(sellerId).put("orderCount", row[1]);
                sellerStats.get(sellerId).put("totalSales", row[2]);
            }
        }
        List<Object[]> returnData = returnRepo.findReturnStatsBySeller();
        for (Object[] row : returnData) {
            Long sellerId = (Long) row[0];
            if (sellerId != null && sellerStats.containsKey(sellerId)) {
                sellerStats.get(sellerId).put("returnCount", row[1]);
                sellerStats.get(sellerId).put("refundAmount", row[2]);
                long orderCount = ((Number) sellerStats.get(sellerId).getOrDefault("orderCount", 0L)).longValue();
                long returnCount = ((Number) row[1]).longValue();
                double rate = orderCount > 0 ? (double) returnCount / orderCount * 100 : 0;
                sellerStats.get(sellerId).put("returnRate", Math.round(rate * 100.0) / 100.0);
            }
        }
        for (Map<String, Object> stats : sellerStats.values()) {
            stats.putIfAbsent("orderCount", 0L);
            stats.putIfAbsent("totalSales", 0.0);
            stats.putIfAbsent("returnCount", 0L);
            stats.putIfAbsent("refundAmount", 0.0);
            stats.putIfAbsent("returnRate", 0.0);
        }
        return new ArrayList<>(sellerStats.values());
    }

    public Map<String, Object> getCustomerActivitySummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCustomers", userRepo.countByRole(Role.CUSTOMER));
        summary.put("totalOrdersPlaced", orderRepo.count());
        summary.put("deliveredOrders", orderRepo.countByOrderStatus("DELIVERED"));
        summary.put("wishlistItems", wishlistRepo.count());
        return summary;
    }

    public Map<String, Object> getReviewSentimentAnalysis() {
        Map<String, Object> sentiment = new LinkedHashMap<>();
        Double avgRating = reviewRepo.getAverageRating();
        sentiment.put("averageRating", avgRating != null ? Math.round(avgRating * 100.0) / 100.0 : 0.0);
        sentiment.put("totalReviews", reviewRepo.count());
        sentiment.put("approvedReviews", reviewRepo.countByApproved(true));
        List<Object[]> ratingDist = reviewRepo.getRatingDistribution();
        List<Map<String, Object>> distribution = new ArrayList<>();
        for (Object[] row : ratingDist) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rating", row[0]);
            entry.put("count", row[1]);
            distribution.add(entry);
        }
        sentiment.put("ratingDistribution", distribution);
        return sentiment;
    }

    public List<Map<String, Object>> getNotifications() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        long lowStock = productRepo.countLowStockProducts(10);
        if (lowStock > 0) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("type", "LOW_STOCK");
            alert.put("severity", "warning");
            alert.put("message", lowStock + " products are running low on stock");
            alert.put("count", lowStock);
            alerts.add(alert);
        }
        List<Product> pendingApprovals = productRepo.findByApprovalStatus("PENDING");
        if (!pendingApprovals.isEmpty()) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("type", "PENDING_APPROVAL");
            alert.put("severity", "info");
            alert.put("message", pendingApprovals.size() + " products pending admin approval");
            alert.put("count", (long) pendingApprovals.size());
            alerts.add(alert);
        }
        Double avgRating = reviewRepo.getAverageRating();
        if (avgRating != null && avgRating < 3.0) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("type", "NEGATIVE_RATINGS");
            alert.put("severity", "critical");
            alert.put("message", "Average rating is " + String.format("%.1f", avgRating) + " - below threshold");
            alert.put("count", 0L);
            alerts.add(alert);
        }
        long totalRefunds = refundRepo.getTotalRefundCount();
        long totalOrdersDelivered = orderRepo.countByOrderStatus("DELIVERED");
        if (totalOrdersDelivered > 0) {
            double refundRate = (double) totalRefunds / totalOrdersDelivered * 100;
            if (refundRate > 10.0) {
                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("type", "HIGH_REFUND_RATE");
                alert.put("severity", "critical");
                alert.put("message", String.format("Refund rate is %.1f%% - exceeds 10%% threshold", refundRate));
                alert.put("count", totalRefunds);
                alerts.add(alert);
            }
        }
        long oos = productRepo.countByStock(0);
        if (oos > 0) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("type", "OUT_OF_STOCK");
            alert.put("severity", "warning");
            alert.put("message", oos + " products are out of stock");
            alert.put("count", oos);
            alerts.add(alert);
        }
        return alerts;
    }

    public Map<String, Object> generateReport(String type, String filter) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("type", type);
        report.put("filter", filter);
        report.put("generatedAt", LocalDateTime.now().toString());
        switch (type) {
            case "seller": {
                List<Map<String, Object>> sellerPerf = getSellerPerformance();
                if (filter != null && !filter.isEmpty()) {
                    sellerPerf = sellerPerf.stream()
                        .filter(s -> filter.equals(String.valueOf(s.get("sellerId"))))
                        .collect(Collectors.toList());
                }
                report.put("data", sellerPerf);
                report.put("label", "Seller Performance Report");
                break;
            }
            case "category": {
                List<Object[]> raw = orderRepo.findSalesGroupedByCategory();
                List<Map<String, Object>> catData = new ArrayList<>();
                for (Object[] row : raw) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("category", row[0]);
                    entry.put("revenue", row[1]);
                    catData.add(entry);
                }
                report.put("data", catData);
                report.put("label", "Category Sales Report");
                break;
            }
            case "warehouse": {
                List<Warehouse> warehouses = warehouseRepo.findByActiveTrue();
                List<Map<String, Object>> whData = new ArrayList<>();
                for (Warehouse w : warehouses) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", w.getId());
                    entry.put("name", w.getName());
                    entry.put("city", w.getCity());
                    entry.put("state", w.getState());
                    entry.put("capacity", w.getCapacity());
                    whData.add(entry);
                }
                report.put("data", whData);
                report.put("label", "Warehouse Summary Report");
                break;
            }
            case "month": {
                Map<String, Object> overview = getSalesOverview();
                report.put("data", overview);
                report.put("label", "Monthly Sales Report");
                break;
            }
            case "state": {
                List<Object[]> raw = orderRepo.findSalesByState(filter);
                List<Map<String, Object>> stateData = new ArrayList<>();
                for (Object[] row : raw) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("state", row[0] != null ? row[0] : "Unknown");
                    entry.put("revenue", row[1]);
                    stateData.add(entry);
                }
                report.put("data", stateData);
                report.put("label", "Sales by State Report");
                break;
            }
            default:
                report.put("data", Collections.emptyList());
                report.put("label", "Unknown Report Type");
        }
        return report;
    }
}
