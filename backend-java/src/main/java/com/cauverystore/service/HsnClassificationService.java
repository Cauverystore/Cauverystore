package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.HsnAssignment;
import com.cauverystore.entities.HsnMaster;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.GstRateMasterRepository;
import com.cauverystore.repository.HsnAssignmentRepository;
import com.cauverystore.repository.HsnMasterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps product classification honest.
 *
 * Two separate things decide what tax a product attracts, and only one of them is the
 * platform's to know. What rate a code attracts comes from the CBIC notifications and is never
 * guessed (see GstRateResolver). Which code a product belongs under is judgement about the
 * goods themselves, which only the seller has - so this does not try to infer it.
 *
 * What it does do is stop the two failure modes that make a wrong classification invisible:
 *
 *   a code that does not exist  - previously accepted silently, then resolved to nothing, so
 *                                 the product was taxed at the fallback rate forever while the
 *                                 invoice looked perfectly normal. Now refused outright.
 *   a code nobody can check     - a bare number tells a reviewer nothing, so every code is
 *                                 offered with the government's own description of it.
 *
 * And because classification repeats - the second bag of rice belongs where the first went -
 * every assignment is remembered against its category and offered back next time.
 */
@Service
public class HsnClassificationService {

    private static final Logger log = LoggerFactory.getLogger(HsnClassificationService.class);

    private static final int MAX_SEARCH_RESULTS = 30;

    private final HsnMasterRepository hsnRepo;
    private final HsnAssignmentRepository assignmentRepo;
    private final GstRateMasterRepository rateRepo;
    private final GstRateResolver rateResolver;

    public HsnClassificationService(HsnMasterRepository hsnRepo,
                                    HsnAssignmentRepository assignmentRepo,
                                    GstRateMasterRepository rateRepo,
                                    GstRateResolver rateResolver) {
        this.hsnRepo = hsnRepo;
        this.assignmentRepo = assignmentRepo;
        this.rateRepo = rateRepo;
        this.rateResolver = rateResolver;
    }

    /** Thrown when a product carries a code that is not in the official master. */
    public static class UnknownHsnException extends RuntimeException {
        public UnknownHsnException(String message) { super(message); }
    }

    /** Thrown when a product would go on sale without a determinable GST rate. */
    public static class UnclassifiedProductException extends RuntimeException {
        public UnclassifiedProductException(String message) { super(message); }
    }

    /**
     * The published rate lines a seller has to choose between for a code.
     *
     * Returned with the notification's own wording, because the choice is only answerable in
     * those terms - "Coffee roasted" against "Coffee beans, not roasted" is a question anyone
     * can answer about their own stock, whereas "5% or nil?" is not.
     *
     * Empty when the heading is not ambiguous: either one rate applies, or the resolver can
     * already settle it from the price or the packaging, and there is nothing to ask.
     */
    public List<Map<String, Object>> rateOptionsFor(String hsnCode, Double unitPrice,
                                                    Boolean prePackaged) {
        if (hsnCode == null || hsnCode.isBlank()) return List.of();
        LocalDate today = LocalDate.now();
        if (rateResolver.findRate(hsnCode, today, unitPrice, prePackaged).isPresent()) {
            return List.of();   // already decidable - do not ask a question with one answer
        }

        String hsn = normalise(hsnCode);
        for (String candidate : List.of(hsn,
                hsn.length() > 6 ? hsn.substring(0, 6) : hsn,
                hsn.length() > 4 ? hsn.substring(0, 4) : hsn,
                hsn.length() > 2 ? hsn.substring(0, 2) : hsn)) {
            List<GstRateMaster> rows = rateRepo.findByHsnCodeOrderByEffectiveFromDesc(candidate)
                    .stream()
                    .filter(r -> !r.getEffectiveFrom().isAfter(today))
                    .filter(r -> r.getEffectiveTo() == null || !r.getEffectiveTo().isBefore(today))
                    .toList();
            if (rows.size() < 2) continue;

            List<Map<String, Object>> options = new ArrayList<>();
            for (GstRateMaster r : rows) {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("rateId", r.getId());
                option.put("gstRate", r.getGstRate());
                option.put("hsnCode", r.getHsnCode());
                option.put("description", r.getConditionText());
                option.put("source", r.getSource());
                options.add(option);
            }
            return options;
        }
        return List.of();
    }

    /** Codes matching a code prefix or a word in the description. */
    public List<Map<String, Object>> search(String query) {
        if (query == null || query.trim().length() < 2) return List.of();
        return hsnRepo.search(query.trim(), PageRequest.of(0, MAX_SEARCH_RESULTS))
                .stream().map(this::describe).toList();
    }

