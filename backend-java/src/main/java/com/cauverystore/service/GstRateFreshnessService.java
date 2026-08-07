package com.cauverystore.service;

import com.cauverystore.entities.GstRateSource;
import com.cauverystore.repository.GstRateSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Answers "are these the current GST rates?" - and chases the answer when it goes stale.
 *
 * <h2>Why this is not a poller</h2>
 *
 * The obvious design is a job that fetches CBIC's notification list nightly and alerts on
 * anything new. CBIC's notification API returns 401 to anyone without credentials, and the
 * public site is a single-page app with no server-rendered listing, so that job cannot be
 * written honestly. Scraping a third-party tax blog instead would produce an authoritative-
 * looking signal from a source nobody would accept in an audit.
 *
 * What CAN be automated is the chasing. The rates record which notifications they were built
 * from and when a human last confirmed nothing newer exists. That age is measured daily and
 * escalates. It converts "we cannot verify" into "our verification is 47 days old, and Priya
 * did it" - which is a fact someone can act on, and which an auditor can be shown.
 *
 * <h2>Why it does not block sales</h2>
 *
 * A stale verification is a risk, not proof of a wrong rate. Halting a shop because nobody
 * clicked a button for two months would cause certain harm to avoid a possible one. It is
 * reported loudly instead, and it appears in the compliance readiness check.
 */
@Service
public class GstRateFreshnessService {

    private static final Logger log = LoggerFactory.getLogger(GstRateFreshnessService.class);

    public static final String STATUS_CURRENT = "CURRENT";
    public static final String STATUS_DUE = "DUE";
    public static final String STATUS_OVERDUE = "OVERDUE";
    public static final String STATUS_NEVER = "NEVER_VERIFIED";

    /** How long a verification stays good before it is worth chasing. */
    @Value("${gst.rate-verification.due-after-days:30}")
    private int dueAfterDays;

    @Value("${gst.rate-verification.overdue-after-days:60}")
    private int overdueAfterDays;

    private final GstRateSourceRepository sourceRepo;
    private final AuditService auditService;

    public GstRateFreshnessService(GstRateSourceRepository sourceRepo, AuditService auditService) {
        this.sourceRepo = sourceRepo;
        this.auditService = auditService;
    }

