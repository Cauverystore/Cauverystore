package com.cauverystore.service;

import com.cauverystore.entities.AgentJob;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.repository.AgentJobRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Keeps supplier-sourced products in step with the supplier. The agent only reports what the
 * supplier shows; every rule lives here:
 * out of stock = hide, back in stock = show again, a small cost change = reprice by the store's
 * rule, anything bigger or unusual = hide and ask a person.
 *
 * With agents.watch.live=false (the default) it changes nothing and only records what it would do.
 */
@Service
public class AgentWatchService {

    private static final Logger log = LoggerFactory.getLogger(AgentWatchService.class);
    static final double MAX_CHANGE = 0.15;
    public static final String HOLD_STOCK = "OUT_OF_STOCK";
    public static final String HOLD_REVIEW = "NEEDS_REVIEW";

    private final AgentJobRepository jobs;
    private final ProductRepository products;
    private final ProductService productService;
    private final UserRepository users;
    private final NotificationService notifications;
    private final EmailService email;
    private final AuditService audit;

    @Value("${agents.watch.live:false}")
    private boolean live;

    @Value("${agents.watch.alert-email:support@cauverystore.in}")
    private String alertEmail;

    public AgentWatchService(AgentJobRepository jobs, ProductRepository products, ProductService productService,
                             UserRepository users, NotificationService notifications, EmailService email,
                             AuditService audit) {
        this.jobs = jobs;
        this.products = products;
        this.productService = productService;
        this.users = users;
        this.notifications = notifications;
        this.email = email;
        this.audit = audit;
    }

    public record Facts(boolean active, String hold, Double baseline, double price, Double mrp,
                        boolean specialPricing, boolean available, Double cost) {}

    /** action: NONE, BASELINE, HIDE, RELIST, PRICE or REVIEW. Null active/price = leave as it is. */
    public record Decision(String action, Boolean active, Double price, String hold, Double baseline, String reason) {}

    /** The whole rulebook, with no database, so it can be tested on its own. */
    public static Decision decide(Facts f) {
        if (HOLD_REVIEW.equals(f.hold())) {
            // A person switched it back on after reviewing it: accept today's cost as the new normal.
            return f.active() ? new Decision("BASELINE", null, null, null, f.cost() != null ? f.cost() : f.baseline(), "Reviewed by an admin.")
                    : new Decision("NONE", null, null, HOLD_REVIEW, f.baseline(), "Waiting for an admin.");
        }
        if (!f.available()) {
            if (HOLD_STOCK.equals(f.hold()) || !f.active()) return new Decision("NONE", null, null, f.hold(), f.baseline(), "Still out of stock.");
            return new Decision("HIDE", false, null, HOLD_STOCK, f.baseline(), "The supplier is out of stock.");
        }
        boolean relist = HOLD_STOCK.equals(f.hold());
        Boolean on = relist ? Boolean.TRUE : null;
        String same = relist ? "RELIST" : "NONE";
        String sameReason = relist ? "The supplier has it in stock again." : "No change.";
        if (f.cost() == null || f.cost() <= 0) return new Decision(same, on, null, null, f.baseline(), sameReason);
        if (f.baseline() == null || f.baseline() <= 0) {
            return new Decision(relist ? "RELIST" : "BASELINE", on, null, null, f.cost(), relist ? sameReason : "First cost recorded.");
        }
        if (Math.abs(f.cost() - f.baseline()) < 0.005) return new Decision(same, on, null, null, f.baseline(), sameReason);

        String move = "Supplier cost moved from ₹" + money(f.baseline()) + " to ₹" + money(f.cost()) + ". ";
        if (Math.abs(f.cost() - f.baseline()) / f.baseline() > MAX_CHANGE) return review(f, move + "That is more than 15%.");
        if (f.specialPricing()) return review(f, move + "This product has an offer, discount or variants, so its price needs a person.");
        double next = AgentJobService.priceRule(f.cost());
        if (f.mrp() != null && f.mrp() > 0 && next > f.mrp()) return review(f, move + "The new price ₹" + money(next) + " would be above the MRP ₹" + money(f.mrp()) + ".");
        if (f.price() > 0 && Math.abs(next - f.price()) / f.price() > MAX_CHANGE) return review(f, move + "The price would move from ₹" + money(f.price()) + " to ₹" + money(next) + ", more than 15%.");
        if (Math.abs(next - f.price()) < 0.005) return new Decision(relist ? "RELIST" : "BASELINE", on, null, null, f.cost(), relist ? sameReason : move + "Price stays the same.");
        return new Decision("PRICE", on, next, null, f.cost(), move + "Price changed from ₹" + money(f.price()) + " to ₹" + money(next) + ".");
    }

