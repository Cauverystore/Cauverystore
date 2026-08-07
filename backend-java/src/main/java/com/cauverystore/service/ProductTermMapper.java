package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.HsnMaster;
import com.cauverystore.entities.TradeSynonym;
import com.cauverystore.repository.HsnMasterRepository;
import com.cauverystore.repository.TradeSynonymRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns what a seller calls their product into the codes the tariff calls it, and the tax each
 * one carries.
 *
 * <h2>Why a seller needs this and search was not enough</h2>
 *
 * The picker could already search the official master, but only for someone who can already
 * name their goods the tariff's way. Typing "Cotton Lungi" matched nothing useful, because the
 * tariff spreads that across several codes by weave, weight and whether it is handloom, and
 * says nothing about the tax until the code is chosen. So the seller saw codes without
 * consequences, and the one number they actually understand - what tax will be charged - only
 * appeared after the product was saved.
 *
 * <h2>What it will and will not decide</h2>
 *
 * It proposes; it never assigns. "Cotton Lungi" genuinely maps to more than one published code
 * - 52095111 for handloom, 52095119 otherwise, and different headings by fabric weight - and
 * only the seller knows which describes their stock. Picking one for them would be a guess
 * wearing the clothes of an answer.
 *
 * Every proposal is traceable. A code is offered because the seller's own word appears in the
 * government's description of it, or because a recorded translation says their word means the
 * tariff's word. Nothing here asserts a rate: the rate shown is whatever GstRateResolver makes
 * of the code, from the CBIC notifications, and where it cannot decide, that is shown too
 * rather than filled in.
 */
@Service
public class ProductTermMapper {

    /** Enough candidates to cover the real distinctions without becoming a list to scroll. */
    private static final int MAX_CANDIDATES = 12;

    /**
     * Words too common in tariff prose to narrow anything, plus ordinary English filler.
     * Matching on these returns half the master and buries the words that carry meaning.
     */
    private static final Set<String> NOISE = Set.of(
            "other", "others", "goods", "articles", "article", "parts", "part", "with", "without",
            "not", "and", "the", "for", "from", "made", "kind", "kinds", "type", "types", "such",
            "including", "included", "whether", "than", "more", "less", "used", "new",
            "set", "sets", "pack", "packet", "piece", "pieces", "size", "colour", "color",
            "quality", "premium", "best", "offer", "sale", "combo", "pcs", "nos");

    private final HsnMasterRepository hsnRepo;
    private final TradeSynonymRepository synonymRepo;
    private final GstRateResolver rateResolver;

    public ProductTermMapper(HsnMasterRepository hsnRepo,
                             TradeSynonymRepository synonymRepo,
                             GstRateResolver rateResolver) {
        this.hsnRepo = hsnRepo;
        this.synonymRepo = synonymRepo;
        this.rateResolver = rateResolver;
    }

    /**
     * Maps a product description to the codes that could describe it, each with its tax.
     *
     * @param productName what the seller calls it - "Cotton Lungi", "Veshti 2 metre"
     * @param unitPrice   needed wherever the rate turns on sale value, as it does for apparel;
     *                    without it those codes report that the price decides rather than
     *                    guessing a band
     * @param prePackaged needed for the staples whose rate turns on packaging
     */
    public Map<String, Object> map(String productName, Double unitPrice, Boolean prePackaged) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", productName);

        List<String> words = meaningfulWords(productName);
        out.put("termsUsed", words);
        if (words.isEmpty()) {
            out.put("candidates", List.of());
            out.put("message", "Describe the product in a word or two - \"lungi\", \"cotton saree\" "
                    + "- and the published codes for it will be listed with the tax each carries.");
            return out;
        }

        // A translation is applied to the word, not to the classification: the tariff's own word
        // is what gets searched, exactly as if the seller had typed it.
        List<Map<String, Object>> translations = new ArrayList<>();
        Set<String> searchWords = new LinkedHashSet<>(words);
        for (String word : words) {
            Optional<TradeSynonym> synonym = synonymRepo.findByTermIgnoreCase(word);
            if (synonym.isEmpty()) continue;
            searchWords.add(synonym.get().getOfficialTerm());
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("term", word);
            t.put("officialTerm", synonym.get().getOfficialTerm());
            t.put("note", synonym.get().getNote());
            t.put("addedBy", synonym.get().getAddedBy());
            translations.add(t);
        }
        out.put("translations", translations);

        // Score by how many of the seller's words a description contains. "Cotton Lungi" should
        // put the cotton lungi headings above every other lungi and every other cotton thing.
        Map<String, HsnMaster> found = new LinkedHashMap<>();
        Map<String, Set<String>> matchedOn = new LinkedHashMap<>();
        for (String word : searchWords) {
            for (HsnMaster row : hsnRepo.search(word, PageRequest.of(0, 80))) {
                if (!containsWord(row.getDescription(), word)) continue;   // code-prefix hit, not a word
                found.putIfAbsent(row.getHsnCode(), row);
                matchedOn.computeIfAbsent(row.getHsnCode(), k -> new LinkedHashSet<>()).add(word);
            }
        }

