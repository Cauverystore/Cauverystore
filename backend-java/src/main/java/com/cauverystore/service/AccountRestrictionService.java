package com.cauverystore.service;

import com.cauverystore.entities.Order;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.ReturnRequest;
import com.cauverystore.entities.User;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.ReturnRequestRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a stopped account may still do, and when it has finished.
 *
 * <h2>Suspension is a wind-down, not a lockout</h2>
 *
 * Stopping somebody instantly is the wrong shape for a marketplace, because obligations outlive
 * the decision. A suspended seller still owes goods for orders already paid for; a suspended
 * buyer still has a right to return what arrives. Locking either of them out would strand those
 * orders - the seller cannot ship and the buyer cannot ask for a refund - and the marketplace
 * would be the one in breach.
 *
 * So SUSPENDED means: no new business, existing business runs to its end. They keep enough
 * access to discharge what they already owe and nothing more. BLOCKED is the hard stop, for when
 * somebody has to be off the site now and the cost of stranded orders is accepted deliberately.
 *
 * <h2>When an order is finished</h2>
 *
 * Not at delivery. A delivered order can still come back, so it is only finished once its return
 * window has run out - which is per product, because sellers set their own. Cancelled and
 * refunded orders are finished immediately; nothing more can happen to them. An open return is
 * unfinished business whatever the order says.
 */
@Service
public class AccountRestrictionService {

    /** Nothing further can happen to an order in one of these on its own. */
    private static final Set<String> ORDER_FINISHED = Set.of("CANCELLED", "REFUNDED");

    /** A return still being worked through. Anything else is settled. */
    private static final Set<String> RETURN_OPEN =
            Set.of("REQUESTED", "PENDING", "APPROVED", "IN_TRANSIT", "RECEIVED", "UNDER_REVIEW");

    /** Used when a product no longer says, or never said. */
    private static final int DEFAULT_RETURN_WINDOW_DAYS = 7;

    private final OrderRepository orderRepo;
    private final ReturnRequestRepository returnRepo;

    public AccountRestrictionService(OrderRepository orderRepo,
                                     ReturnRequestRepository returnRepo) {
        this.orderRepo = orderRepo;
        this.returnRepo = returnRepo;
    }

    /** Raised when a stopped account tries to start something new. */
    public static class AccountRestrictedException extends RuntimeException {
        public AccountRestrictedException(String message) { super(message); }
    }

    public boolean isStopped(User user) {
        return user != null && ("SUSPENDED".equals(user.getStatus()) || "BLOCKED".equals(user.getStatus()));
    }

    /**
     * Refuses new business to a stopped account, in the buyer's own terms.
     *
     * The gate is here, at the point of commitment, rather than on the basket. Someone winding
     * down can still look at what they had saved; what they cannot do is place another order.
     */
    public void requireMayPlaceOrder(User user) {
        if (!isStopped(user)) return;
        String reason = user.getSuspensionReason();
        throw new AccountRestrictedException(
                "This account is suspended and cannot place new orders."
                        + (reason != null && !reason.isBlank() ? " Reason: " + reason : "")
                        + " Orders you have already placed are not affected, and you can still "
                        + "track them and request a return.");
    }

    /**
     * Whether a suspended account still has business running.
     *
     * The point of asking is that a suspension is not really over until this is empty: the
     * account cannot be closed, and the seller cannot be told to stop watching their orders,
     * while somebody is still waiting for goods or holding a right to return them.
     */
    public Map<String, Object> windDown(User user) {
        List<Order> orders = orderRepo.findByUser(user);
        LocalDateTime now = LocalDateTime.now();

        int awaitingFulfilment = 0;
        int withinReturnWindow = 0;
        LocalDateTime lastObligationEnds = null;

        for (Order o : orders) {
            String status = o.getStatus() == null ? "" : o.getStatus().toUpperCase();
            if (ORDER_FINISHED.contains(status)) continue;

            if (!"DELIVERED".equals(status)) {
                awaitingFulfilment++;
                continue;
            }
            LocalDateTime windowEnds = returnWindowEnds(o);
            if (windowEnds != null && windowEnds.isAfter(now)) {
                withinReturnWindow++;
                if (lastObligationEnds == null || windowEnds.isAfter(lastObligationEnds)) {
                    lastObligationEnds = windowEnds;
                }
            }
        }

        long openReturns = returnRepo.findByUserId(user.getId()).stream()
                .filter(r -> r.getStatus() != null && RETURN_OPEN.contains(r.getStatus().toUpperCase()))
                .count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ordersAwaitingFulfilment", awaitingFulfilment);
        out.put("ordersWithinReturnWindow", withinReturnWindow);
        out.put("openReturns", openReturns);
        out.put("complete", awaitingFulfilment == 0 && withinReturnWindow == 0 && openReturns == 0);
        // Null while a return is open, because a return that is granted starts a refund and there
        // is no way to know today when that finishes. Saying "unknown" beats inventing a date.
        out.put("lastObligationEnds", openReturns == 0 ? lastObligationEnds : null);
        return out;
    }

    /**
     * When the right to return an order runs out.
     *
     * Taken from the longest window of anything in the order, because a customer who may return
     * one item for thirty days has business with us for thirty days, whatever the rest of the
     * basket said.
     */
    private LocalDateTime returnWindowEnds(Order order) {
        if (order.getDeliveredAt() == null) {
            // Delivered but with no timestamp - older rows predate the field. Treated as still
            // open rather than assumed expired, because guessing the wrong way here quietly
            // deprives somebody of a return they are entitled to.
            return LocalDateTime.now().plusDays(DEFAULT_RETURN_WINDOW_DAYS);
        }
        int days = DEFAULT_RETURN_WINDOW_DAYS;
        if (order.getItems() != null) {
            for (var item : order.getItems()) {
                Product p = item.getProduct();
                if (p != null && p.getReturnWindow() != null) {
                    days = Math.max(days, p.getReturnWindow());
                }
            }
        }
        return order.getDeliveredAt().plusDays(days);
    }
}
