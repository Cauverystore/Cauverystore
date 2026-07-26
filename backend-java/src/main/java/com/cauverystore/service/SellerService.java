package com.cauverystore.service;

import com.cauverystore.entities.Order;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.ProductAnalytics;
import com.cauverystore.entities.ReturnRequest;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.entities.SellerStore;
import com.cauverystore.entities.User;
import com.cauverystore.repository.InventoryRepository;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.ProductAnalyticsRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.ProductReviewRepository;
import com.cauverystore.repository.ReturnRequestRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.repository.SellerStoreRepository;
import com.cauverystore.repository.StockMovementRepository;
import com.cauverystore.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SellerService {

    private final ProductRepository productRepo;
    private final OrderRepository orderRepo;
    private final UserRepository userRepo;
    private final ReturnRequestRepository returnRepo;
    private final ProductAnalyticsRepository analyticsRepo;
    private final ProductReviewRepository reviewRepo;
    private final StockMovementRepository stockMovementRepo;
    private final InventoryRepository inventoryRepo;
    private final SellerStoreRepository storeRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final AuditService auditService;
    private final ProductService productService;
    private final GstInvoiceService gstInvoiceService;

    public SellerService(ProductRepository productRepo, OrderRepository orderRepo, UserRepository userRepo, ReturnRequestRepository returnRepo, ProductAnalyticsRepository analyticsRepo, ProductReviewRepository reviewRepo, StockMovementRepository stockMovementRepo, InventoryRepository inventoryRepo, SellerStoreRepository storeRepo, SellerRegistrationRepository sellerRegRepo, AuditService auditService, ProductService productService, GstInvoiceService gstInvoiceService) {
        this.productRepo = productRepo;
        this.orderRepo = orderRepo;
        this.userRepo = userRepo;
        this.returnRepo = returnRepo;
        this.analyticsRepo = analyticsRepo;
        this.reviewRepo = reviewRepo;
        this.stockMovementRepo = stockMovementRepo;
        this.inventoryRepo = inventoryRepo;
        this.storeRepo = storeRepo;
        this.sellerRegRepo = sellerRegRepo;
        this.auditService = auditService;
        this.productService = productService;
        this.gstInvoiceService = gstInvoiceService;
    }

    public Map<String, Object> getDashboard(Long sellerId) {
        List<Product> myProducts = productRepo.findAll().stream()
            .filter(p -> sellerId.equals(p.getSellerId())).toList();
        long total = myProducts.size();
        long active = myProducts.stream().filter(Product::isActive).count();
        long draft = myProducts.stream().filter(p -> "draft".equalsIgnoreCase(p.getProductStatus())).count();
        long outOfStock = myProducts.stream().filter(p -> p.getStock() != null && p.getStock() <= 0).count();
        long lowStock = myProducts.stream().filter(p -> p.getStock() != null && p.getStock() > 0 && p.getStock() <= 10).count();
        List<Order> sellerOrders = orderRepo.findAll().stream()
            .filter(o -> sellerId.equals(o.getSellerId())).toList();
        long inProgress = sellerOrders.stream().filter(o -> Arrays.asList("PLACED", "PROCESSING", "SHIPPED").contains(o.getStatus())).count();
        long returned = sellerOrders.stream().filter(o -> "RETURNED".equals(o.getStatus())).count();
        long refunded = sellerOrders.stream().filter(o -> "REFUNDED".equals(o.getStatus())).count();
        List<ReturnRequest> returns = returnRepo.findAll().stream()
            .filter(r -> r.getProduct() != null && sellerId.equals(r.getProduct().getSellerId())).toList();
        long returnCount = returns.size();
        long replacements = returns.stream().filter(r -> r.getReplacementOrderId() != null).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalProducts", total);
        result.put("activeProducts", active);
        result.put("draftProducts", draft);
        result.put("outOfStockProducts", outOfStock);
        result.put("lowStockProducts", lowStock);
        result.put("ordersInProgress", inProgress);
        result.put("totalReturns", returnCount);
        result.put("totalRefunds", refunded);
        result.put("replacements", replacements);
        result.put("lowStockList", myProducts.stream().filter(p -> p.getStock() != null && p.getStock() <= 10).map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("stock", p.getStock());
            return m;
        }).toList());
        result.put("inProgressOrders", sellerOrders.stream().filter(o -> Arrays.asList("PLACED", "PROCESSING", "SHIPPED").contains(o.getStatus())).limit(10).map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("customerName", o.getUser() != null ? o.getUser().getFullName() : "N/A");
            m.put("totalAmount", o.getTotalAmount());
            m.put("status", o.getStatus());
            return m;
        }).toList());
        Optional<User> seller = userRepo.findById(sellerId);
        result.put("sellerName", seller.map(User::getFullName).orElse("Seller"));
        result.put("storeName", "");
        Optional<SellerStore> store = storeRepo.findBySellerId(sellerId);
        store.ifPresent(s -> result.put("storeName", s.getStoreName()));

        Optional<SellerRegistration> reg = sellerRegRepo.findByUserId(sellerId);
        if (reg.isPresent()) {
            SellerRegistration r = reg.get();
            Map<String, Object> gst = new LinkedHashMap<>();
            gst.put("gstin", r.getGstin() != null ? r.getGstin() : "");
            gst.put("panNumber", r.getPanNumber() != null ? r.getPanNumber() : "");
            gst.put("businessName", r.getBusinessName() != null ? r.getBusinessName() : "");
            gst.put("businessAddress", r.getBusinessAddress() != null ? r.getBusinessAddress() : "");
            gst.put("licenses", r.getLicenses() != null ? r.getLicenses() : "");
            gst.put("status", r.getStatus() != null ? r.getStatus() : "");
            result.put("gstInfo", gst);
        } else {
            result.put("gstInfo", null);
        }
        return result;
    }

    public Map<String, Object> getProducts(Long sellerId, int page, int size, String category, String stockStatus, String dateAdded) {
        List<Product> all = productRepo.findAll().stream()
            .filter(p -> sellerId.equals(p.getSellerId())).collect(Collectors.toList());
        if (category != null && !category.isEmpty()) {
            all = all.stream().filter(p -> p.getCategory() != null && category.equalsIgnoreCase(p.getCategory().getName())).toList();
        }
        if ("in_stock".equalsIgnoreCase(stockStatus)) {
            all = all.stream().filter(p -> p.getStock() != null && p.getStock() > 0).toList();
        } else if ("out_of_stock".equalsIgnoreCase(stockStatus)) {
            all = all.stream().filter(p -> p.getStock() == null || p.getStock() <= 0).toList();
        } else if ("low_stock".equalsIgnoreCase(stockStatus)) {
            all = all.stream().filter(p -> p.getStock() != null && p.getStock() > 0 && p.getStock() <= 10).toList();
        } else if ("active".equalsIgnoreCase(stockStatus)) {
            all = all.stream().filter(Product::isActive).toList();
        } else if ("draft".equalsIgnoreCase(stockStatus)) {
            all = all.stream().filter(p -> "draft".equalsIgnoreCase(p.getProductStatus())).toList();
        }
        if ("oldest".equalsIgnoreCase(dateAdded)) {
            Collections.reverse(all);
        }
        int start = page * size;
        int end = Math.min(start + size, all.size());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", start < all.size() ? all.subList(start, end) : List.of());
        resp.put("totalPages", (int) Math.ceil((double) all.size() / size));
        resp.put("totalElements", all.size());
        resp.put("page", page);
        resp.put("size", size);
        return resp;
    }

    public List<Order> getOrders(Long sellerId) {
        return orderRepo.findAll().stream()
            .filter(o -> sellerId.equals(o.getSellerId()))
            .collect(Collectors.toList());
    }

    public Order updateOrderStatus(Long orderId, Long sellerId, String newStatus) {
        Order order = orderRepo.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
        if (!sellerId.equals(order.getSellerId())) throw new RuntimeException("Not your order");
        order.setStatus(newStatus);
        Order saved = orderRepo.save(order);
        if ("SHIPPED".equals(newStatus) || "DELIVERED".equals(newStatus)) {
            try {
                gstInvoiceService.updateInvoiceStatusByOrderId(orderId, newStatus.equals("SHIPPED") ? "DISPATCHED" : "DELIVERED");
            } catch (Exception e) {
                System.err.println("Invoice status update failed for order " + orderId + ": " + e.getMessage());
            }
        }
        auditService.log(null, "seller:" + sellerId, "ORDER_STATUS_UPDATE", "Order", orderId, "Status changed to " + newStatus, null);
        return saved;
    }

    public List<ReturnRequest> getReturns(Long sellerId) {
        return returnRepo.findAll().stream()
            .filter(r -> r.getProduct() != null && sellerId.equals(r.getProduct().getSellerId()))
            .collect(Collectors.toList());
    }

    public ReturnRequest updateReturnStatus(Long returnId, Long sellerId, String newStatus, String adminNote) {
        ReturnRequest req = returnRepo.findById(returnId).orElseThrow(() -> new RuntimeException("Return not found"));
        if (req.getProduct() != null && !sellerId.equals(req.getProduct().getSellerId())) throw new RuntimeException("Not your return");
        req.setStatus(newStatus);
        ReturnRequest saved = returnRepo.save(req);
        auditService.log(null, "seller:" + sellerId, "RETURN_STATUS_UPDATE", "ReturnRequest", returnId, "Status changed to " + newStatus, null);
        return saved;
    }

    public Map<String, Object> getAnalytics(Long sellerId) {
        List<Product> myProducts = productRepo.findAll().stream()
            .filter(p -> sellerId.equals(p.getSellerId())).toList();
        List<Long> productIds = myProducts.stream().map(Product::getId).toList();
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        List<ProductAnalytics> allAnalytics = analyticsRepo.findByDateBetweenOrderByDateDesc(thirtyDaysAgo, LocalDate.now());
        List<ProductAnalytics> myAnalytics = allAnalytics.stream()
            .filter(a -> productIds.contains(a.getProduct().getId())).toList();

        long totalViews = 0, totalAddToCart = 0, totalWishlist = 0, totalOrders = 0;
        double totalRevenue = 0;
        for (ProductAnalytics pa : myAnalytics) {
            totalViews += pa.getViews() != null ? pa.getViews() : 0;
            totalAddToCart += pa.getAddToCart() != null ? pa.getAddToCart() : 0;
            totalWishlist += pa.getWishlistAdds() != null ? pa.getWishlistAdds() : 0;
            totalOrders += pa.getOrders() != null ? pa.getOrders() : 0;
            totalRevenue += pa.getRevenue() != null ? pa.getRevenue() : 0;
        }
        double conversionRate = totalViews > 0 ? (double) totalOrders / totalViews * 100 : 0;
        List<Order> sellerOrders = orderRepo.findAll().stream()
            .filter(o -> sellerId.equals(o.getSellerId())).toList();
        long totalDelivered = sellerOrders.stream().filter(o -> "DELIVERED".equals(o.getStatus())).count();
        List<ReturnRequest> returns = returnRepo.findAll().stream()
            .filter(r -> r.getProduct() != null && sellerId.equals(r.getProduct().getSellerId())).toList();
        double returnRate = totalDelivered > 0 ? (double) returns.size() / totalDelivered * 100 : 0;

        List<Map<String, Object>> productPerf = new ArrayList<>();
        for (Product p : myProducts) {
            Long pid = p.getId();
            List<ProductAnalytics> paList = myAnalytics.stream().filter(a -> a.getProduct().getId().equals(pid)).toList();
            long v = 0, ac = 0, wl = 0, ord = 0;
            double rev = 0;
            for (ProductAnalytics pa : paList) {
                v += pa.getViews() != null ? pa.getViews() : 0;
                ac += pa.getAddToCart() != null ? pa.getAddToCart() : 0;
                wl += pa.getWishlistAdds() != null ? pa.getWishlistAdds() : 0;
                ord += pa.getOrders() != null ? pa.getOrders() : 0;
                rev += pa.getRevenue() != null ? pa.getRevenue() : 0;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", pid);
            entry.put("name", p.getName());
            entry.put("sku", p.getSku());
            entry.put("price", p.getPrice());
            entry.put("stock", p.getStock());
            entry.put("views", v);
            entry.put("addToCart", ac);
            entry.put("wishlistAdds", wl);
            entry.put("orders", ord);
            entry.put("revenue", rev);
            entry.put("conversionRate", v > 0 ? Math.round((double) ord / v * 10000) / 100.0 : 0);
            productPerf.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalViews", totalViews);
        result.put("totalAddToCart", totalAddToCart);
        result.put("totalWishlist", totalWishlist);
        result.put("totalOrders", totalOrders);
        result.put("totalRevenue", totalRevenue);
        result.put("conversionRate", Math.round(conversionRate * 100.0) / 100.0);
        result.put("returnRate", Math.round(returnRate * 100.0) / 100.0);
        result.put("productPerformance", productPerf);
        result.put("periodDays", 30);
        return result;
    }

    public Map<String, Object> getStoreProfile(Long sellerId) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("sellerId", sellerId);
        Optional<SellerStore> store = storeRepo.findBySellerId(sellerId);
        if (store.isPresent()) {
            SellerStore s = store.get();
            profile.put("storeName", s.getStoreName());
            profile.put("storeSlug", s.getStoreSlug());
            profile.put("description", s.getDescription());
            profile.put("logoUrl", s.getLogoUrl());
            profile.put("bannerUrl", s.getBannerUrl());
            profile.put("address", s.getAddress());
            profile.put("contactPhone", s.getContactPhone());
            profile.put("contactEmail", s.getContactEmail());
            profile.put("returnPolicy", s.getReturnPolicy());
            profile.put("shippingPolicy", s.getShippingPolicy());
            profile.put("status", s.getStatus());
        } else {
            profile.put("storeName", "");
            profile.put("storeSlug", "");
            profile.put("description", "");
            profile.put("logoUrl", "");
            profile.put("bannerUrl", "");
            profile.put("address", "");
            profile.put("contactPhone", "");
            profile.put("contactEmail", "");
            profile.put("returnPolicy", "");
            profile.put("shippingPolicy", "");
            profile.put("status", "PENDING");
        }
        Optional<User> seller = userRepo.findById(sellerId);
        seller.ifPresent(u -> {
            profile.put("email", u.getEmail());
            profile.put("fullName", u.getFullName());
            profile.put("phone", u.getPhone());
        });
        return profile;
    }

    public Map<String, Object> updateStoreProfile(Long sellerId, Map<String, Object> body) {
        SellerStore store = storeRepo.findBySellerId(sellerId).orElseGet(() -> {
            SellerStore ns = new SellerStore();
            User seller = userRepo.findById(sellerId).orElseThrow(() -> new RuntimeException("Seller not found"));
            ns.setSeller(seller);
            return ns;
        });
        if (body.containsKey("storeName")) store.setStoreName((String) body.get("storeName"));
        if (body.containsKey("storeSlug")) store.setStoreSlug((String) body.get("storeSlug"));
        if (body.containsKey("description")) store.setDescription((String) body.get("description"));
        if (body.containsKey("logoUrl")) store.setLogoUrl((String) body.get("logoUrl"));
        if (body.containsKey("bannerUrl")) store.setBannerUrl((String) body.get("bannerUrl"));
        if (body.containsKey("address")) store.setAddress((String) body.get("address"));
        if (body.containsKey("contactPhone")) store.setContactPhone((String) body.get("contactPhone"));
        if (body.containsKey("contactEmail")) store.setContactEmail((String) body.get("contactEmail"));
        if (body.containsKey("returnPolicy")) store.setReturnPolicy((String) body.get("returnPolicy"));
        if (body.containsKey("shippingPolicy")) store.setShippingPolicy((String) body.get("shippingPolicy"));
        SellerStore saved = storeRepo.save(store);
        auditService.log(sellerId, "seller:" + sellerId, "STORE_UPDATE", "SellerStore", saved.getId(), "Store profile updated", null);
        return getStoreProfile(sellerId);
    }

    public List<Map<String, Object>> getNotifications(Long sellerId) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        List<Product> myProducts = productRepo.findAll().stream()
            .filter(p -> sellerId.equals(p.getSellerId())).toList();
        long pendingApproval = myProducts.stream().filter(p -> "PENDING".equalsIgnoreCase(p.getApprovalStatus())).count();
        if (pendingApproval > 0) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("type", "PENDING_APPROVAL"); a.put("severity", "warning");
            a.put("message", pendingApproval + " products pending admin approval"); a.put("count", pendingApproval);
            alerts.add(a);
        }
        long lowStock = myProducts.stream().filter(p -> p.getStock() != null && p.getStock() > 0 && p.getStock() <= 10).count();
        if (lowStock > 0) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("type", "LOW_STOCK"); a.put("severity", "warning");
            a.put("message", lowStock + " products are running low on stock"); a.put("count", lowStock);
            alerts.add(a);
        }
        long outOfStock = myProducts.stream().filter(p -> p.getStock() == null || p.getStock() <= 0).count();
        if (outOfStock > 0) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("type", "OUT_OF_STOCK"); a.put("severity", "critical");
            a.put("message", outOfStock + " products are out of stock"); a.put("count", outOfStock);
            alerts.add(a);
        }
        List<Order> sellerOrders = orderRepo.findAll().stream()
            .filter(o -> sellerId.equals(o.getSellerId())).toList();
        long newOrders = sellerOrders.stream().filter(o -> "PLACED".equals(o.getStatus())).count();
        if (newOrders > 0) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("type", "NEW_ORDERS"); a.put("severity", "info");
            a.put("message", newOrders + " new orders need processing"); a.put("count", newOrders);
            alerts.add(a);
        }
        return alerts;
    }

    public Map<String, Object> getProductDetail(Long productId, Long sellerId) {
        Product p = productRepo.findById(productId).orElseThrow(() -> new RuntimeException("Product not found"));
        if (!sellerId.equals(p.getSellerId())) throw new RuntimeException("Not your product");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("product", p);
        Optional<ProductAnalytics> latest = analyticsRepo.findTopByProductIdOrderByDateDesc(productId);
        latest.ifPresent(pa -> {
            detail.put("views", pa.getViews());
            detail.put("addToCart", pa.getAddToCart());
            detail.put("orders", pa.getOrders());
            detail.put("revenue", pa.getRevenue());
        });
        return detail;
    }
}
