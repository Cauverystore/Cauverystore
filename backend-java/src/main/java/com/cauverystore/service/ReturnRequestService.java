package com.cauverystore.service;

import com.cauverystore.entities.CreditNote;
import com.cauverystore.entities.OrderItem;
import com.cauverystore.entities.Refund;
import com.cauverystore.entities.ReturnRequest;
import com.cauverystore.repository.ReturnRequestRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The return lifecycle.
 *
 * <h2>A return is processed when the goods are back, not when it is agreed</h2>
 *
 * A credit note is not paperwork. Under section 34 it reduces the seller's output tax liability
 * for the month it is issued in, and it is what the refund rests on. Issuing it on APPROVED -
 * which is what used to happen - meant the tax came off, and the money went back, at the moment
 * somebody agreed the customer <em>may</em> send the goods back. If the parcel never arrived, or
 * arrived as the wrong item, or arrived damaged and was refused, the credit note had already been
 * issued and the liability already reduced. Nothing reversed it.
 *
 * So it now issues on COMPLETED, and COMPLETED cannot be reached without the goods being
 * received first.
 *
 * <h2>Why the transitions are enforced at all</h2>
 *
 * The status endpoint took any string and wrote it. There was nothing to stop a return going
 * straight from REQUESTED to COMPLETED, which would have got round the rule above entirely -
 * a guard that only holds when everybody remembers the right order is not a guard.
 */
@Service
public class ReturnRequestService {

    /** Raised when a return is moved somewhere it cannot go from where it is. */
    public static class InvalidReturnTransitionException extends RuntimeException {
        public InvalidReturnTransitionException(String message) { super(message); }
    }

