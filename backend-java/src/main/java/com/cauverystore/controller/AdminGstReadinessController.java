package com.cauverystore.controller;

import com.cauverystore.service.GstComplianceReadinessService;
import com.cauverystore.service.CbicNotificationDetector;
import com.cauverystore.service.GstRateFreshnessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Whether the store can charge correct tax on everything it sells.
 *
 * There is no fallback rate and no switch to relax the rules, so a product the system cannot
 * tax will refuse to sell. This is how a seller finds those before a customer does.
 */
@RestController
@RequestMapping("/api/admin/gst")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SELLER')")
public class AdminGstReadinessController {

    private final GstComplianceReadinessService service;
    private final GstRateFreshnessService freshnessService;
    private final CbicNotificationDetector detector;

    public AdminGstReadinessController(GstComplianceReadinessService service,
                                       GstRateFreshnessService freshnessService,
                                       CbicNotificationDetector detector) {
        this.service = service;
        this.freshnessService = freshnessService;
        this.detector = detector;
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> readiness() {
        return ResponseEntity.ok(service.readiness());
    }

    /**
     * Which unreviewed headings are actually holding the shop up, busiest first.
     *
     * The review queue is 357 rates, which is a project. This is the short list that matters:
     * only headings something on sale resolves through, ordered by how many products each one
     * would release.
     */
    @GetMapping("/readiness/review-priority")
    public ResponseEntity<List<Map<String, Object>>> reviewPriority() {
        return ResponseEntity.ok(service.reviewPriority());
    }

    /** Just the products that cannot be sold, with the fix for each. */
    @GetMapping("/readiness/blocking-products")
    public ResponseEntity<List<GstComplianceReadinessService.Blocker>> blocking() {
        return ResponseEntity.ok(service.blockingProducts());
    }

    /**
     * How current the rates are: which notification they are built from, when a human last
     * confirmed nothing newer exists, and how stale that is.
     */
    /**
     * What CBIC is publishing right now, and which of it we already know about.
     *
     * Reads the portal live so the check can be proved to still work. A detector that silently
     * stopped watching looks exactly like one reporting all-clear, and this is how to tell the
     * difference without waiting for the next notification.
     */
    @GetMapping("/cbic-updates")
    public ResponseEntity<Map<String, Object>> cbicUpdates() {
        return ResponseEntity.ok(detector.preview());
    }

    /** Runs the CBIC check immediately rather than waiting for the overnight run. */
    @PostMapping("/cbic-updates/check")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> runCbicCheck() {
        detector.detect();
        return ResponseEntity.ok(Map.of(
                "checked", true,
                "outstanding", detector.outstanding()));
    }

    @GetMapping("/rate-freshness")
    public ResponseEntity<Map<String, Object>> rateFreshness() {
        return ResponseEntity.ok(freshnessService.status());
    }

    /**
     * Records that someone has checked CBIC and found no newer Central Tax (Rate) notification.
     *
     * This is the only way the clock resets, and it stays a named, dated human act even now
     * that CbicNotificationDetector watches the portal automatically. The detector can say a
     * notification exists; only a person can say they have read it and that the rates charged
     * here still match it.
     */
    @PostMapping("/rate-freshness/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> verifyRates(@RequestBody(required = false) Map<String, String> body) {
        String by = currentUserEmail();
        String note = body == null ? null : body.get("note");
        try {
            return ResponseEntity.ok(freshnessService.recordVerification(by, note));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Records a notification seen at CBIC that the rate seed does not yet reflect. */
    @PostMapping("/rate-freshness/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> recordPending(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(freshnessService.recordPendingNotification(
                    body.get("notificationNumber"),
                    parseDate(body.get("notificationDate")),
                    parseDate(body.get("effectiveFrom")),
                    body.get("description")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private java.time.LocalDate parseDate(String raw) {
        return raw == null || raw.isBlank() ? null : java.time.LocalDate.parse(raw);
    }

    private String currentUserEmail() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    /**
     * Invoices raised at the old fallback rate, before it was removed. They carry a figure
     * that was never a lawful rate for those goods, and each needs a credit note.
     */
    @GetMapping("/readiness/fallback-invoices")
    public ResponseEntity<List<Map<String, Object>>> fallbackInvoices() {
        return ResponseEntity.ok(service.invoicesTaxedByFallback());
    }
}
