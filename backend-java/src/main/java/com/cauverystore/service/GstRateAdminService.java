package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.HsnMaster;
import com.cauverystore.entities.MasterUpdateLog;
import com.cauverystore.repository.GstRateMasterRepository;
import com.cauverystore.repository.HsnMasterRepository;
import com.cauverystore.repository.MasterUpdateLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Review workflow for GST rates.
 *
 * The importer approves only what the CBIC extract can justify on its own. Everything else -
 * a heading taxed differently by packaging, or one where only part of a value band was
 * published - is parked as UNVERIFIED and never charged. This is what a human uses to work
 * through that backlog.
 *
 * Two rules are enforced rather than left to the reviewer's care, because getting either
 * wrong is silent: a rate must not be approved if it would leave the resolver unable to
 * choose (which quietly falls back to the legacy rate), and approving is always attributed
 * to a named person with a timestamp, since that attribution is the audit trail.
 */
@Service
public class GstRateAdminService {

    private static final Logger log = LoggerFactory.getLogger(GstRateAdminService.class);

    private final GstRateMasterRepository rateRepo;
    private final HsnMasterRepository hsnRepo;
    private final MasterUpdateLogRepository logRepo;
    private final AuthorizationService authorizationService;
    private final GstMasterDataLoader masterDataLoader;

    public GstRateAdminService(GstRateMasterRepository rateRepo,
                               HsnMasterRepository hsnRepo,
                               MasterUpdateLogRepository logRepo,
                               AuthorizationService authorizationService,
                               GstMasterDataLoader masterDataLoader) {
        this.rateRepo = rateRepo;
        this.hsnRepo = hsnRepo;
        this.logRepo = logRepo;
        this.authorizationService = authorizationService;
        this.masterDataLoader = masterDataLoader;
    }

    private static final List<String> KNOWN_CONDITIONS = List.of(
            GstRateMaster.CONDITION_NONE,
            GstRateMaster.CONDITION_VALUE_UPTO, GstRateMaster.CONDITION_VALUE_ABOVE,
            GstRateMaster.CONDITION_PACKAGED, GstRateMaster.CONDITION_UNPACKAGED);

    /** Raised when a change would leave the rate table in a state the resolver cannot use. */
    public static class RateReviewException extends RuntimeException {
        public RateReviewException(String message) { super(message); }
    }

    // ---------------------------------------------------------------- reading

    public Map<String, Object> summary() {
        long verified = rateRepo.countByStatus(GstRateMaster.STATUS_VERIFIED);
        long unverified = rateRepo.countByStatus(GstRateMaster.STATUS_UNVERIFIED);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("verified", verified);
        out.put("unverified", unverified);
        out.put("headingsAwaitingReview", groupByHeading(
                rateRepo.findByStatusOrderByHsnCodeAsc(GstRateMaster.STATUS_UNVERIFIED)).size());

        List<MasterUpdateLog> logs = logRepo.findTop50ByOrderByCreatedAtDesc();
        out.put("lastImport", logs.isEmpty() ? null : logs.get(0));
        return out;
    }

    /**
     * Headings awaiting review, each with every rate published against it.
     *
     * Grouped deliberately: the reason a row needs review is almost always its siblings
     * (5% versus nil, or a band with no counterpart), so showing one row at a time would
     * hide the very thing the reviewer has to decide between.
     */
    public List<Map<String, Object>> listForReview(String status) {
        String wanted = status == null || status.isBlank()
                ? GstRateMaster.STATUS_UNVERIFIED : status.toUpperCase();
        Map<String, List<GstRateMaster>> byHeading =
                groupByHeading(rateRepo.findByStatusOrderByHsnCodeAsc(wanted));

        List<Map<String, Object>> out = new ArrayList<>();
        byHeading.forEach((hsn, rows) -> out.add(describeHeading(hsn, rows)));
        return out;
    }

    /** Every rate ever recorded for one heading, current and superseded. */
    public Map<String, Object> getHeading(String hsnCode) {
        List<GstRateMaster> rows = rateRepo.findByHsnCodeOrderByEffectiveFromDesc(hsnCode);
        return describeHeading(hsnCode, rows);
    }