    public static final String REQUESTED = "REQUESTED";
    public static final String APPROVED = "APPROVED";
    public static final String IN_TRANSIT = "IN_TRANSIT";
    public static final String RECEIVED = "RECEIVED";
    public static final String COMPLETED = "COMPLETED";
    public static final String REFUNDED = "REFUNDED";
    public static final String REFUND_ISSUED = "REFUND_ISSUED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    /**
     * Where a return may go from where it is.
     *
     * REJECTED is reachable from every live state on purpose: a return can fail at any point,
     * including after the parcel is opened and found to hold something else.
     */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            REQUESTED, Set.of(APPROVED, REJECTED, CANCELLED),
            APPROVED, Set.of(IN_TRANSIT, RECEIVED, REJECTED, CANCELLED),
            IN_TRANSIT, Set.of(RECEIVED, REJECTED),
            // The quality check lives here. Accepting the goods completes the return; refusing
            // them rejects it, and no credit note is ever issued.
            RECEIVED, Set.of(COMPLETED, REJECTED),
            COMPLETED, Set.of(REFUNDED, REFUND_ISSUED),
            REFUNDED, Set.of(REFUND_ISSUED),
            REFUND_ISSUED, Set.of(),
            REJECTED, Set.of(),
            CANCELLED, Set.of());

    /**
     * The states that mean the goods are back and accepted.
     *
     * APPROVED is deliberately absent. It means "yes, send it back" - the goods are still with
     * the customer at that point, and there is nothing to credit.
     */
    private static final Set<String> CREDIT_NOTE_TRIGGER_STATUSES =
            Set.of(COMPLETED, REFUNDED, REFUND_ISSUED);

    private final ReturnRequestRepository returnRepo;
    private final CreditNoteService creditNoteService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public ReturnRequestService(ReturnRequestRepository returnRepo,
                                CreditNoteService creditNoteService,
                                InventoryService inventoryService,
                                PaymentService paymentService,
                                NotificationService notificationService,
                                EmailService emailService) {
        this.returnRepo = returnRepo;
        this.creditNoteService = creditNoteService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    /**
     * The names a return has been stored under, mapped onto the lifecycle.
     *
     * Returns raised through My Orders were written as PENDING, so introducing the lifecycle
     * without this stranded every one of them: PENDING was not a state the map knew, so the only
     * answer to any move was "unrecognised state" and the return could not be progressed at all.
     * Normalising is better than rewriting the rows, because it also covers whatever a future
     * caller invents, and older wording keeps working from a screen nobody has updated yet.
     */
    private static final Map<String, String> ALIASES = Map.of(
            "PENDING", REQUESTED,
            "UNDER_REVIEW", RECEIVED,
            "PICKED", IN_TRANSIT,
            "PICKED_UP", IN_TRANSIT,
            "REFUND_INITIATED", REFUNDED);

    /** The lifecycle name for a stored status, defaulting a blank one to the start. */
    private String normalise(String status) {
        if (status == null || status.isBlank()) return REQUESTED;
        String upper = status.trim().toUpperCase();
        return ALIASES.getOrDefault(upper, upper);
    }

    public List<ReturnRequest> getAll() { return returnRepo.findAll(); }
    public ReturnRequest getById(Long id) { return returnRepo.findById(id).orElse(null); }
    public ReturnRequest create(ReturnRequest rr) {
        if (rr.getStatus() == null || rr.getStatus().isBlank()) rr.setStatus(REQUESTED);
        return returnRepo.save(rr);
    }

    public ReturnRequest updateStatus(Long id, String status) {
        ReturnRequest rr = returnRepo.findById(id).orElse(null);
        if (rr == null) return null;
        if (status == null || status.isBlank()) {
            throw new InvalidReturnTransitionException("A return status is required.");
        }

        String to = normalise(status);
        String from = normalise(rr.getStatus());

        // Re-posting the status a screen is already showing is not a move. It must not be
        // rejected - screens do it routinely - but nothing may happen either: running the side
        // effects again would put the returned goods back into stock a second time.
        if (to.equals(from)) {
            return rr;
        }

        Set<String> permitted = ALLOWED.get(from);
        if (permitted == null) {
            throw new InvalidReturnTransitionException(
                    "This return is in an unrecognised state (" + from + ") and cannot be moved.");
        }
        if (!permitted.contains(to)) {
            throw new InvalidReturnTransitionException(explain(from, to, permitted));
        }

        rr.setStatus(to);
        // Recorded as the goods pass through, so the outcome of the inspection is visible rather
        // than only implied by the status the return happened to land on.
        if (RECEIVED.equals(to)) rr.setQualityCheckStatus("PENDING");
        if (COMPLETED.equals(to)) rr.setQualityCheckStatus("PASSED");
        if (REJECTED.equals(to) && RECEIVED.equals(from)) {
            rr.setQualityCheckStatus("FAILED");
            // Goods came back but cannot be sold again. Recorded here rather than left implied
            // by the rejection, because the difference between "customer never sent it" and
            // "it arrived broken" is the difference between no loss and written-off stock.
            rr.setCondition("UNSELLABLE");
        }

        ReturnRequest saved = returnRepo.save(rr);

        // Passing quality check is the point the goods rejoin sellable stock. Nothing did this
        // before, so returned items were credited and refunded while the stock they came from
        // stayed marked as sold - inventory drifting further from the shelf with every return.
        if (COMPLETED.equals(to)) {
            restock(saved);
        }

        // Only now, with the goods back and accepted. A second credit note is not created for the
        // same return - CreditNoteService returns the existing one - so passing through COMPLETED
        // and then REFUNDED does not credit twice.
        notify(saved, to, from);

        if (CREDIT_NOTE_TRIGGER_STATUSES.contains(to)) {
            Double credited = null;
            try {
                Map<String, Object> result = creditNoteService.generateReturnCreditNote(id, null);
                Object note = result.get("creditNote");
                if (note instanceof CreditNote cn) credited = cn.getTotalAmount();
            } catch (Exception e) {
                System.err.println("Auto credit note generation skipped for return " + id + ": " + e.getMessage());
            }
            if (COMPLETED.equals(to)) {
                payBack(saved, credited);
            }
        }
        return saved;
    }

    /**
     * Tells the customer where their return has got to.
     *
     * Every stage, because a return is a period of not knowing whether you are getting your money
     * back - and silence during it is the complaint, not the outcome. The wording says what has
     * happened and what happens next, since "status updated" tells somebody nothing they can act
     * on.
     *
     * Best-effort. The return has already moved and been saved by this point; failing the
     * transition because a message could not be sent would leave the warehouse and the system
     * disagreeing about where a parcel is, which is worse than a missed email.
     */
    private void notify(ReturnRequest rr, String to, String from) {
        String heading;
        String body;
        switch (to) {
            case APPROVED -> {
                heading = "Return approved";
                body = "Send the item back using the instructions on your orders page. Once it "
                        + "reaches us we will check it and start your refund.";
            }
            case IN_TRANSIT -> {
                heading = "Return picked up";
                body = "We have collected the item and it is on its way back to us.";
            }
            case RECEIVED -> {
                heading = "Return received";
                body = "The item is back with us and is being checked. This usually takes a "
                        + "day or two, and we will write again either way.";
            }
            case COMPLETED -> {
                heading = "Return accepted - refund on its way";
                body = "The item passed our check and your refund has been sent to your original "
                        + "payment method. Instant refunds arrive within minutes; otherwise allow "
                        + "5-7 business days.";
            }
            case REFUNDED, REFUND_ISSUED -> {
                heading = "Refund sent";
                body = "Your refund has been released to the payment method you used. If it has "
                        + "not appeared within 7 business days, reply to this email.";
            }
            case REJECTED -> {
                heading = "Return not accepted";
                // Split, because the two rejections are different situations: one is a decision
                // about the request, the other is a decision about the goods that came back.
                body = RECEIVED.equals(from)
                        ? "The item did not pass our check, so we are unable to accept this "
                                + "return and no refund has been issued."
                                + (rr.getReason() != null ? " Return reason given: " + rr.getReason() + "." : "")
                        : "We are unable to accept this return request.";
            }
            case CANCELLED -> {
                heading = "Return cancelled";
                body = "This return request has been cancelled. Nothing further will happen.";
            }
            default -> { return; }
        }

        String returnId = "RET-" + rr.getId();
        try {
            if (rr.getUser() != null && rr.getUser().getId() != null) {
                notificationService.createNotification(rr.getUser().getId(), "RETURN", heading,
                        returnId + ": " + body);
            }
            if (rr.getUser() != null && rr.getUser().getEmail() != null) {
                emailService.sendReturnStageUpdate(rr.getUser().getEmail(), returnId, heading, body);
            }
        } catch (Exception e) {
            System.err.println("Return notification skipped for " + returnId + ": " + e.getMessage());
        }
    }

    /**
     * Sends the money back once the goods have passed their check.
     *
     * <h2>Where the amount comes from</h2>
     *
     * The credit note, not the order. The credit note is the document that states what is being
     * credited and carries the tax reversal that goes with it, so refunding anything else would
     * put the money and the paperwork at different numbers - and the paperwork is the one the
     * return is filed against.
     *
     * When no credit note could be produced, nothing is guessed: a refund is not attempted, and
     * the return is left for somebody to settle by hand. Inventing an amount here would be
     * paying out a figure no document supports.
     *
     * <h2>Speed</h2>
     *
     * Asked for at the instant rail with a fall back to the ordinary one, because a customer
     * sending goods back has already waited once. Razorpay reports which it managed, and that is
     * what the customer is told rather than what was asked for.
     */
    private void payBack(ReturnRequest rr, Double creditedAmount) {
        if (creditedAmount == null || creditedAmount <= 0) {
            System.err.println("Refund not attempted for return " + rr.getId()
                    + ": no credit note amount to refund. Settle this one manually.");
            return;
        }
        try {
            Long orderId = rr.getOrder() != null ? rr.getOrder().getId() : null;
            if (orderId == null) return;
            Refund refund = paymentService.processRefundForReturn(
                    rr.getId(), orderId, creditedAmount,
                    "Return " + "RET-" + rr.getId() + (rr.getReason() != null ? ": " + rr.getReason() : ""),
                    "optimum");
            rr.setRefundAmount(creditedAmount);
            returnRepo.save(rr);
            System.out.println("Refund " + refund.getId() + " raised for return " + rr.getId()
                    + " (" + refund.getStatus() + ")");
        } catch (Exception e) {
            // The goods are back and the credit note is issued either way. A refund that could
            // not be raised is a queue item, not a reason to undo the quality check.
            System.err.println("Refund could not be raised for return " + rr.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Puts accepted goods back on the shelf.
     *
     * A return may name its own product and quantity, or - for returns raised against a whole
     * order, which is how the customer flow creates them - name neither. In that case everything
     * in the order goes back, because the whole order is what came back.
     *
     * Best-effort on purpose: the goods are physically in the warehouse whatever happens here,
     * and losing the quality-check result because a stock row could not be written would leave
     * the return looking unprocessed while the parcel sits on a shelf.
     */
    private void restock(ReturnRequest rr) {
        try {
            if (rr.getProduct() != null) {
                inventoryService.restoreStock(rr.getProduct(),
                        rr.getQuantity() != null ? rr.getQuantity() : 1);
                return;
            }
            if (rr.getOrder() != null && rr.getOrder().getItems() != null) {
                for (OrderItem item : rr.getOrder().getItems()) {
                    if (item.getProduct() != null) {
                        inventoryService.restoreStock(item.getProduct(), item.getQuantity());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Restock skipped for return " + rr.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Says what is wrong in terms of the goods rather than the state machine.
     *
     * Whoever hits this is looking at a parcel, not at a diagram, and "you cannot complete a
     * return before the goods are back" is actionable where a list of valid states is not.
     */
    private String explain(String from, String to, Set<String> permitted) {
        if (COMPLETED.equals(to)) {
            return "This return cannot be completed yet - the goods have not been received. "
                    + "A return is only completed once the parcel is back and has passed its "
                    + "check, because completing it issues the credit note and the refund. "
                    + "Mark it as received first.";
        }
        if (REFUNDED.equals(to) || REFUND_ISSUED.equals(to)) {
            return "This return cannot be refunded from " + from + ". Refunds follow completion, "
                    + "which requires the goods back and accepted.";
        }
        return "A return that is " + from + " cannot become " + to + ". It can go to: "
                + (permitted.isEmpty() ? "nowhere - this is a final state" : String.join(", ", permitted)) + ".";
    }

    public List<ReturnRequest> getByStatus(String status) { return returnRepo.findByStatus(status); }
    public List<ReturnRequest> getByOrder(Long orderId) { return returnRepo.findByOrderId(orderId); }
    public List<ReturnRequest> getByUser(Long userId) { return returnRepo.findByUserId(userId); }
}
