package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.GstRateProposal;
import com.cauverystore.entities.GstRateSource;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.repository.GstRateMasterRepository;
import com.cauverystore.repository.GstRateProposalRepository;
import com.cauverystore.repository.GstRateSourceRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.service.CbicNotificationReader.Change;
import com.cauverystore.service.CbicNotificationReader.Operation;
import com.cauverystore.service.CbicNotificationReader.Reading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a CBIC rate notification into proposals, and proposals into rates - the second step only
 * when a person says so.
 *
 * <h2>The loop this closes</h2>
 *
 * CbicNotificationDetector notices a notification the morning it is published. Until now that
 * was where the automation stopped: somebody had to open the PDF, work out what changed, type it
 * into a spreadsheet and import it. This drafts that work. The notification is read by rule,
 * each change is listed with the rate being charged today beside the rate it would become, and
 * the products it would touch are counted, so what is left for a person is the decision.
 *
 * <h2>What stays human, and why</h2>
 *
 * Accepting. A proposal is the output of pattern-matching on text pulled out of a PDF, and the
 * reader says plainly when it is unsure - but it cannot know what it has not noticed. A rate that
 * is wrong reaches customers' invoices and the returns filed from them, and is found months later
 * by an assessment. One person reading one clause against one row is cheap by comparison.
 *
 * Accepting writes the rate as VERIFIED straight away rather than staging it for a second
 * review. The two-person rule on spreadsheet imports exists because the first person is a
 * transcriber who can mistype; here the transcriber is the reader, and the person accepting is
 * the check.
 */
@Service
public class GstRateChangeService {

    private static final Logger log = LoggerFactory.getLogger(GstRateChangeService.class);

    private final GstRateProposalRepository proposalRepo;
    private final GstRateMasterRepository rateRepo;
    private final GstRateSourceRepository sourceRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final CbicNotificationReader reader;
    private final EmailService emailService;

    public GstRateChangeService(GstRateProposalRepository proposalRepo, GstRateMasterRepository rateRepo,
                                GstRateSourceRepository sourceRepo, ProductRepository productRepo,
                                UserRepository userRepo, CbicNotificationReader reader,
                                EmailService emailService) {
        this.proposalRepo = proposalRepo;
        this.rateRepo = rateRepo;
        this.sourceRepo = sourceRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.reader = reader;
        this.emailService = emailService;
    }

    public static class RateChangeException extends RuntimeException {
        public RateChangeException(String message) { super(message); }
    }

    /**
     * Reads a notification and stores what it changes as proposals.
     *
     * Safe to call twice: a notification that already has proposals is left alone, because
     * re-drafting would bring back rows somebody has already rejected.
     *
     * @return how many proposals were drafted
     */
    @Transactional
    public int draft(GstRateSource source, byte[] pdf) {
        if (source == null || source.getId() == null || pdf == null) return 0;
        if (proposalRepo.existsBySourceId(source.getId())) return 0;

        Reading reading = reader.readPdf(pdf);

        // Only the Central Tax notification is drafted from. The Integrated and Union Territory
        // ones published beside it make the same change in their own Acts, and drafting all
        // three would put every rate on the desk three times.
        if (reading.taxKind() != null && !reading.taxKind().startsWith("Central")) {
            log.info("{} is the {} counterpart of a Central Tax notification; no separate "
                    + "proposals drafted.", source.getNotificationNumber(), reading.taxKind());
            return 0;
        }

        if (source.getEffectiveFrom() == null && reading.effectiveFrom() != null) {
            source.setEffectiveFrom(reading.effectiveFrom());
            sourceRepo.save(source);
        }

        List<GstRateProposal> drafted = new ArrayList<>();
        for (Change change : reading.changes()) {
            if (change.codes().isEmpty()) {
                drafted.add(proposal(source, reading, change, null));
            } else {
                for (String code : change.codes()) drafted.add(proposal(source, reading, change, code));
            }
        }
        if (drafted.isEmpty()) {
            // Nothing matched at all. Said as a proposal of its own so that it appears on the
            // desk: an empty desk would read as "nothing changed".
            GstRateProposal none = new GstRateProposal();
            none.setSourceId(source.getId());
            none.setNotificationNumber(source.getNotificationNumber());
            none.setOperation(Operation.UNREAD.name());
            none.setNeedsAttention(true);
            none.setAttentionReason("Nothing in this notification could be read by rule. "
                    + String.join(" ", reading.warnings()) + " It has to be read by a person.");
            none.setClause(source.getDescription());
            drafted.add(none);
        }
        proposalRepo.saveAll(drafted);
        log.info("Drafted {} rate proposal(s) from {} (effective {}).", drafted.size(),
                source.getNotificationNumber(), reading.effectiveFrom());
        return drafted.size();
    }