    public List<MasterUpdateLog> importHistory() {
        return logRepo.findTop50ByOrderByCreatedAtDesc();
    }

    /**
     * Re-applies the committed master files (HSN, units, states, GST rates) to the database.
     *
     * The files themselves change only when a developer runs the fetch/parse tools and commits
     * the result, so this is how a freshly deployed master takes effect without waiting for a
     * restart. The load is insert-only and idempotent - rates an admin has since verified or
     * closed off are never overwritten - and every change is recorded in master_update_logs.
     * Returns the fresh summary and import history so the caller can show what actually moved.
     */
    public Map<String, Object> refreshMaster() {
        masterDataLoader.loadAll();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary());
        out.put("imports", importHistory());
        return out;
    }

    // ---------------------------------------------------------------- writing

    /**
     * Approves a rate for use in charging tax.
     *
     * Refuses when the heading would end up with more than one unconditional rate in force at
     * the same time, because the resolver cannot choose between them - it would give up and
     * fall back, so the approval would appear to work while quietly changing nothing. The
     * reviewer is told to close off the older row or give the rows a value condition instead.
     */
    @Transactional
    public GstRateMaster verify(Long id, String note) {
        GstRateMaster rate = require(id);
        assertNoConflictingSibling(rate);

        rate.setStatus(GstRateMaster.STATUS_VERIFIED);
        rate.setVerifiedBy(reviewer());
        rate.setVerifiedAt(LocalDateTime.now());
        if (note != null && !note.isBlank()) rate.setNotes(appendNote(rate.getNotes(), note));

        log.info("GST rate {} (HSN {} @ {}%) verified by {}",
                rate.getId(), rate.getHsnCode(), rate.getGstRate(), rate.getVerifiedBy());
        return rateRepo.save(rate);
    }

    /**
     * Withdraws approval, taking the rate out of use immediately.
     * A reason is required - an unexplained withdrawal is not reviewable later.
     */
    @Transactional
    public GstRateMaster unverify(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new RateReviewException("A reason is required when withdrawing a verified rate.");
        }
        GstRateMaster rate = require(id);
        rate.setStatus(GstRateMaster.STATUS_UNVERIFIED);
        rate.setVerifiedBy(null);
        rate.setVerifiedAt(null);
        rate.setNotes(appendNote(rate.getNotes(),
                "Withdrawn by " + reviewer() + " on " + LocalDate.now() + ": " + reason));

        log.warn("GST rate {} (HSN {}) withdrawn by {}: {}",
                rate.getId(), rate.getHsnCode(), reviewer(), reason);
        return rateRepo.save(rate);
    }

    /**
     * Adds a rate a reviewer has read off a notification - typically the missing half of a
     * value band, such as footwear above Rs 2500 per pair, which the extract never published.
     *
     * Arrives UNVERIFIED even when entered by an admin: entering a rate and approving it are
     * separate acts, so a typo has to get past a second look before it can charge anyone.
     */
    @Transactional
    public GstRateMaster create(GstRateMaster input) {
        if (input.getHsnCode() == null || input.getHsnCode().isBlank()) {
            throw new RateReviewException("HSN code is required.");
        }
        if (input.getGstRate() == null || input.getGstRate() < 0 || input.getGstRate() > 100) {
            throw new RateReviewException("GST rate must be between 0 and 100.");
        }
        if (input.getEffectiveFrom() == null) {
            throw new RateReviewException("An effective-from date is required - rates are dated so "
                    + "an old invoice can still be reproduced at the rate that applied to it.");
        }
        validateCondition(input);

        GstRateMaster rate = new GstRateMaster();
        rate.setHsnCode(input.getHsnCode().replaceAll("\\s", ""));
        rate.setGstRate(input.getGstRate());
        rate.setCessRate(input.getCessRate() == null ? 0.0 : input.getCessRate());
        rate.setEffectiveFrom(input.getEffectiveFrom());
        rate.setEffectiveTo(input.getEffectiveTo());
        rate.setConditionType(input.getConditionType() == null
                ? GstRateMaster.CONDITION_NONE : input.getConditionType());
        rate.setThresholdAmount(input.getThresholdAmount());
        rate.setThresholdUnit(input.getThresholdUnit());
        rate.setConditionText(input.getConditionText());
        rate.setSource(input.getSource());
        rate.setNotes(appendNote(input.getNotes(), "Entered by " + reviewer() + " on " + LocalDate.now()));
        rate.setStatus(GstRateMaster.STATUS_UNVERIFIED);

        return rateRepo.save(rate);
    }

    /**
     * Corrects a rate row. Any edit returns it to UNVERIFIED - an approval attests to the
     * values that were reviewed, so it cannot survive those values changing underneath it.
     */
    @Transactional
    public GstRateMaster update(Long id, GstRateMaster input) {
        GstRateMaster rate = require(id);

        if (input.getGstRate() != null) {
            if (input.getGstRate() < 0 || input.getGstRate() > 100) {
                throw new RateReviewException("GST rate must be between 0 and 100.");
            }
            rate.setGstRate(input.getGstRate());
        }
        if (input.getCessRate() != null) rate.setCessRate(input.getCessRate());
        if (input.getEffectiveFrom() != null) rate.setEffectiveFrom(input.getEffectiveFrom());
        // Null effectiveTo legitimately means "still in force", so it is always taken as given
        // rather than treated as "no change" - otherwise a row could never be reopened.
        rate.setEffectiveTo(input.getEffectiveTo());
        if (input.getConditionType() != null) rate.setConditionType(input.getConditionType());
        if (input.getThresholdAmount() != null) rate.setThresholdAmount(input.getThresholdAmount());
        if (input.getThresholdUnit() != null) rate.setThresholdUnit(input.getThresholdUnit());
        if (input.getConditionText() != null) rate.setConditionText(input.getConditionText());
        if (input.getSource() != null) rate.setSource(input.getSource());
        validateCondition(rate);

        rate.setStatus(GstRateMaster.STATUS_UNVERIFIED);
        rate.setVerifiedBy(null);
        rate.setVerifiedAt(null);
        rate.setNotes(appendNote(rate.getNotes(),
                "Edited by " + reviewer() + " on " + LocalDate.now() + "; needs re-approval."));

        return rateRepo.save(rate);
    }

    /**
     * Closes off a rate on the day before its replacement takes effect, which is how a
     * notification supersedes an earlier one without destroying it. Historical invoices still
     * resolve at the rate that applied on their own date.
     */
    @Transactional
    public GstRateMaster supersede(Long id, LocalDate lastDayInForce) {
        if (lastDayInForce == null) {
            throw new RateReviewException("A last-day-in-force date is required.");
        }
        GstRateMaster rate = require(id);
        if (lastDayInForce.isBefore(rate.getEffectiveFrom())) {
            throw new RateReviewException("A rate cannot stop applying before it started ("
                    + rate.getEffectiveFrom() + ").");
        }
        rate.setEffectiveTo(lastDayInForce);
        rate.setNotes(appendNote(rate.getNotes(),
                "Closed off by " + reviewer() + " on " + LocalDate.now()
                        + "; last day in force " + lastDayInForce + "."));
        return rateRepo.save(rate);
    }

    // ---------------------------------------------------------------- internals

    /**
     * Rejects an approval that would put two unconditional rates in force for one heading at
     * once. Value-banded rows are exempt: two bands are exactly how apparel is meant to work,
     * and the resolver tells them apart by price.
     */
    private void assertNoConflictingSibling(GstRateMaster candidate) {
        if (candidate.isConditional()) return;

        List<GstRateMaster> clashes = rateRepo
                .findByHsnCodeOrderByEffectiveFromDesc(candidate.getHsnCode()).stream()
                .filter(r -> !r.getId().equals(candidate.getId()))
                .filter(GstRateMaster::isVerified)
                .filter(r -> !r.isConditional())
                .filter(r -> overlaps(r, candidate))
                .toList();

        if (!clashes.isEmpty()) {
            GstRateMaster other = clashes.get(0);
            throw new RateReviewException(
                    "HSN " + candidate.getHsnCode() + " already has a verified " + other.getGstRate()
                            + "% rate in force over the same period (rate #" + other.getId() + "). "
                            + "Two unconditional rates on one heading leave nothing to choose between, "
                            + "so tax would silently fall back to the default instead. Close off the "
                            + "older rate, or give both rows a sale-value condition if the rate depends "
                            + "on price.");
        }
    }

    /** Whether two dated rows are both in force at any point in time. */
    private boolean overlaps(GstRateMaster a, GstRateMaster b) {
        LocalDate aEnd = a.getEffectiveTo() == null ? LocalDate.MAX : a.getEffectiveTo();
        LocalDate bEnd = b.getEffectiveTo() == null ? LocalDate.MAX : b.getEffectiveTo();
        return !a.getEffectiveFrom().isAfter(bEnd) && !b.getEffectiveFrom().isAfter(aEnd);
    }

    private void validateCondition(GstRateMaster rate) {
        String type = rate.getConditionType();
        if (type == null || GstRateMaster.CONDITION_NONE.equals(type)) return;

        if (!KNOWN_CONDITIONS.contains(type)) {
            throw new RateReviewException("Unknown condition type '" + type + "'. Use one of "
                    + String.join(", ", KNOWN_CONDITIONS) + ".");
        }
        // Only value bands carry a threshold; a packaging split is decided by the product's
        // own packaging flag and has nothing to compare against.
        if (rate.isValueBanded()
                && (rate.getThresholdAmount() == null || rate.getThresholdAmount() <= 0)) {
            throw new RateReviewException("A value-conditional rate needs a positive threshold "
                    + "amount (for example 2500 per piece).");
        }
    }

    /**
     * Flags what a reviewer needs to notice: a heading with two bands is decidable by price
     * and safe to approve; one with a single band, or several unconditional rates, is not.
     */
    private Map<String, Object> describeHeading(String hsnCode, List<GstRateMaster> rows) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hsnCode", hsnCode);
        out.put("description", hsnRepo.findById(hsnCode).map(HsnMaster::getDescription).orElse(null));
        out.put("rates", rows);

        long conditional = rows.stream().filter(GstRateMaster::isConditional).count();
        long unconditional = rows.size() - conditional;
        long packaging = rows.stream().filter(GstRateMaster::isPackagingBanded).count();

        String issue;
        if (packaging > 0 && conditional == packaging && unconditional == 0) {
            issue = packaging > 1
                    ? "Rate turns only on whether the goods are pre-packaged and labelled, which "
                      + "each product states, so the resolver chooses on its own."
                    : "Only one side of the packaging split is published, so nothing covers the "
                      + "other. Add the missing side before approving.";
        } else if (conditional > 0 && unconditional > 0) {
            issue = "This heading mixes a flat rate with value-banded rates. Decide which the "
                    + "goods actually attract and close off or remove the rest.";
        } else if (conditional == 1) {
            issue = "Only one side of the sale-value band is published, so nothing covers items on "
                    + "the other side of the threshold. Add the missing band before approving.";
        } else if (conditional > 1) {
            issue = "Value-banded rates: safe to approve together once the thresholds cover every "
                    + "price, since the rate is chosen from the item's selling price.";
        } else if (unconditional > 1) {
            issue = "Several flat rates on one heading - which applies depends on the goods "
                    + "themselves (packaging, composition), not the code. Approve only the one "
                    + "matching what is actually sold here.";
        } else {
            issue = "Single published rate; nothing to choose between.";
        }
        out.put("reviewNote", issue);
        return out;
    }

    private Map<String, List<GstRateMaster>> groupByHeading(List<GstRateMaster> rows) {
        Map<String, List<GstRateMaster>> byHeading = new TreeMap<>();
        for (GstRateMaster r : rows) {
            byHeading.computeIfAbsent(r.getHsnCode(), k -> new ArrayList<>()).add(r);
        }
        byHeading.values().forEach(l -> l.sort(Comparator.comparing(GstRateMaster::getGstRate)));
        return byHeading;
    }

    private GstRateMaster require(Long id) {
        return rateRepo.findById(id).orElseThrow(
                () -> new RateReviewException("No GST rate found with id " + id + "."));
    }

    private String reviewer() {
        String email = authorizationService.getCurrentUserEmail();
        return email == null || email.isBlank() ? "unknown admin" : email;
    }

    private String appendNote(String existing, String addition) {
        if (addition == null || addition.isBlank()) return existing;
        return (existing == null || existing.isBlank()) ? addition : existing + " | " + addition;
    }
}
