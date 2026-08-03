package com.cauverystore.service;

import com.cauverystore.config.JwtUtil;
import com.cauverystore.dto.AdminDashboardResponse;
import com.cauverystore.dto.InvoiceResponse;
import com.cauverystore.dto.OrderTimelineResponse;
import com.cauverystore.entities.*;
import com.cauverystore.exception.OrderNotFoundException;
import com.cauverystore.exception.UserNotFoundException;
import com.cauverystore.repository.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final CartRepository cartRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final InventoryRepository inventoryRepo;
    private final CartService cartService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    private final RefundRepository refundRepo;
    private final AddressRepository addressRepo;
    private final JwtUtil jwtUtil;
    private final InvoicePdfService invoicePdfService;
    private final AuditService auditService;
    private final GstInvoiceService gstInvoiceService;
    private final GstConfigurationRepository gstConfigRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final ProductService productService;
    private final CouponService couponService;
    private final ReturnRequestRepository returnRequestRepo;
    private final PaymentService paymentService;

    public OrderService(OrderRepository orderRepo, OrderItemRepository orderItemRepo,
                        CartRepository cartRepo, ProductRepository productRepo,
                        UserRepository userRepo, InventoryRepository inventoryRepo,
                        CartService cartService, InventoryService inventoryService,
                        NotificationService notificationService, RefundRepository refundRepo,
                        AddressRepository addressRepo, JwtUtil jwtUtil,
                        InvoicePdfService invoicePdfService,
                        AuditService auditService,
                        GstInvoiceService gstInvoiceService,
                        GstConfigurationRepository gstConfigRepo,
                        SellerRegistrationRepository sellerRegRepo,
                        ProductService productService,
                        CouponService couponService,
                        ReturnRequestRepository returnRequestRepo,
                        PaymentService paymentService) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.cartRepo = cartRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.inventoryRepo = inventoryRepo;
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.notificationService = notificationService;
        this.refundRepo = refundRepo;
        this.addressRepo = addressRepo;
        this.jwtUtil = jwtUtil;
        this.invoicePdfService = invoicePdfService;
        this.auditService = auditService;
        this.gstInvoiceService = gstInvoiceService;
        this.gstConfigRepo = gstConfigRepo;
        this.sellerRegRepo = sellerRegRepo;
        this.productService = productService;
        this.couponService = couponService;
        this.returnRequestRepo = returnRequestRepo;
        this.paymentService = paymentService;
    }

    private User extractUserFromHeader(String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String email = jwtUtil.getEmailFromToken(token);
        User user = userRepo.findByEmail(email);
        if (user == null) throw new RuntimeException("User not found");
        return user;
    }

    public Order placeOrder(String username, Address address, String paymentMethod) {
        return placeOrder(username, address, paymentMethod, null);
    }

    public Order placeOrder(String username, Address address, String paymentMethod, String promoCode) {
        User user = userRepo.findByUsername(username);
        if (user == null) {
            throw new UserNotFoundException("User not found: " + username);
        }

        address.setUser(user);
        Address savedAddress = addressRepo.save(address);

        Cart cart = cartService.getCart(user);
        List<CartItem> activeItems = cart.getItems().stream()
                .filter(item -> !item.isSavedForLater())
                .collect(Collectors.toList());

        if (activeItems.isEmpty()) {
            throw new RuntimeException("Cart has no active items");
        }

        for (CartItem cartItem : activeItems) {
            Product product = cartItem.getProduct();
            int qty = cartItem.getQuantity();

            Inventory inventory = inventoryRepo.findByProduct_Id(product.getId());
            if (inventory != null) {
                if (inventory.getStock() < qty) {
                    throw new RuntimeException("Insufficient stock for " + product.getName());
                }
            } else {
                if (product.getStock() == null || product.getStock() < qty) {
                    throw new RuntimeException("Insufficient stock for " + product.getName());
                }
            }
        }

        Order order = new Order();
        order.setUser(user);
        order.setAddress(savedAddress);
        order.setStatus("PLACED");
        order.setPaymentMethod(paymentMethod != null ? paymentMethod : "COD");

        double subTotal = 0;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : activeItems) {
            Product product = cartItem.getProduct();
            int qty = cartItem.getQuantity();
            // A variant's own price overrides the base product price when set; otherwise charge
            // whatever active discount currently applies. This must match what the cart/checkout
            // UI actually showed the customer, or the charged amount silently diverges from it.
            double price = (cartItem.getVariant() != null && cartItem.getVariant().getPrice() != null)
                    ? cartItem.getVariant().getPrice()
                    : productService.getDiscountedPriceDouble(product.getId());

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(qty);
            orderItem.setPrice(price);
            if (cartItem.getVariant() != null) {
                orderItem.setVariant(cartItem.getVariant());
            }
            orderItems.add(orderItem);
            subTotal += price * qty;

            inventoryService.deductStock(product, qty);
        }

        // Never trust a client-computed discount for what actually gets charged - re-validate
        // the coupon against the server-computed subtotal and recompute the discount here.
        com.cauverystore.entities.Coupon appliedCoupon = null;
        double discount = 0;
        if (promoCode != null && !promoCode.isBlank()) {
            appliedCoupon = couponService.validate(promoCode, subTotal);
            discount = couponService.computeDiscount(appliedCoupon, subTotal);
        }

        double deliveryCharge = (appliedCoupon != null && appliedCoupon.isFreeShipping()) || subTotal >= 500 ? 0 : 40;
        double taxableAmount = Math.max(subTotal - discount, 0);
        double tax = Math.round(taxableAmount * 0.12 * 100.0) / 100.0;
        double totalAmount = taxableAmount + tax + deliveryCharge;

        order.setItems(orderItems);
        order.setSubTotal(subTotal);
        order.setTax(tax);
        order.setDeliveryCharge(deliveryCharge);
        order.setTotalAmount(totalAmount);

        if (appliedCoupon != null) {
            couponService.recordUsage(appliedCoupon);
        }

        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setStatus("PLACED");
        timeline.setMessage("Order placed successfully");
        timeline.setTimestamp(LocalDateTime.now());
        order.setTimeline(new ArrayList<>(List.of(timeline)));

        Order savedOrder = orderRepo.save(order);

        for (OrderItem item : orderItems) {
            item.setOrder(savedOrder);
            orderItemRepo.save(item);
        }

        cart.getItems().removeIf(item -> !item.isSavedForLater());
        cart.setTotalItems(0);
        cart.setTotalPrice(0.0);
        cartRepo.save(cart);

        // Set sellerId from first product's seller
        if (!activeItems.isEmpty()) {
            Long firstSellerId = activeItems.get(0).getProduct().getSellerId();
            if (firstSellerId != null) {
                savedOrder.setSellerId(firstSellerId);
                orderRepo.save(savedOrder);
            }
        }

        // Auto-generate GST invoice if possible
        if (savedOrder.getSellerId() != null) {
            try {
                String sellerGstin = null;
                Optional<GstConfiguration> gstConfig = gstConfigRepo.findBySellerId(savedOrder.getSellerId());
                if (gstConfig.isPresent()) {
                    sellerGstin = gstConfig.get().getGstin();
                } else {
                    Optional<SellerRegistration> sellerReg = sellerRegRepo.findByUserId(savedOrder.getSellerId());
                    if (sellerReg.isPresent() && sellerReg.get().getGstin() != null && !sellerReg.get().getGstin().isBlank()) {
                        sellerGstin = sellerReg.get().getGstin();
                    }
                }
                if (sellerGstin != null) {
                    gstInvoiceService.generateInvoiceFromOrder(savedOrder.getId(), savedOrder.getSellerId(),
                            sellerGstin, null);
                }
            } catch (Exception e) {
                // Auto-generation is best-effort; seller can manually generate later
                System.err.println("Auto GST invoice generation skipped for order " + savedOrder.getId() + ": " + e.getMessage());
            }
        }

        auditService.log(user.getId(), user.getEmail(), "ORDER_PLACED",
                "Order", savedOrder.getId(),
                "Order #" + savedOrder.getId() + " placed for " + totalAmount, null);

        notificationService.sendOrderPlaced(savedOrder, discount);
        notificationService.createNotification(user.getId(), "ORDER", "Order Placed",
                "Your order #" + savedOrder.getId() + " has been placed successfully.");

        return savedOrder;
    }

    public List<Order> getOrders(String username) {
        User user = userRepo.findByUsername(username);
        if (user == null) {
            throw new UserNotFoundException("User not found: " + username);
        }
        return orderRepo.findByUser(user);
    }

    public List<OrderItem> getOrderItems(Long orderId, String authHeader) {
        User user = extractUserFromHeader(authHeader);
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        verifyOrderOwnership(order, user.getId());
        return orderItemRepo.findByOrder_Id(orderId);
    }

    public Map<String, Object> getOrderDetail(Long orderId, String authHeader) {
        User user = extractUserFromHeader(authHeader);
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        verifyOrderOwnership(order, user.getId());

        Map<String, Object> m = new HashMap<>();
        m.put("id", order.getId());
        m.put("status", order.getStatus());
        m.put("paymentMethod", order.getPaymentMethod());
        m.put("paymentStatus", order.isPaid() ? "PAID" : "PENDING");
        m.put("totalAmount", order.getTotalAmount());
        m.put("subTotal", order.getSubTotal());
        m.put("tax", order.getTax());
        m.put("deliveryCharge", order.getDeliveryCharge());
        m.put("trackingNumber", order.getTrackingNumber());
        m.put("courier", order.getCourier());
        m.put("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);

        if (order.getAddress() != null) {
            Address addr = order.getAddress();
            Map<String, Object> shippingAddress = new HashMap<>();
            shippingAddress.put("name", addr.getFullName());
            shippingAddress.put("address", addr.getStreet());
            shippingAddress.put("city", addr.getCity());
            shippingAddress.put("state", addr.getState());
            shippingAddress.put("pincode", addr.getPincode());
            m.put("shippingAddress", shippingAddress);
        }

        return m;
    }

    public InvoiceResponse getInvoice(Long orderId, String authHeader) {
        User user = extractUserFromHeader(authHeader);
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        verifyOrderOwnership(order, user.getId());

        InvoiceResponse invoice = new InvoiceResponse();
        invoice.setOrderId(order.getId().toString());
        invoice.setCustomerName(order.getUser().getFullName());
        invoice.setCustomerEmail(order.getUser().getEmail());
        invoice.setPhone(order.getUser().getPhone());

        if (order.getAddress() != null) {
            Address addr = order.getAddress();
            invoice.setAddress(String.join(", ",
                    addr.getStreet() != null ? addr.getStreet() : "",
                    addr.getCity() != null ? addr.getCity() : "",
                    addr.getState() != null ? addr.getState() : "",
                    addr.getPincode() != null ? addr.getPincode() : ""));
        }

        invoice.setOrderDate(order.getCreatedAt() != null ? order.getCreatedAt().toString() : "");
        invoice.setStatus(order.getStatus());

        double subtotal = order.getItems().stream()
                .mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();
        double deliveryCharge = subtotal >= 500 ? 0 : 40;
        double tax = Math.round(subtotal * 0.12 * 100.0) / 100.0;

        invoice.setSubtotal(subtotal);
        invoice.setTax(tax);
        invoice.setDeliveryCharge(deliveryCharge);
        invoice.setTotalAmount(subtotal + tax + deliveryCharge);
        invoice.setPaymentMethod(order.getPaymentMethod());

        List<Map<String, Object>> items = order.getItems().stream().map(i -> {
            Map<String, Object> m = new HashMap<>();
            m.put("name", i.getProduct().getName());
            m.put("quantity", i.getQuantity());
            m.put("price", i.getPrice());
            m.put("subtotal", i.getPrice() * i.getQuantity());
            return m;
        }).collect(Collectors.toList());
        invoice.setItems(items);

        return invoice;
    }

    public OrderTimelineResponse getOrderTimeline(Long orderId, String authHeader) {
        User user = extractUserFromHeader(authHeader);
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        verifyOrderOwnership(order, user.getId());

        OrderTimelineResponse response = new OrderTimelineResponse();
        response.setOrderId(order.getId().toString());
        response.setCurrentStatus(order.getStatus());

        List<Map<String, Object>> events = order.getTimeline().stream()
                .sorted(Comparator.comparing(OrderTimeline::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("status", t.getStatus());
                    m.put("message", t.getMessage());
                    m.put("timestamp", t.getTimestamp() != null ? t.getTimestamp().toString() : null);
                    return m;
                }).collect(Collectors.toList());
        response.setEvents(events);

        return response;
    }

    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepo.findAll(pageable);
    }

    public Page<Order> getAllOrders(Pageable pageable, String status) {
        if (status == null || status.isBlank()) {
            return orderRepo.findAll(pageable);
        }
        return orderRepo.findByStatus(status, pageable);
    }

    public List<Order> getAllOrders() {
        return orderRepo.findAll();
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, String status) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        List<String> progression = List.of("PLACED", "CONFIRMED", "PACKED", "SHIPPED", "DELIVERED");
        String current = order.getStatus();
        // CANCELLED/REFUNDED sit outside the normal progression list, so the check above would
        // otherwise be skipped entirely for them, letting a cancelled order be "progressed"
        // straight to SHIPPED/DELIVERED.
        if (("CANCELLED".equals(current) || "REFUNDED".equals(current)) && progression.contains(status)) {
            throw new RuntimeException("Cannot move from " + current + " to " + status);
        }
        if (progression.contains(current) && progression.contains(status)) {
            if (progression.indexOf(status) <= progression.indexOf(current)) {
                throw new RuntimeException("Cannot move from " + current + " to " + status);
            }
        }

        order.setStatus(status);

        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setStatus(status);
        timeline.setMessage("Order status updated to " + status);
        timeline.setTimestamp(LocalDateTime.now());
        order.getTimeline().add(timeline);

        Order saved = orderRepo.save(order);

        auditService.log(order.getUser().getId(), order.getUser().getEmail(),
                "ORDER_STATUS_" + status, "Order", orderId,
                "Order #" + orderId + " status updated to " + status, null);

        switch (status) {
            case "DELIVERED":
                notificationService.sendOrderDelivered(order.getUser().getEmail(), orderId.toString());
                try { gstInvoiceService.updateInvoiceStatusByOrderId(orderId, "DELIVERED"); } catch (Exception e) { System.err.println("Invoice status update skipped: " + e.getMessage()); }
                break;
            case "SHIPPED":
                notificationService.sendOrderShipped(order.getUser().getEmail(), orderId.toString());
                try { gstInvoiceService.updateInvoiceStatusByOrderId(orderId, "DISPATCHED"); } catch (Exception e) { System.err.println("Invoice status update skipped: " + e.getMessage()); }
                break;
            case "CANCELLED":
                notificationService.sendOrderCancelled(order, null, null);
                break;
        }

        notificationService.createNotification(order.getUser().getId(), "ORDER",
                "Order " + status,
                "Your order #" + orderId + " has been " + status.toLowerCase() + ".");

        return saved;
    }

    private static final Set<String> TERMINAL_ORDER_STATUSES = Set.of("CANCELLED", "REFUNDED", "DELIVERED");

    public Order cancelOrder(Long orderId) {
        return cancelOrder(orderId, null);
    }

    @Transactional
    public Order cancelOrder(Long orderId, String reason) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        if (TERMINAL_ORDER_STATUSES.contains(order.getStatus())) {
            throw new RuntimeException("Order cannot be cancelled - current status is " + order.getStatus());
        }
        boolean hadReason = reason != null && !reason.isBlank();

        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product != null) {
                inventoryService.restoreStock(product, item.getQuantity());
            }
        }

        order.setStatus("CANCELLED");

        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setStatus("CANCELLED");
        timeline.setMessage(hadReason ? "Order cancelled: " + reason : "Order cancelled");
        timeline.setTimestamp(LocalDateTime.now());
        order.getTimeline().add(timeline);

        // A prepaid order being cancelled needs its money back, not just its stock - this
        // mirrors refundOrder's own record-creation exactly so cancellation-triggered refunds
        // show up the same way a manually-issued one would.
        Refund refund = null;
        if (order.isPaid()) {
            refund = paymentService.processRefundForOrder(orderId, order.getTotalAmount(),
                    hadReason ? "Order cancelled: " + reason : "Order cancelled");
        }

        Order saved = orderRepo.save(order);
        notificationService.sendOrderCancelled(order, reason, refund);
        notificationService.createNotification(order.getUser().getId(), "ORDER",
                "Order Cancelled",
                "Your order #" + orderId + " has been cancelled successfully.");
        return saved;
    }

    @Transactional
    public Order refundOrder(Long orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        if (!order.isPaid()) {
            throw new RuntimeException("Cannot refund an order that was never paid");
        }
        if ("REFUNDED".equals(order.getStatus())) {
            throw new RuntimeException("Order has already been refunded");
        }
        order.setStatus("REFUNDED");

        paymentService.processRefundForOrder(orderId, order.getTotalAmount(), "Customer requested refund");

        return orderRepo.save(order);
    }

    public Order getOrderById(Long orderId) {
        return orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
    }

    @Transactional
    public Map<String, Object> placeOrder(String authHeader, Map<String, Object> body) {
        User user = extractUserFromHeader(authHeader);
        Address address = new Address();
        address.setFullName((String) body.getOrDefault("fullName", user.getFullName()));
        address.setPhone((String) body.getOrDefault("phone", user.getPhone()));
        address.setStreet((String) body.get("street"));
        address.setCity((String) body.get("city"));
        address.setState((String) body.get("state"));
        address.setPincode((String) body.get("pincode"));

        String paymentMethod = (String) body.getOrDefault("paymentMethod", "COD");
        String promoCode = (String) body.get("promoCode");
        Order order = placeOrder(user.getUsername(), address, paymentMethod, promoCode);
        Map<String, Object> result = new HashMap<>();
        result.put("id", order.getId());
        result.put("status", order.getStatus());
        result.put("totalAmount", order.getTotalAmount());
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getOrdersByHeader(String authHeader, String status) {
        User user = extractUserFromHeader(authHeader);
        List<Order> orders = getOrders(user.getUsername());
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            orders = orders.stream().filter(o -> status.equalsIgnoreCase(o.getStatus())).toList();
        }
        return orders.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("orderId", o.getId().toString());
            m.put("status", o.getStatus());
            m.put("totalAmount", o.getTotalAmount());
            m.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : null);
            m.put("paymentMethod", o.getPaymentMethod());

            List<Map<String, Object>> items = o.getItems().stream().map(item -> {
                Map<String, Object> im = new HashMap<>();
                im.put("id", item.getId());
                im.put("quantity", item.getQuantity());
                im.put("price", item.getPrice());
                Product product = item.getProduct();
                if (product != null) {
                    im.put("productId", product.getId());
                    im.put("productName", product.getName());
                    String imageUrl = "";
                    if (product.getImages() != null && !product.getImages().isEmpty()) {
                        imageUrl = product.getImages().get(0).getUrl();
                    }
                    im.put("productImage", imageUrl);
                } else {
                    im.put("productName", "Product is no longer available");
                }
                return im;
            }).collect(Collectors.toList());
            m.put("items", items);

            return m;
        }).toList();
    }

    public Resource generateInvoicePdf(Long orderId, String authHeader) {
        InvoiceResponse invoice = getInvoice(orderId, authHeader);
        byte[] pdfBytes = invoicePdfService.generatePdf(invoice);
        return new ByteArrayResource(pdfBytes);
    }

    private void verifyOrderOwnership(Order order, Long userId) {
        if (!order.getUser().getId().equals(userId)) {
            throw new RuntimeException("Order not found");
        }
    }

    // @Transactional here (not just on the cancelOrder(Long) it calls) matters: that inner
    // call is a same-class self-invocation, which bypasses Spring's proxy and silently ignores
    // its own @Transactional. Without an active transaction/session from this outer method,
    // the lazy-loaded order.getTimeline()/getItems() inside it fail once the session closes.
    public Map<String, Object> cancelOrder(String authHeader, Long orderId) {
        return cancelOrder(authHeader, orderId, null);
    }

    @Transactional
    public Map<String, Object> cancelOrder(String authHeader, Long orderId, String reason) {
        User user = extractUserFromHeader(authHeader);
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        verifyOrderOwnership(order, user.getId());
        // Customer self-service cancellation only - once an order has shipped it's out for
        // delivery and can't be unilaterally pulled back the way an unshipped one can. Admins
        // retain the ability to cancel a shipped order via adminCancelOrder for exceptions.
        if ("SHIPPED".equals(order.getStatus())) {
            throw new RuntimeException("This order has already been shipped and can no longer be cancelled");
        }
        Order o = cancelOrder(orderId, reason);
        boolean hadReason = reason != null && !reason.isBlank();
        auditService.log(user.getId(), user.getEmail(), "ORDER_CANCELLED",
                "Order", o.getId(),
                "Order #" + o.getId() + " cancelled by customer" + (hadReason ? ": " + reason : ""), null);
        return Map.of("id", o.getId(), "status", o.getStatus());
    }

    // Distinct from cancelOrder: a return only makes sense once an order was actually
    // delivered. It raises a ReturnRequest for admin review instead of unilaterally
    // reversing the order/stock the way cancellation does.
    @Transactional
    public Map<String, Object> createReturnRequest(String authHeader, Long orderId, String reason) {
        User user = extractUserFromHeader(authHeader);
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        verifyOrderOwnership(order, user.getId());
        if (!"DELIVERED".equals(order.getStatus())) {
            throw new RuntimeException("Only delivered orders can be returned");
        }
        if (reason == null || reason.isBlank()) {
            throw new RuntimeException("A reason is required to request a return");
        }

        ReturnRequest rr = new ReturnRequest();
        rr.setOrder(order);
        rr.setUser(user);
        rr.setReason(reason);
        rr.setStatus("PENDING");
        ReturnRequest saved = returnRequestRepo.save(rr);

        auditService.log(user.getId(), user.getEmail(), "RETURN_REQUESTED",
                "Order", orderId,
                "Return requested for order #" + orderId + ": " + reason, null);
        notificationService.createNotification(user.getId(), "ORDER",
                "Return Request Submitted",
                "Your return request for order #" + orderId + " has been submitted.");

        return Map.of("id", saved.getId(), "status", saved.getStatus());
    }

    @Transactional
    public Order markShipped(Long orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        if ("CANCELLED".equals(order.getStatus()) || "REFUNDED".equals(order.getStatus())) {
            throw new RuntimeException("Cannot ship an order that is " + order.getStatus());
        }
        order.setStatus("SHIPPED");
        order.setShippedAt(LocalDateTime.now());

        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setStatus("SHIPPED");
        timeline.setMessage("Order has been shipped");
        timeline.setTimestamp(LocalDateTime.now());
        order.getTimeline().add(timeline);

        Order saved = orderRepo.save(order);

        try {
            gstInvoiceService.updateInvoiceStatusByOrderId(orderId, "DISPATCHED");
        } catch (Exception e) {
            System.err.println("Invoice status update skipped for order " + orderId + ": " + e.getMessage());
        }

        auditService.log(order.getUser().getId(), order.getUser().getEmail(),
                "ORDER_SHIPPED", "Order", orderId,
                "Order #" + orderId + " has been shipped", null);

        notificationService.sendOrderShipped(order.getUser().getEmail(), orderId.toString());
        notificationService.createNotification(order.getUser().getId(), "ORDER",
                "Order Shipped", "Your order #" + orderId + " has been shipped.");
        return saved;
    }

    @Transactional
    public Order markDelivered(Long orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        if ("CANCELLED".equals(order.getStatus()) || "REFUNDED".equals(order.getStatus())) {
            throw new RuntimeException("Cannot deliver an order that is " + order.getStatus());
        }
        order.setStatus("DELIVERED");
        order.setDeliveredAt(LocalDateTime.now());

        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setStatus("DELIVERED");
        timeline.setMessage("Order has been delivered successfully");
        timeline.setTimestamp(LocalDateTime.now());
        order.getTimeline().add(timeline);

        Order saved = orderRepo.save(order);

        try {
            gstInvoiceService.updateInvoiceStatusByOrderId(orderId, "DELIVERED");
        } catch (Exception e) {
            System.err.println("Invoice status update skipped for order " + orderId + ": " + e.getMessage());
        }

        auditService.log(order.getUser().getId(), order.getUser().getEmail(),
                "ORDER_DELIVERED", "Order", orderId,
                "Order #" + orderId + " has been delivered", null);

        notificationService.sendOrderDelivered(order.getUser().getEmail(), orderId.toString());
        notificationService.createNotification(order.getUser().getId(), "ORDER",
                "Order Delivered", "Your order #" + orderId + " has been delivered.");
        return saved;
    }

    @Transactional
    public Order adminCancelOrder(Long orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        if (TERMINAL_ORDER_STATUSES.contains(order.getStatus())) {
            throw new RuntimeException("Order cannot be cancelled - current status is " + order.getStatus());
        }
        order.setStatus("CANCELLED");

        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setStatus("CANCELLED");
        timeline.setMessage("Order cancelled by admin");
        timeline.setTimestamp(LocalDateTime.now());
        order.getTimeline().add(timeline);

        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product != null) {
                inventoryService.restoreStock(product, item.getQuantity());
            }
        }

        Refund refund = null;
        if (order.isPaid()) {
            refund = paymentService.processRefundForOrder(orderId, order.getTotalAmount(), "Order cancelled by admin");
        }

        Order saved = orderRepo.save(order);

        auditService.log(order.getUser().getId(), order.getUser().getEmail(),
                "ORDER_ADMIN_CANCELLED", "Order", orderId,
                "Order #" + orderId + " cancelled by admin", null);

        notificationService.sendOrderCancelled(order, "Order cancelled by admin", refund);
        notificationService.createNotification(order.getUser().getId(), "ORDER",
                "Order Cancelled", "Your order #" + orderId + " has been cancelled by admin.");
        return saved;
    }

    @Transactional
    public Order assignCourier(Long orderId, String courier, String trackingNumber) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        order.setCourier(courier);
        if (trackingNumber != null && !trackingNumber.isEmpty()) {
            order.setTrackingNumber(trackingNumber);
        }

        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setStatus("COURIER_ASSIGNED");
        timeline.setMessage("Courier assigned: " + courier
                + (trackingNumber != null && !trackingNumber.isEmpty() ? " (Tracking: " + trackingNumber + ")" : ""));
        timeline.setTimestamp(LocalDateTime.now());
        order.getTimeline().add(timeline);

        return orderRepo.save(order);
    }

    public AdminDashboardResponse getAdminDashboard() {
        AdminDashboardResponse dashboard = new AdminDashboardResponse();

        List<Order> allOrders = orderRepo.findAll();
        dashboard.setTotalOrders(allOrders.size());

        double revenue = allOrders.stream()
                .filter(o -> !"CANCELLED".equals(o.getStatus()) && !"REFUNDED".equals(o.getStatus()))
                .mapToDouble(Order::getTotalAmount).sum();
        dashboard.setTotalRevenue(revenue);

        dashboard.setTotalProducts(productRepo.count());
        dashboard.setTotalUsers(userRepo.count());

        Map<String, Long> orderStatusCounts = allOrders.stream()
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));
        dashboard.setOrdersByStatus(orderStatusCounts);

        dashboard.setPendingOrders(orderStatusCounts.getOrDefault("PLACED", 0L)
                + orderStatusCounts.getOrDefault("PROCESSING", 0L));
        dashboard.setShippedOrders(orderStatusCounts.getOrDefault("SHIPPED", 0L));
        dashboard.setDeliveredOrders(orderStatusCounts.getOrDefault("DELIVERED", 0L));
        dashboard.setCancelledOrders(orderStatusCounts.getOrDefault("CANCELLED", 0L));

        Double totalRefund = refundRepo.getTotalRefundAmount();
        dashboard.setRefundAmount(totalRefund != null ? totalRefund : 0);
        Long totalRefundCount = refundRepo.getTotalRefundCount();
        dashboard.setRefundCount(totalRefundCount != null ? totalRefundCount : 0);

        List<Map<String, Object>> recentOrders = orderRepo.getRecentOrders().stream()
                .limit(10).map(o -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", o.getId());
                    m.put("customer", o.getUser().getFullName());
                    m.put("amount", o.getTotalAmount());
                    m.put("status", o.getStatus());
                    m.put("date", o.getCreatedAt() != null ? o.getCreatedAt().toString() : "");
                    return m;
                }).collect(Collectors.toList());
        dashboard.setRecentOrders(recentOrders);

        List<Map<String, Object>> topProducts = productRepo.findByActiveTrue().stream()
                .sorted((a, b) -> Long.compare(b.getViewCount() != null ? b.getViewCount() : 0,
                        a.getViewCount() != null ? a.getViewCount() : 0))
                .limit(5).map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getName());
                    m.put("price", p.getPrice());
                    m.put("views", p.getViewCount());
                    return m;
                }).collect(Collectors.toList());
        dashboard.setTopProducts(topProducts);

        return dashboard;
    }
}
