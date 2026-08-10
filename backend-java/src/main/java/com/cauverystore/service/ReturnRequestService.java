package com.cauverystore.service;

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

    public ReturnRequestService(ReturnRequestRepository returnRepo, CreditNoteService creditNoteService) {
        this.returnRepo = returnRepo;
        this.creditNoteService = creditNoteService;
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

        String to = status.trim().toUpperCase();
        String from = rr.getStatus() == null || rr.getStatus().isBlank()
                ? REQUESTED : rr.getStatus().trim().toUpperCase();

        if (!to.equals(from)) {
            Set<String> permitted = ALLOWED.get(from);
            if (permitted == null) {
                throw new InvalidReturnTransitionException(
                        "This return is in an unrecognised state (" + from + ") and cannot be moved.");
            }
            if (!permitted.contains(to)) {
                throw new InvalidReturnTransitionException(explain(from, to, permitted));
            }
        }

        rr.setStatus(to);
        // Recorded as the goods pass through, so the outcome of the inspection is visible rather
        // than only implied by the status the return happened to land on.
        if (RECEIVED.equals(to)) rr.setQualityCheckStatus("PENDING");
        if (COMPLETED.equals(to)) rr.setQualityCheckStatus("PASSED");
        if (REJECTED.equals(to) && RECEIVED.equals(from)) rr.setQualityCheckStatus("FAILED");

        ReturnRequest saved = returnRepo.save(rr);

        // Only now, with the goods back and accepted. A second credit note is not created for the
        // same return - CreditNoteService returns the existing one - so passing through COMPLETED
        // and then REFUNDED does not credit twice.
        if (CREDIT_NOTE_TRIGGER_STATUSES.contains(to)) {
            try {
                creditNoteService.generateReturnCreditNote(id, null);
            } catch (Exception e) {
                System.err.println("Auto credit note generation skipped for return " + id + ": " + e.getMessage());
            }
        }
        return saved;
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