    private static Decision review(Facts f, String reason) {
        return new Decision("REVIEW", false, null, HOLD_REVIEW, f.baseline(), reason);
    }

    private static String money(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    /** The products the watcher should check: on sale, or hidden by the watcher itself. */
    @Transactional(readOnly = true)
    public Map<String, Object> targets() {
        Map<Long, AgentJob> latest = new LinkedHashMap<>();
        for (AgentJob j : jobs.findByStatusAndProductIdNotNullOrderByIdAsc(AgentJob.USED)) latest.put(j.getProductId(), j);
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentJob j : latest.values()) {
            Optional<Product> p = products.findById(j.getProductId());
            if (p.isEmpty() || (!p.get().isActive() && j.getWatchHold() == null)) continue;
            out.add(Map.of("productId", j.getProductId(), "sourceUrl", j.getSourceUrl()));
        }
        return Map.of("products", out, "live", live);
    }

    /** One supplier reading: {"available": bool, "cost": number, "mrp": number}. */
    @Transactional
    public Map<String, Object> report(Long productId, Map<String, Object> body) {
        if (!(body.get("available") instanceof Boolean available)) throw new AgentJobService.BadRequest("A reading needs \"available\".");
        AgentJob job = jobs.findFirstByProductIdAndStatusOrderByIdDesc(productId, AgentJob.USED)
                .orElseThrow(() -> new AgentJobService.BadRequest("This product did not come from a supplier link."));
        Product p = products.findById(productId).orElseThrow(() -> new AgentJobService.BadRequest("No such product."));
        if (!p.isActive() && job.getWatchHold() == null) return Map.of("action", "NONE", "applied", false);

        Double cost = number(body.get("cost"));
        Double mrp = p.getMrp() != null && p.getMrp() > 0 ? p.getMrp() : number(body.get("mrp"));
        boolean special = p.getOfferPrice() != null || !p.getVariants().isEmpty() || !p.getDiscounts().isEmpty();
        double price = p.getPrice() == null ? 0 : p.getPrice();
        Decision d = decide(new Facts(p.isActive(), job.getWatchHold(), job.getWatchCost(), price, mrp, special, available, cost));

        job.setWatchCheckedAt(LocalDateTime.now());
        boolean noteworthy = !Set.of("NONE", "BASELINE").contains(d.action());
        if (live) {
            job.setWatchHold(d.hold());
            job.setWatchCost(d.baseline());
            if (d.active() != null || d.price() != null) productService.applyWatcherChange(productId, d.active(), d.price());
        } else if (job.getWatchCost() == null) {
            job.setWatchCost(d.baseline());
        }
        jobs.save(job);
        if (noteworthy) {
            audit.log(null, "stock-price-watcher", live ? "WATCH_" + d.action() : "WATCH_PREVIEW_" + d.action(),
                    "Product", productId, d.reason(), null);
            if (live) alert(p, d);
        }
        return Map.of("action", d.action(), "applied", live, "reason", d.reason());
    }

    private void alert(Product p, Decision d) {
        String title = switch (d.action()) {
            case "HIDE" -> "Product hidden: supplier out of stock";
            case "RELIST" -> "Product back on sale";
            case "PRICE" -> "Price updated";
            default -> "Product hidden: needs your review";
        };
        String message = p.getName() + " (#" + p.getId() + "): " + d.reason();
        try {
            for (Role role : List.of(Role.ADMIN, Role.SUPER_ADMIN)) {
                for (User u : users.findByRole(role)) notifications.createNotification(u.getId(), "STOCK_WATCH", title, message);
            }
            if ("REVIEW".equals(d.action()) && alertEmail != null && !alertEmail.isBlank()) {
                email.sendWatcherAlert(alertEmail, title, message);
            }
        } catch (Exception e) {
            log.warn("Could not send the watcher alert for product {}: {}", p.getId(), e.getMessage());
        }
    }

    private static Double number(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return null;
    }
}
