package com.cauverystore.controller;

import com.cauverystore.entities.TradeSynonym;
import com.cauverystore.service.HsnClassificationService;
import com.cauverystore.service.ProductTermMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Lookup for the HSN picker on the product form.
 *
 * Open to anyone who can list a product, since that is exactly who has to classify one. The
 * data is a public government code list, so there is nothing here to protect - the reason it
 * is behind auth at all is that it is only ever used from the seller and admin screens.
 */
@RestController
@RequestMapping("/api/hsn")
@PreAuthorize("hasAnyRole('SELLER', 'ADMIN', 'EXECUTIVE', 'SUPER_ADMIN')")
public class HsnController {

    private final HsnClassificationService service;
    private final ProductTermMapper termMapper;

    public HsnController(HsnClassificationService service, ProductTermMapper termMapper) {
        this.service = service;
        this.termMapper = termMapper;
    }

    /**
     * Maps a plain product description to the published codes for it, each with its tax.
     *
     * "Cotton Lungi" comes back as the cotton lungi headings with 5% against them, rather than
     * as a code the seller has to look up the consequences of separately.
     */
    @GetMapping("/map")
    public ResponseEntity<Map<String, Object>> mapProduct(
            @RequestParam("name") String productName,
            @RequestParam(required = false) Double unitPrice,
            @RequestParam(required = false) Boolean prePackaged) {
        return ResponseEntity.ok(termMapper.map(productName, unitPrice, prePackaged));
    }

    /** The translations the store asserts, for review. */
    @GetMapping("/synonyms")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<TradeSynonym>> synonyms() {
        return ResponseEntity.ok(termMapper.synonyms());
    }

    /**
     * Records that a word sellers use means a word the tariff uses.
     *
     * Admin-only, and attributed: this asserts what goods are, which is not a seller's call to
     * make for the whole store.
     */
    @PostMapping("/synonyms")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<TradeSynonym> addSynonym(@RequestBody Map<String, String> body,
                                                   java.security.Principal principal) {
        return ResponseEntity.ok(termMapper.addSynonym(
                body.get("term"), body.get("officialTerm"), body.get("note"),
                principal != null ? principal.getName() : null));
    }

    /**
     * The chapter list, for browsing down rather than searching across.
     *
     * Search only helps a seller who can already name their goods the tariff's way. Browsing
     * gives everyone else somewhere to start.
     */
    @GetMapping("/chapters")
    public ResponseEntity<List<Map<String, Object>>> chapters() {
        return ResponseEntity.ok(service.chapters());
    }

    /** The four-digit headings inside one chapter - the second step of browsing. */
    @GetMapping("/chapters/{chapter}/headings")
    public ResponseEntity<List<Map<String, Object>>> headings(@PathVariable String chapter) {
        return ResponseEntity.ok(service.headingsInChapter(chapter));
    }

    /** Search the official master by code prefix or by words in the description. */
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(service.search(query));
    }

    /**
     * Codes already used for this category, most-chosen first - so classifying the second bag
     * of rice is a click rather than a fresh judgement call.
     */
    @GetMapping("/suggestions")
    public ResponseEntity<List<Map<String, Object>>> suggestions(
            @RequestParam(value = "categoryId", required = false) Long categoryId) {
        return ResponseEntity.ok(service.suggestionsFor(categoryId));
    }

    /**
     * The published rate lines to choose between for a code, in the notification's own words.
     *
     * Empty when there is nothing to ask - one rate applies, or price and packaging already
     * settle it. The form only puts the question when it genuinely cannot be answered for the
     * seller.
     */
    @GetMapping("/rate-options")
    public ResponseEntity<List<Map<String, Object>>> rateOptions(
            @RequestParam("hsnCode") String hsnCode,
            @RequestParam(required = false) Double unitPrice,
            @RequestParam(required = false) Boolean prePackaged) {
        return ResponseEntity.ok(service.rateOptionsFor(hsnCode, unitPrice, prePackaged));
    }

    /**
     * The rate the master will actually apply to these goods, for the form's live preview.
     *
     * Uses the same walk as the invoice, so what the seller sees is what will be charged.
     * Empty map when the code cannot be resolved yet - the rate-options question is what stands
     * in the way, and the form shows that instead.
     */
    @GetMapping("/rate-preview")
    public ResponseEntity<Map<String, Object>> ratePreview(
            @RequestParam("hsnCode") String hsnCode,
            @RequestParam(required = false) Double unitPrice,
            @RequestParam(required = false) Boolean prePackaged) {
        return ResponseEntity.ok(service.ratePreview(hsnCode, unitPrice, prePackaged));
    }

    /** The government's description of one code, for showing next to an already-saved product. */
    @GetMapping("/{hsnCode}")
    public ResponseEntity<Map<String, Object>> describe(@PathVariable String hsnCode) {
        return service.describeCode(hsnCode)
                .map(desc -> ResponseEntity.ok(Map.<String, Object>of(
                        "hsnCode", hsnCode, "description", desc)))
                .orElseGet(() -> ResponseEntity.badRequest().body(Map.<String, Object>of(
                        "hsnCode", hsnCode,
                        "error", "Not an HSN code in the official GSTN master.")));
    }
}