        List<Map<String, Object>> candidates = found.values().stream()
                .sorted(Comparator
                        // most of the seller's words matched first
                        .comparingInt((HsnMaster h) -> -matchedOn.get(h.getHsnCode()).size())
                        // then the most specific code, which is the one they should be using
                        .thenComparing(Comparator.comparingInt((HsnMaster h) -> -h.getHsnCode().length()))
                        .thenComparing(HsnMaster::getHsnCode))
                .limit(MAX_CANDIDATES)
                .map(h -> describe(h, matchedOn.get(h.getHsnCode()), unitPrice, prePackaged))
                .toList();

        out.put("candidates", candidates);
        out.put("message", candidates.isEmpty()
                ? "Nothing in the official master is described using those words. Try the goods' "
                  + "plainest name, or browse the tariff by trade."
                : "These are the published codes whose official description uses your words. "
                  + "Choose the one that actually describes your stock - only you can tell.");
        return out;
    }

    /** One candidate: the code, the government's wording, and what tax it would attract. */
    private Map<String, Object> describe(HsnMaster hsn, Set<String> matchedOn,
                                         Double unitPrice, Boolean prePackaged) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hsnCode", hsn.getHsnCode());
        row.put("description", hsn.getDescription());
        row.put("matchedOn", matchedOn);

        Optional<Double> rate =
                rateResolver.findRate(hsn.getHsnCode(), LocalDate.now(), unitPrice, prePackaged);
        if (rate.isPresent()) {
            row.put("gstRate", rate.get());
            row.put("taxable", true);
            row.put("rateNote", rateNote(hsn.getHsnCode(), unitPrice));
        } else {
            // Saying "no rate" would read as "no tax". It is the opposite: the rate exists in
            // law and this system cannot yet tell which one, so the code cannot be used until
            // that is settled.
            row.put("gstRate", null);
            row.put("taxable", false);
            row.put("rateNote", "No published rate can be settled for this code yet"
                    + (unitPrice == null ? ", and the rate here may depend on the sale price" : "")
                    + ". Picking it will hold the product back from sale until it is resolved.");
        }
        return row;
    }

    private String rateNote(String hsnCode, Double unitPrice) {
        if (unitPrice == null) return null;
        // Apparel and footwear are banded, so the same code is two different rates by price.
        String chapter = hsnCode.length() >= 2 ? hsnCode.substring(0, 2) : "";
        if (List.of("61", "62", "63", "64").contains(chapter)) {
            return "This rate is for a sale value of Rs " + unitPrice
                    + " per piece. Above Rs 2,500 the published rate is higher.";
        }
        return null;
    }

    /** Whether the word appears as a word, so "tea" does not match "steam" or "protease". */
    private boolean containsWord(String description, String word) {
        if (description == null) return false;
        return description.toLowerCase().matches(".*\\b" + java.util.regex.Pattern.quote(word) + ".*");
    }

    private List<String> meaningfulWords(String productName) {
        if (productName == null || productName.isBlank()) return List.of();
        return Arrays.stream(productName.toLowerCase().split("[^a-z]+"))
                .filter(w -> w.length() >= 3)
                .filter(w -> !NOISE.contains(w))
                .distinct()
                .limit(6)
                .toList();
    }

    /** The recorded translations, for an admin to review what the store is asserting. */
    public List<TradeSynonym> synonyms() {
        return synonymRepo.findAllByOrderByTermAsc();
    }

    /**
     * Records that a word sellers use means a word the tariff uses.
     *
     * The tariff's word has to actually appear in the official master. Without that check a
     * translation could point at nothing, and the seller who typed "veshti" would get the same
     * empty result as before while the store's records claimed the word was handled.
     */
    public TradeSynonym addSynonym(String term, String officialTerm, String note, String addedBy) {
        if (term == null || term.isBlank() || officialTerm == null || officialTerm.isBlank()) {
            throw new IllegalArgumentException("Both the seller's word and the tariff's word are required.");
        }
        if (addedBy == null || addedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "A translation has to be attributed to someone - it is an assertion about "
                            + "what goods are, and somebody has to stand behind it.");
        }
        String cleanTerm = term.trim().toLowerCase();
        String cleanOfficial = officialTerm.trim().toLowerCase();
        if (cleanTerm.equals(cleanOfficial)) {
            throw new IllegalArgumentException("A word cannot be a translation of itself.");
        }
        boolean known = hsnRepo.search(cleanOfficial, PageRequest.of(0, 5)).stream()
                .anyMatch(h -> containsWord(h.getDescription(), cleanOfficial));
        if (!known) {
            throw new IllegalArgumentException("\"" + officialTerm + "\" does not appear in any "
                    + "official HSN description, so pointing \"" + term + "\" at it would still "
                    + "find nothing. Use the word the tariff itself uses.");
        }
        return synonymRepo.findByTermIgnoreCase(cleanTerm).orElseGet(() ->
                synonymRepo.save(new TradeSynonym(cleanTerm, cleanOfficial, note, addedBy)));
    }
}