    private GstRateProposal proposal(GstRateSource source, Reading reading, Change change, String code) {
        GstRateProposal p = new GstRateProposal();
        p.setSourceId(source.getId());
        p.setNotificationNumber(source.getNotificationNumber());
        p.setOperation(change.operation().name());
        p.setSchedule(change.schedule());
        p.setSerialNo(change.serialNo());
        p.setHsnCode(code);
        p.setEffectiveFrom(reading.effectiveFrom());
        p.setDescription(change.description());
        p.setClause(change.clause());
        boolean rateBearing = code != null
                && (change.operation() == Operation.INSERT || change.operation() == Operation.SUBSTITUTE_CODES);
        if (rateBearing) {
            p.setProposedRate(change.totalRate());
            p.setRateWhenDrafted(rateInForce(code, LocalDate.now()));
        }
        String reason = change.attentionReason();
        if (rateBearing && reading.effectiveFrom() == null) {
            reason = (reason == null ? "" : reason + " ")
                    + "The date this comes into force could not be read, so it cannot be accepted "
                    + "until the date is known.";
        }
        p.setNeedsAttention(change.needsAttention() || reason != null);
        p.setAttentionReason(reason);
        return p;
    }

    /**
     * The verified rate a code resolves to on a date, looking up through its parent headings.
     *
     * An eight-digit code with no row of its own is taxed at its six- or four-digit heading, so
     * "no rate" for the code itself would be the wrong thing to show beside a proposal.
     */
    Double rateInForce(String code, LocalDate onDate) {
        for (int len = code.length(); len >= 2; len -= 2) {
            List<GstRateMaster> found = rateRepo.findApplicable(
                    code.substring(0, len), GstRateMaster.STATUS_VERIFIED, onDate);
            if (!found.isEmpty()) return found.get(0).getGstRate();
        }
        return null;
    }