    /**
     * Seeds the notifications the committed rate seed was built from.
     *
     * Written once and never overwritten - the verification stamps live on these rows, and
     * re-seeding on every restart would quietly reset them to "never checked".
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedKnownSources() {
        try {
            record Known(String number, LocalDate dated, LocalDate effective, String what) {}
            List<Known> known = List.of(
                    new Known("09/2025-Central Tax (Rate)", LocalDate.of(2025, 9, 17),
                            LocalDate.of(2025, 9, 22),
                            "Base rate schedules I-VII. Supersedes 01/2017."),
                    new Known("10/2025-Central Tax (Rate)", LocalDate.of(2025, 9, 17),
                            LocalDate.of(2025, 9, 22),
                            "Exemption list - everything at nil. Supersedes 02/2017."),
                    new Known("19/2025-Central Tax (Rate)", LocalDate.of(2025, 12, 31),
                            LocalDate.of(2026, 2, 1),
                            "Pan masala and tobacco to 40%, biris at 18%, Schedule VII omitted."),
                    new Known("01/2026-Central Tax (Rate)", LocalDate.of(2026, 4, 30),
                            LocalDate.of(2026, 5, 1),
                            "Beverage headings under 2202 re-cut into eight-digit codes."));

            for (Known k : known) {
                if (sourceRepo.findByNotificationNumber(k.number()).isPresent()) continue;
                sourceRepo.save(new GstRateSource(k.number(), k.dated(), k.effective(), k.what()));
            }
        } catch (Exception e) {
            // Reference data must never stop the application starting.
            log.error("Could not seed the GST rate sources: {}", e.getMessage(), e);
        }
    }

    /** The newest notification the rates are built from. */
    public Optional<GstRateSource> newestApplied() {
        return sourceRepo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getApplied()))
                .filter(s -> s.getEffectiveFrom() != null)
                .max(Comparator.comparing(GstRateSource::getEffectiveFrom));
    }

    /** The most recent time anyone confirmed the rates against CBIC. */
    public Optional<LocalDateTime> lastVerifiedAt() {
        return sourceRepo.findAll().stream()
                .map(GstRateSource::getLastVerifiedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder());
    }

    /**
     * How stale the verification is.
     *
     * NEVER_VERIFIED is deliberately distinct from OVERDUE. "Nobody has ever checked" and
     * "somebody checked, a while ago" call for different responses, and collapsing them would
     * hide a store that has never been verified at all behind a routine-looking reminder.
     */
    public Map<String, Object> status() {
        Optional<GstRateSource> newest = newestApplied();
        Optional<LocalDateTime> verified = lastVerifiedAt();

        String status;
        Long daysSince = null;
        if (verified.isEmpty()) {
            status = STATUS_NEVER;
        } else {
            daysSince = ChronoUnit.DAYS.between(verified.get(), LocalDateTime.now());
            status = daysSince >= overdueAfterDays ? STATUS_OVERDUE
                    : daysSince >= dueAfterDays ? STATUS_DUE
                    : STATUS_CURRENT;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status);
        out.put("daysSinceVerified", daysSince);
        out.put("dueAfterDays", dueAfterDays);
        out.put("overdueAfterDays", overdueAfterDays);
        out.put("lastVerifiedAt", verified.orElse(null));
        out.put("lastVerifiedBy", sourceRepo.findAll().stream()
                .filter(s -> s.getLastVerifiedAt() != null)
                .max(Comparator.comparing(GstRateSource::getLastVerifiedAt))
                .map(GstRateSource::getLastVerifiedBy).orElse(null));
        out.put("newestNotification", newest.map(GstRateSource::getNotificationNumber).orElse(null));
        out.put("newestEffectiveFrom", newest.map(GstRateSource::getEffectiveFrom).orElse(null));
        out.put("message", message(status, daysSince, newest));
        out.put("checkAt", "https://taxinformation.cbic.gov.in — GST, Notifications, Central Tax (Rate)");
        out.put("sources", new ArrayList<>(sourceRepo.findAll()));
        return out;
    }

    private String message(String status, Long daysSince, Optional<GstRateSource> newest) {
        String applied = newest.map(s -> "The newest notification applied is "
                        + s.getNotificationNumber() + ", effective " + s.getEffectiveFrom() + ". ")
                .orElse("No rate notification is recorded as applied. ");
        switch (status) {
            case STATUS_NEVER:
                return applied + "Nobody has confirmed these are still current. Check CBIC for any "
                        + "Central Tax (Rate) notification issued since, then record the check here.";
            case STATUS_OVERDUE:
                return applied + "The last check was " + daysSince + " days ago, which is past the "
                        + overdueAfterDays + "-day limit. A rate change published since then would "
                        + "be on every invoice raised since.";
            case STATUS_DUE:
                return applied + "The last check was " + daysSince + " days ago and is due again.";
            default:
                return applied + "Checked " + daysSince + " day(s) ago.";
        }
    }

    /**
     * Records that a human has checked CBIC and found nothing newer.
     *
     * Stamped on every applied source rather than on one, so the record reads "these rates, as a
     * set, were confirmed on this date" - which is the claim actually being made.
     */
    @Transactional
    public Map<String, Object> recordVerification(String verifiedBy, String note) {
        if (verifiedBy == null || verifiedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "A verification has to be attributed to someone - an unattributed check is "
                            + "not evidence of anything.");
        }
        LocalDateTime now = LocalDateTime.now();
        List<GstRateSource> applied = sourceRepo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getApplied()))
                .toList();
        for (GstRateSource s : applied) {
            s.setLastVerifiedAt(now);
            s.setLastVerifiedBy(verifiedBy);
        }
        sourceRepo.saveAll(applied);

        auditService.log(null, verifiedBy, "GST_RATES_VERIFIED", "GstRateSource", null,
                "Confirmed against CBIC that no newer Central Tax (Rate) notification applies"
                        + (note != null && !note.isBlank() ? ": " + note : ""), null);
        log.info("GST rates confirmed current by {}", verifiedBy);
        return status();
    }

    /**
     * Records a notification found at CBIC that is NOT yet in the rate seed.
     *
     * Stored as applied=false so it shows as an outstanding change rather than pretending the
     * rates already reflect it. Applying it is a seed regeneration, which is deliberate work.
     */
    @Transactional
    public GstRateSource recordPendingNotification(String number, LocalDate dated,
                                                   LocalDate effective, String description) {
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException("The notification number is required.");
        }
        GstRateSource existing = sourceRepo.findByNotificationNumber(number).orElse(null);
        if (existing != null) return existing;

        GstRateSource pending = new GstRateSource(number, dated, effective, description);
        pending.setApplied(false);
        log.warn("A GST rate notification not yet applied has been recorded: {} (effective {}). "
                + "The rate seed needs regenerating before it takes effect.", number, effective);
        return sourceRepo.save(pending);
    }

    /** Notifications recorded as outstanding - published, but not yet in the rate table. */
    public List<GstRateSource> pendingNotifications() {
        return sourceRepo.findAll().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getApplied()))
                .toList();
    }

    /**
     * The daily chase.
     *
     * Logs at a level that matches the state, so an overdue check shows up in whatever the host
     * surfaces as errors rather than sitting in a log nobody reads. It changes nothing - the
     * point is to make the gap visible, not to act on a rate nobody has confirmed.
     */
    @Scheduled(cron = "${gst.rate-verification.cron:0 30 6 * * ?}")
    public void chaseVerification() {
        try {
            Map<String, Object> status = status();
            String state = String.valueOf(status.get("status"));
            List<GstRateSource> pending = pendingNotifications();

            if (!pending.isEmpty()) {
                log.error("GST COMPLIANCE: {} rate notification(s) are published but not applied "
                                + "to the rate table: {}. Invoices are being raised at superseded "
                                + "rates.", pending.size(),
                        pending.stream().map(GstRateSource::getNotificationNumber).toList());
            }
            if (STATUS_OVERDUE.equals(state) || STATUS_NEVER.equals(state)) {
                log.error("GST COMPLIANCE: {}", status.get("message"));
            } else if (STATUS_DUE.equals(state)) {
                log.warn("GST rate verification is due: {}", status.get("message"));
            } else {
                log.info("GST rate verification is current ({} day(s) old).",
                        status.get("daysSinceVerified"));
            }
        } catch (Exception e) {
            log.error("The GST rate verification check failed to run: {}", e.getMessage(), e);
        }
    }
}