    /**
     * Codes already used in this category, most-chosen first, so the common case is one click.
     * Falls back to the store's most-used codes when the product has no category yet.
     */
    public List<Map<String, Object>> suggestionsFor(Long categoryId) {
        List<HsnAssignment> used = categoryId != null
                ? assignmentRepo.findByCategoryIdOrderByTimesUsedDescLastUsedAtDesc(categoryId)
                : assignmentRepo.findTop10ByOrderByTimesUsedDescLastUsedAtDesc();

        List<Map<String, Object>> out = new ArrayList<>();
        for (HsnAssignment a : used) {
            Map<String, Object> row = describe(hsnRepo.findById(a.getHsnCode()).orElse(null));
            if (row.isEmpty()) continue;
            row.put("timesUsed", a.getTimesUsed());
            row.put("lastUsedBy", a.getLastUsedBy());
            out.add(row);
        }
        return out;
    }

    /** The official description of one code, or empty if it is not a real code. */
    public Optional<String> describeCode(String hsnCode) {
        if (hsnCode == null || hsnCode.isBlank()) return Optional.empty();
        return hsnRepo.findById(normalise(hsnCode)).map(HsnMaster::getDescription);
    }

    /**
     * Rejects a product whose HSN is not in the official master.
     *
     * A blank code is allowed through: plenty of the catalogue predates any of this, and
     * refusing to save an existing product because of it would be worse than the missing code.
     * Such products resolve to the fallback rate, which is logged and visible on the GST screen.
     */
    public void validate(Product product) {
        if (product == null) return;
        String code = product.getHsnCode();
        if (code == null || code.isBlank()) return;

        String normalised = normalise(code);
        if (!hsnRepo.existsById(normalised)) {
            throw new UnknownHsnException(
                    "'" + code + "' is not an HSN code in the official GSTN master. A code that "
                            + "does not exist cannot be matched to a published rate, so the product "
                            + "would be taxed at the fallback rate on every sale. Search for the "
                            + "goods by name and pick the code that describes them.");
        }
        product.setHsnCode(normalised);
    }

    /**
     * Refuses to put a product on sale when its GST rate cannot be determined.
     *
     * There is no provisional rate in GST. Every invoice states an HSN and the rate that
     * applies, so "decide later" is not a position the law offers - and charging a guessed
     * rate is its own breach either way: collect too much and it still has to be paid over
     * while the customer was overcharged, collect too little and the shortfall carries
     * interest. The only state in which no wrong invoice is issued is not selling yet.
     *
     * So an unclassifiable product stays a draft. It is not blocked from being saved - the
     * seller keeps their work - it simply cannot be published until the question is answered.
     * This is not configurable: a switch to disable it would be a switch to sell goods the
     * system knows it cannot tax correctly.
     */
    public void assertSellable(Product product) {
        if (product == null) return;
        if (!"published".equalsIgnoreCase(product.getProductStatus())) return;   // drafts are fine

        String hsn = product.getHsnCode();
        if (hsn == null || hsn.isBlank()) {
            throw new UnclassifiedProductException(
                    "This product has no HSN code, so there is no lawful rate to charge on it. "
                            + "Pick the code that describes the goods, then publish. It can stay a "
                            + "draft in the meantime.");
        }
        if (product.getGstRateSelectionId() != null) return;   // the seller has answered

        Double unitPrice = product.getOfferPrice() != null ? product.getOfferPrice() : product.getPrice();
        if (rateResolver.findRate(hsn, LocalDate.now(), unitPrice,
                product.getPrePackagedAndLabelled()).isPresent()) {
            return;
        }
        throw new UnclassifiedProductException(
                "HSN " + hsn + " carries more than one published rate, and nothing on this "
                        + "product says which describes it. Choose the wording that matches the "
                        + "goods and publish again - or leave it as a draft and come back to it. "
                        + "It cannot go on sale until then, because every invoice has to state a "
                        + "rate that is actually correct for what was sold.");
    }

    /**
     * Remembers that this category was classified under this code, so it leads the suggestions
     * next time. Never allowed to fail a product save - a lost suggestion costs a seller one
     * search, whereas a failed save costs them their work.
     */
    @Transactional
    public void rememberAssignment(Product product, String by) {
        if (product == null || product.getHsnCode() == null || product.getHsnCode().isBlank()) return;
        try {
            Long categoryId = product.getCategory() != null ? product.getCategory().getId() : null;
            String code = normalise(product.getHsnCode());

            HsnAssignment assignment = assignmentRepo
                    .findByCategoryIdAndHsnCode(categoryId, code)
                    .orElseGet(() -> {
                        HsnAssignment fresh = new HsnAssignment();
                        fresh.setCategoryId(categoryId);
                        fresh.setHsnCode(code);
                        return fresh;
                    });
            assignment.recordUse(by);
            assignmentRepo.save(assignment);
        } catch (Exception e) {
            log.warn("Could not record HSN assignment for product {}: {}",
                    product.getId(), e.getMessage());
        }
    }

    private Map<String, Object> describe(HsnMaster hsn) {
        if (hsn == null) return Map.of();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hsnCode", hsn.getHsnCode());
        row.put("description", hsn.getDescription());
        row.put("chapter", hsn.getChapter());
        row.put("digits", hsn.getDigits());
        return row;
    }

    private String normalise(String code) {
        return code == null ? null : code.replaceAll("\\s", "");
    }
}
