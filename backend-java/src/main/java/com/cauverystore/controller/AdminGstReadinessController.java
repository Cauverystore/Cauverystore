package com.cauverystore.controller;

import com.cauverystore.service.GstComplianceReadinessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

    public AdminGstReadinessController(GstComplianceReadinessService service) {
        this.service = service;
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> readiness() {
        return ResponseEntity.ok(service.readiness());
    }

    /** Just the products that cannot be sold, with the fix for each. */
    @GetMapping("/readiness/blocking-products")
    public ResponseEntity<List<GstComplianceReadinessService.Blocker>> blocking() {
        return ResponseEntity.ok(service.blockingProducts());
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
