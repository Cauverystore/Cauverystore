package com.cauverystore.controller;

import com.cauverystore.service.HsnClassificationService;
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

    public HsnController(HsnClassificationService service) {
        this.service = service;
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
