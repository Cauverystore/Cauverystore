package com.cauverystore.controller;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.MasterUpdateLog;
import com.cauverystore.service.GstRateAdminService;
import com.cauverystore.service.GstMasterDataLoader;
import com.cauverystore.service.GstRateImportService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Review desk for the GST rate master.
 *
 * Rates the importer could not approve on its own sit here until someone reads the
 * notification and decides. Restricted to admins because approving a rate here is what
 * causes it to be charged to customers and declared on returns.
 */
@RestController
@RequestMapping("/api/admin/gst-rates")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminGstRateController {

    private final GstRateAdminService service;
    private final GstRateImportService importService;
    private final GstMasterDataLoader loader;

    public AdminGstRateController(GstRateAdminService service, GstRateImportService importService,
                                  GstMasterDataLoader loader) {
        this.service = service;
        this.importService = importService;
        this.loader = loader;
    }

    /**
     * Imports rates from a spreadsheet transcribed from a CBIC notification.
     *
     * Everything lands UNVERIFIED and appears on the review desk below. A transcription is not
     * evidence, so nothing uploaded here is charged to a customer until a second person has
     * checked it against the notification itself.
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importRates(
            @RequestParam("file") MultipartFile file,
            @RequestParam("notification") String notification,
            java.security.Principal principal) {
        return ResponseEntity.ok(importService.importRates(file, notification,
                principal != null ? principal.getName() : null));
    }

    /** How much is verified, how much is still waiting, and when the last import ran. */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(service.summary());
    }

    /** Headings awaiting review, each with all its rates and what makes it undecidable. */
    @GetMapping("/review")
    public ResponseEntity<List<Map<String, Object>>> review(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(service.listForReview(status));
    }

    /** Full rate history for one heading, including superseded rows. */
    @GetMapping("/heading/{hsnCode}")
    public ResponseEntity<Map<String, Object>> heading(@PathVariable String hsnCode) {
        return ResponseEntity.ok(service.getHeading(hsnCode));
    }

    /**
     * What is actually in the database after the last startup load.
     *
     * One call instead of reading the host's logs. The number to look at is
     * chapterRowsMarkedWholeChapter: if the backfill did not run, apparel stops resolving and
     * nothing else on this report would tell you.
     */
    @GetMapping("/data-state")
    public ResponseEntity<Map<String, Object>> dataState() {
        return ResponseEntity.ok(loader.state());
    }

    @GetMapping("/imports")
    public ResponseEntity<List<MasterUpdateLog>> imports() {
        return ResponseEntity.ok(service.importHistory());
    }

    /** Re-applies the committed GST master files (HSN, units, states, rates) to the database. */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        return ResponseEntity.ok(service.refreshMaster());
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<GstRateMaster> verify(@PathVariable Long id,
                                                @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(service.verify(id, body == null ? null : body.get("note")));
    }

    @PostMapping("/{id}/unverify")
    public ResponseEntity<GstRateMaster> unverify(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(service.unverify(id, body == null ? null : body.get("reason")));
    }

    /** Close a rate off on its last day in force, so a later notification can take over. */
    @PostMapping("/{id}/supersede")
    public ResponseEntity<GstRateMaster> supersede(@PathVariable Long id,
                                                   @RequestBody Map<String, String> body) {
        String date = body == null ? null : body.get("lastDayInForce");
        return ResponseEntity.ok(service.supersede(id, date == null ? null : LocalDate.parse(date)));
    }

    @PostMapping
    public ResponseEntity<GstRateMaster> create(@RequestBody GstRateMaster rate) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(rate));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GstRateMaster> update(@PathVariable Long id,
                                                @RequestBody GstRateMaster rate) {
        return ResponseEntity.ok(service.update(id, rate));
    }

    /**
     * Review failures are the reviewer's own input being rejected (a rate that would leave the
     * table unusable, a missing reason), so they come back as 400 with the explanation intact
     * rather than as an opaque 500.
     */
    @ExceptionHandler(GstRateAdminService.RateReviewException.class)
    public ResponseEntity<Map<String, String>> handleReviewError(
            GstRateAdminService.RateReviewException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
