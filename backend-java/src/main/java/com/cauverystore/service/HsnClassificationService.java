package com.cauverystore.service;

import com.cauverystore.entities.HsnAssignment;
import com.cauverystore.entities.HsnMaster;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.HsnAssignmentRepository;
import com.cauverystore.repository.HsnMasterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public HsnClassificationService(HsnMasterRepository hsnRepo,
                                    HsnAssignmentRepository assignmentRepo) {
        this.hsnRepo = hsnRepo;
        this.assignmentRepo = assignmentRepo;
    }

    /** Thrown when a product carries a code that is not in the official master. */
    public static class UnknownHsnException extends RuntimeException {
        public UnknownHsnException(String message) { super(message); }
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
