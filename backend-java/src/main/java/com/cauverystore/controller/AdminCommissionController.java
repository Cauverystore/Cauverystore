package com.cauverystore.controller;

import com.cauverystore.entities.CommissionInvoice;
import com.cauverystore.entities.CommissionRate;
import com.cauverystore.repository.CommissionInvoiceRepository;
import com.cauverystore.repository.CommissionRateRepository;
import com.cauverystore.service.CommissionInvoiceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The marketplace's commission: what it charges, and the invoices raising it.
 *
 * Restricted to admins because these are the platform's own outward supplies - issuing one
 * creates an output tax liability for the marketplace and an input credit for the seller.
 */
@RestController
@RequestMapping("/api/admin/commission")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminCommissionController {

    private final CommissionInvoiceService service;
    private final CommissionRateRepository rateRepo;
    private final CommissionInvoiceRepository invoiceRepo;

    public AdminCommissionController(CommissionInvoiceService service,
                                     CommissionRateRepository rateRepo,
                                     CommissionInvoiceRepository invoiceRepo) {
        this.service = service;
        this.rateRepo = rateRepo;
        this.invoiceRepo = invoiceRepo;
    }

    // ------------------------------------------------------------- rate card

    @GetMapping("/rates")
    public ResponseEntity<List<CommissionRate>> rates() {
        return ResponseEntity.ok(rateRepo.findAll());
    }

    /**
     * Adds a rate. Rates are never edited in place - a change is a new row, so what was billed
     * on invoices already issued stays reproducible.
     */
    @PostMapping("/rates")
    public ResponseEntity<?> addRate(@RequestBody CommissionRate rate) {
        if (rate.getRatePercent() == null || rate.getRatePercent() < 0 || rate.getRatePercent() > 100) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "The commission rate must be between 0 and 100 percent."));
        }
        if (rate.getEffectiveFrom() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "An effective-from date is required, so a rate change cannot "
                            + "silently restate commission already billed."));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(rateRepo.save(rate));
    }

    /** Closes a rate off on its last day, which is how a replacement takes over. */
    @PostMapping("/rates/{id}/close")
    public ResponseEntity<?> closeRate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return rateRepo.findById(id).map(rate -> {
            String last = body == null ? null : body.get("lastDayInForce");
            if (last == null || last.isBlank()) {
                return ResponseEntity.badRequest().body(Map.<String, Object>of(
                        "error", "A last-day-in-force date is required."));
            }
            LocalDate end = LocalDate.parse(last);
            if (end.isBefore(rate.getEffectiveFrom())) {
                return ResponseEntity.badRequest().body(Map.<String, Object>of(
                        "error", "A rate cannot stop applying before it started ("
                                + rate.getEffectiveFrom() + ")."));
            }
            rate.setEffectiveTo(end);
            return ResponseEntity.ok((Object) rateRepo.save(rate));
        }).orElse(ResponseEntity.badRequest().body(Map.of("error", "No commission rate with id " + id)));
    }

    /** What would be charged for a seller and category today, and why. */
    @GetMapping("/rates/resolve")
    public ResponseEntity<?> resolve(@RequestParam Long sellerId,
                                     @RequestParam(required = false) Long categoryId,
                                     @RequestParam(required = false) String on) {
        LocalDate date = on == null || on.isBlank() ? LocalDate.now() : LocalDate.parse(on);
        return service.resolveRate(sellerId, categoryId, date)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "error", "No commission rate applies to this seller and category on "
                                + date + ". Orders will be left unbilled rather than charged at "
                                + "zero - add a default rate to cover them.")));
    }

    // -------------------------------------------------------------- invoices

    @GetMapping("/invoices")
    public ResponseEntity<List<CommissionInvoice>> invoices(@RequestParam(required = false) String period) {
        return ResponseEntity.ok(period == null || period.isBlank()
                ? invoiceRepo.findAll()
                : invoiceRepo.findByPeriod(period));
    }

    /**
     * Bills one seller for a month. Safe to re-run - a period already invoiced comes back as
     * it stands rather than being billed twice.
     */
    @PostMapping("/invoices/generate")
    public ResponseEntity<?> generate(@RequestParam Long sellerId, @RequestParam String period) {
        return ResponseEntity.ok(service.generateForSeller(sellerId, period, currentUserEmail()));
    }

    /** Bills every seller with activity in the month, reporting who was skipped and why. */
    @PostMapping("/invoices/generate-period")
    public ResponseEntity<?> generatePeriod(@RequestParam String period) {
        return ResponseEntity.ok(service.generateForPeriod(period, currentUserEmail()));
    }

    /**
     * A refusal is the operator's own request being rejected with a reason they can act on -
     * a missing rate card, a seller without a state - so it returns 400 with the explanation
     * rather than an opaque 500.
     */
    @ExceptionHandler(CommissionInvoiceService.CommissionException.class)
    public ResponseEntity<Map<String, String>> handleCommissionError(
            CommissionInvoiceService.CommissionException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    private String currentUserEmail() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