    /** Proposals for the desk, each with the rate charged today and the products it touches. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String status) {
        List<GstRateProposal> rows = status == null || status.isBlank()
                ? proposalRepo.findByStatusOrderByIdAsc(GstRateProposal.STATUS_PENDING)
                : "ALL".equalsIgnoreCase(status) ? proposalRepo.findAll()
                : proposalRepo.findByStatusOrderByIdAsc(status.toUpperCase());
        List<Product> selling = productRepo.findByActiveTrue();
        List<Map<String, Object>> out = new ArrayList<>();
        for (GstRateProposal p : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("notification", p.getNotificationNumber());
            row.put("operation", p.getOperation());
            row.put("schedule", p.getSchedule());
            row.put("serialNo", p.getSerialNo());
            row.put("hsnCode", p.getHsnCode());
            row.put("description", p.getDescription());
            row.put("proposedRate", p.getProposedRate());
            row.put("currentRate", p.getHsnCode() == null ? null : rateInForce(p.getHsnCode(), LocalDate.now()));
            row.put("effectiveFrom", p.getEffectiveFrom());
            row.put("clause", p.getClause());
            row.put("needsAttention", p.isNeedsAttention());
            row.put("attentionReason", p.getAttentionReason());
            row.put("canAccept", canAccept(p));
            row.put("status", p.getStatus());
            row.put("decidedBy", p.getDecidedBy());
            row.put("decidedAt", p.getDecidedAt());
            row.put("decisionNote", p.getDecisionNote());
            List<String> affected = new ArrayList<>();
            if (p.getHsnCode() != null) {
                for (Product product : selling) {
                    String hsn = product.getHsnCode();
                    if (hsn != null && hsn.replaceAll("\\s", "").startsWith(p.getHsnCode())) {
                        affected.add(product.getName());
                    }
                }
            }
            row.put("affectedProductCount", affected.size());
            row.put("affectedProducts", affected.size() > 10 ? affected.subList(0, 10) : affected);
            out.add(row);
        }
        return out;
    }

    private boolean canAccept(GstRateProposal p) {
        return GstRateProposal.STATUS_PENDING.equals(p.getStatus())
                && p.getHsnCode() != null && p.getProposedRate() != null && p.getEffectiveFrom() != null;
    }

    /**
     * Accepts one proposal: the rate becomes VERIFIED from its effective date.
     *
     * The rate it replaces is closed off the day before rather than overwritten, because an
     * invoice raised last month still has to resolve to the rate that applied last month.
     */
    @Transactional
    public GstRateProposal accept(Long id, String acceptedBy, String note) {
        GstRateProposal p = proposalRepo.findById(id)
                .orElseThrow(() -> new RateChangeException("That proposal does not exist."));
        if (acceptedBy == null || acceptedBy.isBlank()) {
            throw new RateChangeException("Accepting a rate has to be attributed to someone.");
        }
        if (!GstRateProposal.STATUS_PENDING.equals(p.getStatus())) {
            throw new RateChangeException("This proposal was already " + p.getStatus().toLowerCase()
                    + " by " + p.getDecidedBy() + ".");
        }
        if (!canAccept(p)) {
            throw new RateChangeException("This proposal carries no rate that can be applied - it "
                    + "is a clause to be read and dealt with by hand. Acknowledge it once that is done.");
        }

        boolean alreadyInForce = false;
        for (GstRateMaster current : rateRepo.findByHsnCodeOrderByEffectiveFromDesc(p.getHsnCode())) {
            if (current.getEffectiveTo() != null || current.getEffectiveFrom() == null) continue;
            if (current.getEffectiveFrom().equals(p.getEffectiveFrom())
                    && current.getGstRate() != null
                    && Math.abs(current.getGstRate() - p.getProposedRate()) < 0.0001
                    && GstRateMaster.STATUS_VERIFIED.equals(current.getStatus())) {
                alreadyInForce = true;   // somebody applied this notification by hand already
                continue;
            }
            if (!current.getEffectiveFrom().isBefore(p.getEffectiveFrom())) continue;
            current.setEffectiveTo(p.getEffectiveFrom().minusDays(1));
            current.setNotes((current.getNotes() == null ? "" : current.getNotes() + " | ")
                    + "Closed off by " + p.getNotificationNumber() + ", which sets a new rate for "
                    + "this heading from " + p.getEffectiveFrom() + ". Accepted by " + acceptedBy + ".");
            rateRepo.save(current);
        }

        if (!alreadyInForce) {
            GstRateMaster rate = new GstRateMaster();
            rate.setHsnCode(p.getHsnCode());
            rate.setGstRate(p.getProposedRate());
            rate.setEffectiveFrom(p.getEffectiveFrom());
            rate.setConditionText(p.getDescription());
            rate.setSource("Notification " + p.getNotificationNumber()
                    + (p.getSchedule() == null ? "" : ", Schedule " + p.getSchedule())
                    + (p.getSerialNo() == null ? "" : ", S. No. " + p.getSerialNo()));
            rate.setNotes("Read from the notification by rule and accepted by " + acceptedBy + " on "
                    + LocalDate.now() + ". Clause: " + p.getClause());
            rate.setStatus(GstRateMaster.STATUS_VERIFIED);
            rate.setVerifiedBy(acceptedBy);
            rate.setVerifiedAt(LocalDateTime.now());
            rateRepo.save(rate);
        }

        decide(p, GstRateProposal.STATUS_ACCEPTED, acceptedBy,
                alreadyInForce ? "Already in force at this rate from this date; nothing was changed."
                        + (note == null || note.isBlank() ? "" : " " + note) : note);
        log.info("GST rate proposal {} accepted by {}: {} at {}% from {} ({}).", p.getId(), acceptedBy,
                p.getHsnCode(), p.getProposedRate(), p.getEffectiveFrom(), p.getNotificationNumber());
        return p;
    }

