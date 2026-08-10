package com.cauverystore.service;

import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderItem;
import com.cauverystore.entities.Product;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Whether an order may be returned, and on what terms.
 *
 * <h2>Why this exists</h2>
 *
 * The only check on a return used to be that the order had been delivered. Nothing looked at when
 * it was delivered, so a return could be raised years later and the seller had no ground to
 * refuse it; and nothing looked at what the goods were, so innerwear and perishables were as
 * returnable as a kettle. Both are things a customer should be told before they pack a parcel,
 * not after it arrives at a warehouse that will not take it.
 *
 * <h2>Where the answer comes from</h2>
 *
 * The window is the seller's own, held per product, because a seller offering thirty days has
 * made a promise the marketplace should keep. The shortest window in the order governs it: a
 * basket cannot be half returnable, and reading the longest would extend a promise nobody made.
 * That is the opposite of the wind-down calculation, which takes the longest - deliberately, and
 * for the same reason in both places, which is that the customer keeps whichever right is wider
 * and the marketplace holds itself to whichever duty is longer.
 */
@Service
public class ReturnEligibilityService {

    /** Raised when goods cannot be sent back. Carries wording meant for the customer. */
    public static class NotReturnableException extends RuntimeException {
        public NotReturnableException(String message) { super(message); }
    }

    /**
     * Categories that cannot come back once delivered, on hygiene, perishability or because the
     * item was made for one person and cannot be sold to anybody else.
     *
     * Matched on words rather than category ids, because the catalogue's categories are free
     * text a seller types. That is looser than it should be and will let something through that
     * ought to be caught - but a category table with a returnable flag is the fix for that, and
     * matching nothing at all until it exists would be worse.
     */
    private static final Set<String> NON_RETURNABLE_TERMS = Set.of(
            "innerwear", "inner wear", "underwear", "lingerie", "briefs",
            "swimwear", "swimsuit",
            "perishable", "fresh", "dairy", "meat", "frozen",
            "clearance",
            "customised", "customized", "personalised", "personalized", "made to order");

    private static final int DEFAULT_RETURN_WINDOW_DAYS = 7;

    /**
     * A change of mind, as opposed to something wrong with the goods.
     *
     * The distinction decides who pays for the parcel to come back, so it is worth naming rather
     * than leaving to whoever reads the reason later.
     */
    private static final Set<String> FAULT_REASONS = Set.of(
            "DAMAGED", "WRONG_ITEM", "DEFECTIVE", "NOT_AS_DESCRIBED", "MISSING_PARTS");

    /**
     * Checks an order can be returned and returns the terms, or explains why not.
     *
     * @throws NotReturnableException with wording written for the customer
     */
    public Map<String, Object> check(Order order, String reason) {
        if (order == null) throw new NotReturnableException("Order not found.");
        if (!"DELIVERED".equalsIgnoreCase(order.getStatus())) {
            throw new NotReturnableException(
                    "Only delivered orders can be returned. This one is " + order.getStatus() + ".");
        }

        String blocked = firstNonReturnableItem(order);
        if (blocked != null) {
            throw new NotReturnableException(
                    "\"" + blocked + "\" cannot be returned. Innerwear, swimwear, perishable "
                            + "goods, clearance items and anything made to order are non-returnable "
                            + "once delivered, for hygiene and safety reasons.");
        }

        int windowDays = shortestWindow(order);
        LocalDateTime deliveredAt = order.getDeliveredAt();
        if (deliveredAt != null) {
            long daysSince = ChronoUnit.DAYS.between(deliveredAt, LocalDateTime.now());
            if (daysSince > windowDays) {
                throw new NotReturnableException(
                        "The return window for this order closed " + (daysSince - windowDays)
                                + " day(s) ago. Returns must be raised within " + windowDays
                                + " days of delivery.");
            }
        }
        // A delivered order with no timestamp predates the field. Refusing on a date we do not
        // have would deny a return somebody is entitled to, so the doubt goes their way - the
        // same choice the wind-down calculation makes about the same missing data.

        boolean sellerAtFault = reason != null
                && FAULT_REASONS.contains(reason.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));

        Map<String, Object> terms = new LinkedHashMap<>();
        terms.put("returnable", true);
        terms.put("returnWindowDays", windowDays);
        terms.put("returnBy", deliveredAt == null ? null : deliveredAt.plusDays(windowDays));
        terms.put("sellerAtFault", sellerAtFault);
        // Who pays for the parcel to travel. Free when the goods were wrong; the customer's cost
        // when they simply changed their mind.
        terms.put("pickupFree", sellerAtFault);
        terms.put("shippingBorneBy", sellerAtFault ? "SELLER" : "CUSTOMER");
        return terms;
    }

    /** The name of the first item that cannot come back, or null when all of them can. */
    private String firstNonReturnableItem(Order order) {
        if (order.getItems() == null) return null;
        for (OrderItem item : order.getItems()) {
            Product p = item.getProduct();
            if (p == null) continue;
            if (isNonReturnable(p)) return p.getName();
        }
        return null;
    }

    private boolean isNonReturnable(Product p) {
        // An explicit "No Returns" on the product is the seller saying so directly and settles it
        // without any guessing at words.
        if (p.getReturnPolicy() != null
                && p.getReturnPolicy().toLowerCase(Locale.ROOT).contains("no return")) {
            return true;
        }
        if (p.getReturnWindow() != null && p.getReturnWindow() <= 0) return true;

        String haystack = ((p.getName() == null ? "" : p.getName()) + " "
                + (p.getCategory() != null && p.getCategory().getName() != null
                        ? p.getCategory().getName() : ""))
                .toLowerCase(Locale.ROOT);
        return NON_RETURNABLE_TERMS.stream().anyMatch(haystack::contains);
    }

    /**
     * The shortest window across the order.
     *
     * A basket cannot be half returnable, and taking the longest would hold a seller to a promise
     * they never made about their own item.
     */
    private int shortestWindow(Order order) {
        List<OrderItem> items = order.getItems();
        if (items == null || items.isEmpty()) return DEFAULT_RETURN_WINDOW_DAYS;
        int shortest = Integer.MAX_VALUE;
        for (OrderItem item : items) {
            Product p = item.getProduct();
            int days = (p != null && p.getReturnWindow() != null)
                    ? p.getReturnWindow() : DEFAULT_RETURN_WINDOW_DAYS;
            shortest = Math.min(shortest, days);
        }
        return shortest == Integer.MAX_VALUE ? DEFAULT_RETURN_WINDOW_DAYS : shortest;
    }
}
