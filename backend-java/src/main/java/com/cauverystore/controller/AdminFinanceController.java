package com.cauverystore.controller;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import com.cauverystore.service.AuthorizationService;
import com.cauverystore.service.LedgerService;
import com.cauverystore.service.SettlementService;
import com.cauverystore.service.SettlementService.FinanceException;
import com.cauverystore.util.FinanceMath;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/** The finance desk: what sellers are owed, payout runs, reserves, adjustments and the books-versus-bank checks. */
@RestController
@RequestMapping("/api/admin/finance")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminFinanceController {

    private final LedgerService ledger;
    private final SettlementService settlement;
    private final LedgerEntryRepository ledgerRepo;
    private final SettlementRunRepository runRepo;
    private final SettlementItemRepository itemRepo;
    private final SellerReserveRepository reserveRepo;
    private final SellerPayoutRepository payoutRepo;
    private final OrderRepository orderRepo;
    private final RefundRepository refundRepo;
    private final UserRepository userRepo;
    private final AuthorizationService auth;

    public AdminFinanceController(LedgerService ledger, SettlementService settlement, LedgerEntryRepository ledgerRepo,
                                  SettlementRunRepository runRepo, SettlementItemRepository itemRepo,
                                  SellerReserveRepository reserveRepo, SellerPayoutRepository payoutRepo,
                                  OrderRepository orderRepo, RefundRepository refundRepo, UserRepository userRepo,
                                  AuthorizationService auth) {
        this.ledger = ledger;
        this.settlement = settlement;
        this.ledgerRepo = ledgerRepo;
        this.runRepo = runRepo;
        this.itemRepo = itemRepo;
        this.reserveRepo = reserveRepo;
        this.payoutRepo = payoutRepo;
        this.orderRepo = orderRepo;
        this.refundRepo = refundRepo;
        this.userRepo = userRepo;
        this.auth = auth;
    }

    @ExceptionHandler({FinanceException.class, IllegalArgumentException.class, DateTimeParseException.class})
    ResponseEntity<?> rule(RuntimeException e) {
        String msg = e instanceof DateTimeParseException ? "Dates must be in the form YYYY-MM-DD." : e.getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    // ----------------------------------------------------------- overview

    @GetMapping("/overview")
    public ResponseEntity<?> overview() {
        long available = 0, pending = 0;
        for (long[] b : ledger.balances().values()) {
            if (b[0] > 0) available += b[0];
            pending += b[1];
        }
        long reserves = reserveRepo.findAll().stream().mapToLong(SellerReserve::getHeldPaise).sum();
        double paid = payoutRepo.findAllByOrderByPaidOnDescIdDesc().stream()
                .filter(p -> !Boolean.TRUE.equals(p.getVoided())).mapToDouble(SellerPayout::getAmount).sum();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ledgerStartDate", ledger.startDate().map(LocalDate::toString).orElse(null));
        m.put("payableNow", FinanceMath.rupees(available));
        m.put("pendingReturnWindow", FinanceMath.rupees(pending));
        m.put("reservesHeld", FinanceMath.rupees(reserves));
        m.put("totalPaidOut", Math.round(paid * 100.0) / 100.0);
        m.put("openRuns", runRepo.findAll().stream().filter(r -> Set.of("DRAFT", "APPROVED").contains(r.getStatus())).count());
        m.put("unreconciledPayouts", payoutRepo.findByVoidedFalseAndReconciledAtIsNull().size());
        m.put("minimumPayout", FinanceMath.rupees(SettlementService.MIN_PAYOUT_PAISE));
        return ResponseEntity.ok(m);
    }

    @PutMapping("/settings")
    public ResponseEntity<?> settings(@RequestBody Map<String, Object> body) {
        auth.requireSuperAdmin();
        Object d = body.get("ledgerStartDate");
        if (d == null || d.toString().isBlank()) return bad("Choose the date the ledger starts from.");
        LocalDate date = LocalDate.parse(d.toString().trim());
        if (ledger.startDate().isPresent()) return bad("The start date is already set to " + ledger.startDate().get() + ". Changing it would re-book or skip orders.");
        ledger.setStartDate(date);
        return overview();
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync() {
        LedgerService.SyncResult r = ledger.sync();
        if (!r.configured()) return bad("Set the date the ledger starts from first.");
        return ResponseEntity.ok(Map.of("ordersPosted", r.ordersPosted(), "refundsPosted", r.refundsPosted(),
                "ordersWithoutRate", r.ordersWithoutRate(), "refundsSkipped", r.refundsSkipped()));
    }

    // ----------------------------------------------------------- payables

    /** The payouts-due queue: every seller with money owed or waiting. */
    @GetMapping("/payables")
    public ResponseEntity<?> payables() {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<Long, long[]> balances = ledger.balances();
        Set<Long> ids = new LinkedHashSet<>(balances.keySet());
        reserveRepo.findAll().forEach(r -> ids.add(r.getSellerId()));
        for (Long id : ids) {
            long[] b = balances.getOrDefault(id, new long[]{0, 0});
            SellerReserve r = reserveRepo.findById(id).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sellerId", id);
            m.put("sellerName", sellerName(id));
            m.put("available", FinanceMath.rupees(b[0]));
            m.put("pending", FinanceMath.rupees(b[1]));
            m.put("reservePercent", r == null ? 0 : r.getPercentBp() / 100.0);
            m.put("reserveHeld", r == null ? 0.0 : FinanceMath.rupees(r.getHeldPaise()));
            out.add(m);
        }
        out.sort((a, b) -> Double.compare((Double) b.get("available"), (Double) a.get("available")));
        return ResponseEntity.ok(out);
    }

    @GetMapping("/ledger")
    public ResponseEntity<?> ledgerLines(@RequestParam(required = false) Long sellerId,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(defaultValue = "0") int page) {
        Page<LedgerEntry> p = ledgerRepo.search(sellerId == null ? 0L : sellerId, type == null ? "" : type.trim(),
                PageRequest.of(Math.max(page, 0), 50));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lines", p.getContent().stream().map(AdminFinanceController::lineMap).toList());
        out.put("page", p.getNumber());
        out.put("totalPages", p.getTotalPages());
        out.put("totalLines", p.getTotalElements());
        return ResponseEntity.ok(out);
    }

    static Map<String, Object> lineMap(LedgerEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("sellerId", e.getSellerId());
        m.put("orderId", e.getOrderId());
        m.put("type", e.getEntryType());
        m.put("amount", FinanceMath.rupees(e.getAmountPaise()));
        m.put("availableOn", e.getAvailableOn());
        m.put("description", e.getDescription());
        m.put("runId", e.getSettlementRunId());
        m.put("createdAt", e.getCreatedAt());
        return m;
    }

    // --------------------------------------------------------------- runs

    @GetMapping("/runs")
    public ResponseEntity<?> runs() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SettlementRun r : runRepo.findAllByOrderByIdDesc()) out.add(runMap(r));
        return ResponseEntity.ok(out);
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<?> run(@PathVariable Long id) {
        SettlementRun r = runRepo.findById(id).orElse(null);
        if (r == null) return bad("No run with id " + id + ".");
        Map<String, Object> m = runMap(r);
        List<Map<String, Object>> items = new ArrayList<>();
        for (SettlementItem i : itemRepo.findByRunIdOrderByIdAsc(id)) {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("id", i.getId());
            im.put("sellerId", i.getSellerId());
            im.put("sellerName", sellerName(i.getSellerId()));
            im.put("gross", FinanceMath.rupees(i.getGrossPaise()));
            im.put("reserve", FinanceMath.rupees(i.getReservePaise()));
            im.put("payable", FinanceMath.rupees(i.getPayablePaise()));
            im.put("lines", i.getEntryCount());
            im.put("status", i.getStatus());
            im.put("heldReason", i.getHeldReason());
            im.put("utr", i.getUtr());
            im.put("paidOn", i.getPaidOn());
            items.add(im);
        }
        m.put("items", items);
        return ResponseEntity.ok(m);
    }

    @PostMapping("/runs")
    public ResponseEntity<?> createRun(@RequestBody(required = false) Map<String, Object> body) {
        LocalDate cutoff = body == null || text(body.get("cutoff")) == null ? null : LocalDate.parse(text(body.get("cutoff")));
        SettlementRun r = settlement.createRun(cutoff, body == null ? null : text(body.get("note")),
                auth.getCurrentUserId(), auth.getCurrentUserEmail());
        return ResponseEntity.ok(runMap(r));
    }

    @PostMapping("/runs/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        return ResponseEntity.ok(runMap(settlement.approve(id, auth.getCurrentUserId(), auth.getCurrentUserEmail())));
    }

    @PostMapping("/runs/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(runMap(settlement.cancel(id, auth.getCurrentUserId(), auth.getCurrentUserEmail())));
    }

    @PostMapping("/runs/{id}/items/{itemId}/paid")
    public ResponseEntity<?> paid(@PathVariable Long id, @PathVariable Long itemId, @RequestBody Map<String, Object> body) {
        LocalDate on = text(body.get("paidOn")) == null ? null : LocalDate.parse(text(body.get("paidOn")));
        SettlementItem i = settlement.markPaid(id, itemId, text(body.get("utr")), on, text(body.get("method")),
                auth.getCurrentUserId(), auth.getCurrentUserEmail());
        return ResponseEntity.ok(Map.of("id", i.getId(), "status", i.getStatus()));
    }

    @GetMapping("/runs/{id}/bank-file")
    public ResponseEntity<?> bankFile(@PathVariable Long id) {
        String csv = settlement.bankFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payout-run-" + id + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv")).body(csv);
    }

    private Map<String, Object> runMap(SettlementRun r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("status", r.getStatus());
        m.put("cutoffDate", r.getCutoffDate());
        m.put("totalPayable", FinanceMath.rupees(r.getTotalPayablePaise()));
        m.put("payableSellers", r.getPayableSellers());
        m.put("heldSellers", r.getHeldSellers());
        m.put("createdBy", r.getCreatedBy());
        m.put("approvedBy", r.getApprovedBy());
        m.put("approvedAt", r.getApprovedAt());
        m.put("note", r.getNote());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    // ---------------------------------------------- reserves & adjustments

    @PutMapping("/sellers/{sellerId}/reserve")
    public ResponseEntity<?> setReserve(@PathVariable Long sellerId, @RequestBody Map<String, Object> body) {
        BigDecimal pct = decimal(body.get("percent"));
        if (pct == null) return bad("Enter the reserve percentage.");
        SellerReserve r = settlement.setReservePercent(sellerId, pct.movePointRight(2).intValue(),
                auth.getCurrentUserId(), auth.getCurrentUserEmail());
        return ResponseEntity.ok(Map.of("sellerId", sellerId, "percent", r.getPercentBp() / 100.0));
    }

    @PostMapping("/sellers/{sellerId}/reserve/release")
    public ResponseEntity<?> releaseReserve(@PathVariable Long sellerId, @RequestBody Map<String, Object> body) {
        BigDecimal amt = decimal(body.get("amount"));
        if (amt == null) return bad("Enter the amount to release.");
        SellerReserve r = settlement.releaseReserve(sellerId, amt.movePointRight(2).longValue(), text(body.get("reason")),
                auth.getCurrentUserId(), auth.getCurrentUserEmail());
        return ResponseEntity.ok(Map.of("sellerId", sellerId, "reserveHeld", FinanceMath.rupees(r.getHeldPaise())));
    }

    /** A signed correction or a penalty. Positive credits the seller; a penalty is always a deduction. */
    @PostMapping("/adjustments")
    public ResponseEntity<?> adjust(@RequestBody Map<String, Object> body) {
        auth.requireSuperAdmin();
        Long sellerId = body.get("sellerId") == null ? null : Long.valueOf(body.get("sellerId").toString());
        BigDecimal amt = decimal(body.get("amount"));
        if (sellerId == null || amt == null) return bad("Choose a seller and enter an amount.");
        String type = Optional.ofNullable(text(body.get("type"))).orElse("ADJUSTMENT").toUpperCase();
        LedgerEntry e = settlement.adjust(sellerId, type, amt.movePointRight(2).longValue(), text(body.get("reason")),
                auth.getCurrentUserId(), auth.getCurrentUserEmail());
        return ResponseEntity.ok(lineMap(e));
    }

    // ------------------------------------------------------- reconciliation

    /** Body: {"statement": "UTR,amount" one per line}. */
    @PostMapping("/reconcile")
    public ResponseEntity<?> reconcile(@RequestBody Map<String, Object> body) {
        String raw = text(body.get("statement"));
        if (raw == null) return bad("Paste the bank statement lines: the reference, a comma, then the amount.");
        List<SettlementService.StatementLine> lines = new ArrayList<>();
        for (String row : raw.split("\\r?\\n")) {
            String[] cols = row.split("[,\\t]");
            if (cols.length < 2 || cols[0].isBlank()) continue;
            BigDecimal amt = decimal(cols[cols.length - 1].replace("₹", ""));
            if (amt == null) continue;
            lines.add(new SettlementService.StatementLine(cols[0].trim(), amt.movePointRight(2).longValue()));
        }
        if (lines.isEmpty()) return bad("No usable lines found. Use: reference, amount.");
        return ResponseEntity.ok(settlement.reconcile(lines, auth.getCurrentUserId(), auth.getCurrentUserEmail()));
    }

    /** Self-checks on the books. Anything not OK needs a person to look at it. */
    @GetMapping("/integrity")
    public ResponseEntity<?> integrity() {
        List<Map<String, Object>> checks = new ArrayList<>();
        Optional<LocalDate> start = ledger.startDate();

        if (start.isEmpty()) {
            checks.add(check("start-date", "Ledger start date is set", false, 1, "Set it on the Overview tab; nothing is booked until then."));
        } else {
            List<Long> unposted = new ArrayList<>();
            for (Order o : orderRepo.findByStatusIn(List.of("DELIVERED"))) {
                if (o.getSellerId() == null || o.getDeliveredAt() == null) continue;
                if (o.getDeliveredAt().toLocalDate().isBefore(start.get())) continue;
                if (!ledgerRepo.existsByRefKey("ORDER:" + o.getId() + ":SALE")) unposted.add(o.getId());
            }
            checks.add(check("unposted-orders", "Every delivered order is in the ledger", unposted.isEmpty(), unposted.size(),
                    unposted.isEmpty() ? "" : "Not booked, usually because no commission rate applies: orders " + unposted.stream().limit(20).toList()));
            long unbooked = refundRepo.findAll().stream().filter(r -> r.getOrderId() != null && r.getAmount() != null && r.getAmount() > 0
                    && ledgerRepo.existsByRefKey("ORDER:" + r.getOrderId() + ":SALE")
                    && !ledgerRepo.existsByRefKey("REFUND:" + r.getId() + ":SALE")).count();
            checks.add(check("unposted-refunds", "Every refund on a booked order is in the ledger", unbooked == 0, (int) unbooked, "Press Sync to book them."));
        }

        int mismatched = 0;
        List<Long> badRuns = new ArrayList<>();
        for (SettlementRun r : runRepo.findAll()) {
            if (SettlementRun.CANCELLED.equals(r.getStatus())) continue;
            long lines = ledgerRepo.sumForRun(r.getId());
            long items = itemRepo.findByRunIdOrderByIdAsc(r.getId()).stream()
                    .filter(i -> !SettlementItem.HELD.equals(i.getStatus())).mapToLong(SettlementItem::getGrossPaise).sum();
            if (lines != items) { mismatched++; badRuns.add(r.getId()); }
        }
        checks.add(check("run-totals", "Each run's lines add up to its payouts", mismatched == 0, mismatched,
                mismatched == 0 ? "" : "Runs " + badRuns));

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long stale = runRepo.findByStatus(SettlementRun.APPROVED).stream()
                .filter(r -> r.getApprovedAt() != null && r.getApprovedAt().isBefore(weekAgo)).count();
        checks.add(check("stale-runs", "No approved run is waiting more than a week to be paid", stale == 0, (int) stale, ""));

        long unrec = payoutRepo.findByVoidedFalseAndReconciledAtIsNull().stream()
                .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isBefore(LocalDateTime.now().minusDays(3))).count();
        checks.add(check("unreconciled", "Payments older than 3 days are matched to the bank statement", unrec == 0, (int) unrec,
                "Paste the bank statement on the Reconcile tab."));

        long negative = ledger.balances().values().stream().filter(b -> b[0] < 0).count();
        checks.add(check("negative-balances", "No seller owes the marketplace", negative == 0, (int) negative,
                "A negative balance is recovered from the seller's next payout."));
        return ResponseEntity.ok(checks);
    }

    private static Map<String, Object> check(String code, String title, boolean ok, int count, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code); m.put("title", title); m.put("ok", ok); m.put("count", count); m.put("detail", detail);
        return m;
    }

    private String sellerName(Long id) {
        return userRepo.findById(id).map(u -> u.getFullName() != null ? u.getFullName() : u.getEmail()).orElse("Seller #" + id);
    }

    private static ResponseEntity<Map<String, Object>> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private static String text(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal decimal(Object o) {
        try { return o == null ? null : new BigDecimal(o.toString().trim()).setScale(2, java.math.RoundingMode.HALF_UP); }
        catch (NumberFormatException e) { return null; }
    }
}