    /**
     * Accepts every proposal of a notification that the reader had no doubt about.
     *
     * Anything marked for attention is left on the desk. Those are exactly the rows where the
     * reader is saying it may have misread, and accepting them in bulk would defeat the marking.
     */
    @Transactional
    public Map<String, Object> acceptClean(Long sourceId, String acceptedBy) {
        int accepted = 0;
        int left = 0;
        for (GstRateProposal p : proposalRepo.findBySourceIdOrderByIdAsc(sourceId)) {
            if (!GstRateProposal.STATUS_PENDING.equals(p.getStatus())) continue;
            if (p.isNeedsAttention() || !canAccept(p)) { left++; continue; }
            accept(p.getId(), acceptedBy, "Accepted with the other clean proposals of this notification.");
            accepted++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accepted", accepted);
        out.put("leftForReview", left);
        return out;
    }

    /** Rejects a proposal: the reader got it wrong, or it does not apply. Nothing is charged. */
    @Transactional
    public GstRateProposal reject(Long id, String rejectedBy, String reason) {
        return close(id, GstRateProposal.STATUS_REJECTED, rejectedBy, reason,
                "Say why it is being rejected - the next person to read this notification needs to "
                        + "know whether the reader misread it or the change does not apply here.");
    }

    /** Records that a clause with no rate to accept has been read and dealt with by hand. */
    @Transactional
    public GstRateProposal acknowledge(Long id, String by, String note) {
        return close(id, GstRateProposal.STATUS_ACKNOWLEDGED, by, note,
                "Say what was done about it - which rates were changed by hand, or why none were.");
    }

    private GstRateProposal close(Long id, String status, String by, String note, String noteRequired) {
        GstRateProposal p = proposalRepo.findById(id)
                .orElseThrow(() -> new RateChangeException("That proposal does not exist."));
        if (by == null || by.isBlank()) throw new RateChangeException("This has to be attributed to someone.");
        if (!GstRateProposal.STATUS_PENDING.equals(p.getStatus())) {
            throw new RateChangeException("This proposal was already " + p.getStatus().toLowerCase()
                    + " by " + p.getDecidedBy() + ".");
        }
        if (note == null || note.isBlank()) throw new RateChangeException(noteRequired);
        decide(p, status, by, note);
        return p;
    }

    private void decide(GstRateProposal p, String status, String by, String note) {
        p.setStatus(status);
        p.setDecidedBy(by);
        p.setDecidedAt(LocalDateTime.now());
        p.setDecisionNote(note);
        proposalRepo.save(p);

        // The notification counts as applied once nothing of it is left undecided. Until then
        // it stays on the compliance screen as outstanding, which is the truth.
        if (proposalRepo.countBySourceIdAndStatus(p.getSourceId(), GstRateProposal.STATUS_PENDING) == 0) {
            sourceRepo.findById(p.getSourceId()).ifPresent(source -> {
                if (!Boolean.TRUE.equals(source.getApplied())) {
                    source.setApplied(true);
                    source.setLastVerifiedAt(LocalDateTime.now());
                    source.setLastVerifiedBy(by);
                    sourceRepo.save(source);
                    log.info("{} is now applied: every change in it has been decided, the last by {}.",
                            source.getNotificationNumber(), by);
                }
            });
        }
    }

    /** How many proposals are waiting, for the compliance screen. */
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pending", proposalRepo.countByStatus(GstRateProposal.STATUS_PENDING));
        out.put("accepted", proposalRepo.countByStatus(GstRateProposal.STATUS_ACCEPTED));
        out.put("rejected", proposalRepo.countByStatus(GstRateProposal.STATUS_REJECTED));
        out.put("acknowledged", proposalRepo.countByStatus(GstRateProposal.STATUS_ACKNOWLEDGED));
        return out;
    }

    /**
     * Tells the people who can act on it that CBIC has published a rate notification.
     *
     * Sent to every admin rather than to one address in a setting, because a setting nobody
     * filled in sends nothing and looks exactly like no notification having been published.
     * Best-effort: the notification is already recorded, and a failed email must not undo that.
     */
    public void alertAdmins(GstRateSource source, int drafted) {
        try {
            Set<String> recipients = new LinkedHashSet<>();
            for (Role role : List.of(Role.SUPER_ADMIN, Role.ADMIN)) {
                for (User u : userRepo.findByRole(role)) {
                    if (u.getEmail() != null && !u.getEmail().isBlank() && u.isActive()) {
                        recipients.add(u.getEmail().trim());
                    }
                }
            }
            for (String to : recipients) {
                emailService.sendGstRateNotificationAlert(to, source.getNotificationNumber(),
                        source.getNotificationDate(), source.getEffectiveFrom(),
                        source.getDescription(), drafted);
            }
            if (recipients.isEmpty()) {
                log.error("GST COMPLIANCE: {} was published and there is no active admin with an "
                        + "email address to tell.", source.getNotificationNumber());
            }
        } catch (Exception e) {
            log.error("The alert for {} could not be sent: {}. It is recorded and shows on the "
                    + "compliance screen regardless.", source.getNotificationNumber(), e.getMessage());
        }
    }
}
