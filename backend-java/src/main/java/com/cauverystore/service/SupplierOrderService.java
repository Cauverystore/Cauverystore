package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prepares what must be bought from the supplier for each customer order. It never buys or pays:
 * a person checks each line, pays on the supplier's site and records the supplier's order number.
 */
@Service
public class SupplierOrderService {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> CLOSED = Set.of("CANCELLED", "REFUNDED");
    private static final Set<String> PAST_BUYING = Set.of("CANCELLED", "REFUNDED", "SHIPPED", "DELIVERED", "COD_COLLECTED");
    static final int LOOK_BACK_DAYS = 14;

    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) { super(message); }
    }

    private final SupplierOrderTaskRepository tasks;
    private final OrderRepository orders;
    private final AgentJobRepository jobs;
    private final UserRepository users;
    private final NotificationService notifications;
    private final EmailService email;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${agents.watch.alert-email:support@cauverystore.in}")
    String alertEmail = "";

    public SupplierOrderService(SupplierOrderTaskRepository tasks, OrderRepository orders, AgentJobRepository jobs,
                                UserRepository users, NotificationService notifications, EmailService email,
                                AuditService audit, PlatformTransactionManager transactions) {
        this.tasks = tasks;
        this.orders = orders;
        this.jobs = jobs;
        this.users = users;
        this.notifications = notifications;
        this.email = email;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactions);
    }

    /** Should this order be bought from the supplier now? Paid online, or cash on delivery. */
    public static boolean readyToBuy(boolean paid, String paymentMethod, String status) {
        String s = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (PAST_BUYING.contains(s)) return false;
        return paid || (paymentMethod != null && paymentMethod.toUpperCase(Locale.ROOT).contains("COD"));
    }

    @Scheduled(initialDelayString = "${agents.orders.first-check-ms:60000}", fixedDelayString = "${agents.orders.check-ms:300000}")
    public void scheduled() {
        try {
            prepare();
        } catch (Exception e) {
            log.warn("Could not prepare supplier orders: {}", e.getMessage());
        }
    }

    /** Adds new lines and reacts to cancelled orders. Only one run at a time. */
    public Map<String, Object> prepare() {
        if (!running.compareAndSet(false, true)) return Map.of("busy", true);
        try {
            int[] counts = tx.execute(status -> run());
            if (counts[0] > 0) alert("New supplier order to place", counts[0] + " item(s) need ordering from the supplier.");
            if (counts[2] > 0) alert("Cancelled order already bought", counts[2] + " item(s) were bought from the supplier, then the customer cancelled.");
            return Map.of("added", counts[0], "cancelled", counts[1], "problems", counts[2]);
        } finally {
            running.set(false);
        }
    }

    private int[] run() {
        int added = 0, cancelled = 0, problems = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Order o : orders.findByCreatedAtBetween(now.minusDays(LOOK_BACK_DAYS), now)) {
            if (!readyToBuy(o.isPaid(), o.getPaymentMethod(), o.getStatus())) continue;
            for (OrderItem item : o.getItems()) {
                Product p = item.getProduct();
                if (p == null || tasks.existsByOrderItemId(item.getId())) continue;
                Optional<AgentJob> job = jobs.findFirstByProductIdAndStatusOrderByIdDesc(p.getId(), AgentJob.USED);
                if (job.isEmpty()) continue;
                SupplierOrderTask t = new SupplierOrderTask();
                t.setOrderId(o.getId());
                t.setOrderItemId(item.getId());
                t.setProductId(p.getId());
                t.setProductName(p.getName());
                t.setSku(p.getSku());
                t.setSupplierUrl(job.get().getSourceUrl());
                t.setQuantity(item.getQuantity());
                t.setSalePrice(item.getPrice());
                tasks.save(t);
                added++;
            }
        }
        for (SupplierOrderTask t : tasks.findByStatusInOrderByIdDesc(List.of(SupplierOrderTask.TO_ORDER, SupplierOrderTask.ORDERED))) {
            String status = orders.findById(t.getOrderId()).map(Order::getStatus).orElse("CANCELLED");
            if (!CLOSED.contains(status.toUpperCase(Locale.ROOT))) continue;
            if (SupplierOrderTask.TO_ORDER.equals(t.getStatus())) {
                t.setStatus(SupplierOrderTask.CANCELLED);
                cancelled++;
            } else {
                t.setStatus(SupplierOrderTask.PROBLEM);
                problems++;
            }
            tasks.save(t);
        }
        return new int[] {added, cancelled, problems};
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String view) {
        List<String> statuses = switch (view == null ? "" : view) {
            case "done" -> List.of(SupplierOrderTask.ORDERED, SupplierOrderTask.CANCELLED);
            case "problem" -> List.of(SupplierOrderTask.PROBLEM);
            default -> List.of(SupplierOrderTask.TO_ORDER);
        };
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SupplierOrderTask t : tasks.findByStatusInOrderByIdDesc(statuses)) rows.add(view(t));
        return Map.of("rows", rows, "counts", Map.of(
                "todo", tasks.countByStatus(SupplierOrderTask.TO_ORDER),
                "problem", tasks.countByStatus(SupplierOrderTask.PROBLEM)));
    }

    /** Records that a person placed and paid this on the supplier's site. */
    @Transactional
    public Map<String, Object> markOrdered(Long id, String ref, Double amount, String by) {
        SupplierOrderTask t = find(id);
        if (!SupplierOrderTask.TO_ORDER.equals(t.getStatus())) throw new BadRequest("This item is no longer waiting to be ordered.");
        String status = orders.findById(t.getOrderId()).map(Order::getStatus).orElse("CANCELLED");
        if (CLOSED.contains(status.toUpperCase(Locale.ROOT))) throw new BadRequest("The customer's order was cancelled. Do not buy this.");
        String clean = ref == null ? "" : ref.trim();
        if (clean.isEmpty() || clean.length() > 80) throw new BadRequest("Enter the supplier's order number.");
        if (amount == null || amount <= 0) throw new BadRequest("Enter the amount you paid the supplier.");
        t.setSupplierOrderRef(clean);
        t.setAmountPaid(amount);
        t.setOrderedBy(by);
        t.setOrderedAt(LocalDateTime.now());
        t.setStatus(SupplierOrderTask.ORDERED);
        tasks.save(t);
        audit.log(null, by, "SUPPLIER_ORDER_PLACED", "Order", t.getOrderId(), "Supplier order " + clean, null);
        return view(t);
    }

    /** A person has sorted out a bought item whose customer order was cancelled. */
    @Transactional
    public Map<String, Object> markHandled(Long id, String by) {
        SupplierOrderTask t = find(id);
        if (!SupplierOrderTask.PROBLEM.equals(t.getStatus())) throw new BadRequest("This item does not need sorting out.");
        t.setStatus(SupplierOrderTask.CANCELLED);
        tasks.save(t);
        audit.log(null, by, "SUPPLIER_ORDER_PROBLEM_HANDLED", "Order", t.getOrderId(), "Item " + t.getOrderItemId(), null);
        return view(t);
    }

    private SupplierOrderTask find(Long id) {
        return tasks.findById(id).orElseThrow(() -> new BadRequest("No such item."));
    }

    private Map<String, Object> view(SupplierOrderTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("orderId", t.getOrderId());
        m.put("productId", t.getProductId());
        m.put("productName", t.getProductName());
        m.put("sku", t.getSku());
        m.put("supplierUrl", t.getSupplierUrl());
        m.put("quantity", t.getQuantity());
        m.put("salePrice", t.getSalePrice());
        m.put("status", t.getStatus());
        m.put("supplierOrderRef", t.getSupplierOrderRef());
        m.put("amountPaid", t.getAmountPaid());
        m.put("orderedAt", t.getOrderedAt());
        jobs.findFirstByProductIdAndStatusOrderByIdDesc(t.getProductId(), AgentJob.USED).ifPresent(j -> {
            m.put("expectedCost", cost(j));
            m.put("supplierOutOfStock", AgentWatchService.HOLD_STOCK.equals(j.getWatchHold()));
        });
        if (SupplierOrderTask.TO_ORDER.equals(t.getStatus())) {
            orders.findById(t.getOrderId()).ifPresent(o -> {
                m.put("payment", o.isPaid() ? "Paid online" : "Cash on delivery");
                m.put("shipTo", shipTo(o));
            });
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Double cost(AgentJob j) {
        if (j.getWatchCost() != null) return j.getWatchCost();
        if (j.getSupplierCost() != null) return j.getSupplierCost();
        try {
            Object c = JSON.readValue(j.getDraftJson(), Map.class).get("supplierCost");
            return c instanceof Number n ? n.doubleValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Where the supplier must deliver: the consignee on a bill-to/ship-to order, else the address. */
    static String shipTo(Order o) {
        if (Boolean.TRUE.equals(o.getBillToShipTo()) && o.getConsigneeName() != null && !o.getConsigneeName().isBlank()) {
            return o.getConsigneeName().trim() + "\n" + (o.getConsigneeAddress() == null ? "" : o.getConsigneeAddress().trim());
        }
        Address a = o.getAddress();
        if (a == null) return "";
        List<String> lines = new ArrayList<>();
        add(lines, a.getFullName());
        add(lines, a.getLine1());
        add(lines, a.getLine2());
        add(lines, a.getStreet());
        add(lines, a.getLandmark());
        add(lines, String.join(", ", Arrays.stream(new String[] {a.getCity(), a.getState(), a.getPincode()})
                .filter(s -> s != null && !s.isBlank()).map(String::trim).toList()));
        if (a.getPhone() != null && !a.getPhone().isBlank()) lines.add("Phone: " + a.getPhone().trim());
        return String.join("\n", lines);
    }

    private static void add(List<String> lines, String s) {
        if (s != null && !s.isBlank()) lines.add(s.trim());
    }

    private void alert(String title, String message) {
        try {
            for (Role role : List.of(Role.ADMIN, Role.SUPER_ADMIN)) {
                for (User u : users.findByRole(role)) notifications.createNotification(u.getId(), "SUPPLIER_ORDER", title, message);
            }
            if (alertEmail != null && !alertEmail.isBlank()) email.sendSupplierOrderAlert(alertEmail, title, message);
        } catch (Exception e) {
            log.warn("Could not send the supplier order alert: {}", e.getMessage());
        }
    }
}
