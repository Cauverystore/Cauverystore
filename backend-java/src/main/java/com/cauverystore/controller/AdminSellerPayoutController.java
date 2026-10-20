package com.cauverystore.controller;

import com.cauverystore.entities.Role;
import com.cauverystore.entities.SellerPayout;
import com.cauverystore.entities.User;
import com.cauverystore.repository.SellerPayoutRepository;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.service.AuditService;
import com.cauverystore.service.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Recording money paid to sellers.
 *
 * The payment itself happens at the bank; this is the ledger of it, so a seller can see what
 * arrived and an admin can see what has gone out. Entries are voided, never deleted.
 */
@RestController
@RequestMapping("/api/admin/seller-payouts")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminSellerPayoutController {

    private static final Set<String> METHODS = Set.of("BANK_TRANSFER", "UPI", "OTHER");

    private final SellerPayoutRepository payoutRepo;
    private final UserRepository userRepo;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public AdminSellerPayoutController(SellerPayoutRepository payoutRepo, UserRepository userRepo,
                                       AuthorizationService authorizationService, AuditService auditService) {
        this.payoutRepo = payoutRepo;
        this.userRepo = userRepo;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    /** Sellers to choose from when recording a payment. */
    @GetMapping("/sellers")
    public ResponseEntity<?> sellers() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : userRepo.findByRole(Role.SELLER)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getFullName() != null ? u.getFullName() : u.getEmail());
            m.put("email", u.getEmail());
            out.add(m);
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("name")).toLowerCase()));
        return ResponseEntity.ok(out);
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) Long sellerId) {
        List<SellerPayout> payouts = sellerId != null
                ? payoutRepo.findBySellerIdOrderByPaidOnDescIdDesc(sellerId)
                : payoutRepo.findAllByOrderByPaidOnDescIdDesc();
        Map<Long, String> names = new HashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (SellerPayout p : payouts) {
            String name = names.computeIfAbsent(p.getSellerId(), id -> userRepo.findById(id)
                    .map(u -> u.getFullName() != null ? u.getFullName() : u.getEmail()).orElse("Seller #" + id));
            Map<String, Object> m = toMap(p);
            m.put("sellerName", name);
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping
    public ResponseEntity<?> record(@RequestBody Map<String, Object> body) {
        Long sellerId = asLong(body.get("sellerId"));
        if (sellerId == null) return bad("Choose the seller who was paid.");
        Optional<User> seller = userRepo.findById(sellerId);
        if (seller.isEmpty() || seller.get().getRole() != Role.SELLER) return bad("No seller with id " + sellerId + ".");

        Double amount = asDouble(body.get("amount"));
        if (amount == null || amount <= 0) return bad("The amount must be more than zero.");
        amount = Math.round(amount * 100.0) / 100.0;

        LocalDate paidOn;
        LocalDate periodFrom;
        LocalDate periodTo;
        try {
            paidOn = asDate(body.get("paidOn"));
            periodFrom = asDate(body.get("periodFrom"));
            periodTo = asDate(body.get("periodTo"));
        } catch (DateTimeParseException e) {
            return bad("Dates must be in the form YYYY-MM-DD.");
        }
        if (paidOn == null) return bad("Enter the date the money was sent.");
        if (paidOn.isAfter(LocalDate.now())) return bad("Record a payment once it has been sent, not before.");
        if (periodFrom != null && periodTo != null && periodTo.isBefore(periodFrom)) {
            return bad("The period cannot end before it starts.");
        }

        String reference = asText(body.get("reference"));
        if (reference == null) return bad("Enter the bank or UPI reference (UTR), so the seller can match the payment.");
        if (reference.length() > 64) return bad("The reference is too long (64 characters at most).");
        if (payoutRepo.existsBySellerIdAndReferenceIgnoreCaseAndVoidedFalse(sellerId, reference)) {
            return bad("A payment with reference " + reference + " is already recorded for this seller.");
        }

        String method = Optional.ofNullable(asText(body.get("method"))).orElse("BANK_TRANSFER").toUpperCase();
        if (!METHODS.contains(method)) return bad("The method must be bank transfer, UPI or other.");

        SellerPayout p = new SellerPayout();
        p.setSellerId(sellerId);
        p.setAmount(amount);
        p.setPaidOn(paidOn);
        p.setReference(reference);
        p.setMethod(method);
        p.setPeriodFrom(periodFrom);
        p.setPeriodTo(periodTo);
        p.setNote(asText(body.get("note")));
        p.setRecordedBy(authorizationService.getCurrentUserEmail());
        p = payoutRepo.save(p);

        auditService.log(authorizationService.getCurrentUserId(), authorizationService.getCurrentUserEmail(),
                "SELLER_PAYOUT_RECORDED", "SellerPayout", p.getId(),
                "Rs." + amount + " to seller " + sellerId + ", ref " + reference, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(toMap(p));
    }

    /** Takes back an entry made in error. The row stays, marked void, with who and why. */
    @PostMapping("/{id}/void")
    public ResponseEntity<?> voidPayout(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Optional<SellerPayout> found = payoutRepo.findById(id);
        if (found.isEmpty()) return bad("No payout with id " + id + ".");
        SellerPayout p = found.get();
        if (Boolean.TRUE.equals(p.getVoided())) return bad("This payout is already void.");
        String reason = body == null ? null : asText(body.get("reason"));
        if (reason == null) return bad("Say why this entry is being voided.");

        p.setVoided(true);
        p.setVoidReason(reason);
        p.setVoidedBy(authorizationService.getCurrentUserEmail());
        p.setVoidedAt(LocalDateTime.now());
        payoutRepo.save(p);

        auditService.log(authorizationService.getCurrentUserId(), authorizationService.getCurrentUserEmail(),
                "SELLER_PAYOUT_VOIDED", "SellerPayout", p.getId(), reason, null);
        return ResponseEntity.ok(toMap(p));
    }

    static Map<String, Object> toMap(SellerPayout p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("sellerId", p.getSellerId());
        m.put("amount", p.getAmount());
        m.put("paidOn", p.getPaidOn());
        m.put("reference", p.getReference());
        m.put("method", p.getMethod());
        m.put("periodFrom", p.getPeriodFrom());
        m.put("periodTo", p.getPeriodTo());
        m.put("note", p.getNote());
        m.put("recordedBy", p.getRecordedBy());
        m.put("voided", Boolean.TRUE.equals(p.getVoided()));
        m.put("voidReason", p.getVoidReason());
        m.put("voidedAt", p.getVoidedAt());
        m.put("createdAt", p.getCreatedAt());
        return m;
    }

    private static ResponseEntity<Map<String, Object>> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private static String asText(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Long asLong(Object o) {
        try { return o == null ? null : Long.valueOf(o.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Double asDouble(Object o) {
        try { return o == null ? null : Double.valueOf(o.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    private static LocalDate asDate(Object o) {
        String s = asText(o);
        return s == null ? null : LocalDate.parse(s);
    }
}
