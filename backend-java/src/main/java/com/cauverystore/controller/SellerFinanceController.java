package com.cauverystore.controller;

import com.cauverystore.entities.LedgerEntry;
import com.cauverystore.entities.SellerReserve;
import com.cauverystore.repository.LedgerEntryRepository;
import com.cauverystore.service.AuthorizationService;
import com.cauverystore.service.LedgerService;
import com.cauverystore.service.SellerAccessService;
import com.cauverystore.service.SellerPermissions;
import com.cauverystore.service.SettlementService;
import com.cauverystore.util.FinanceMath;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** What a seller sees of their own money: balance, reserve and a line-by-line statement. */
@RestController
@RequestMapping("/api/seller/finance")
public class SellerFinanceController {

    private final LedgerService ledger;
    private final LedgerEntryRepository ledgerRepo;
    private final SettlementService settlement;
    private final AuthorizationService auth;
    private final SellerAccessService access;

    public SellerFinanceController(LedgerService ledger, LedgerEntryRepository ledgerRepo, SettlementService settlement,
                                   AuthorizationService auth, SellerAccessService access) {
        this.ledger = ledger;
        this.ledgerRepo = ledgerRepo;
        this.settlement = settlement;
        this.auth = auth;
        this.access = access;
    }

    @GetMapping("/balance")
    public ResponseEntity<?> balance() {
        access.require(SellerPermissions.PAYOUTS_VIEW);
        Long sellerId = auth.getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        long[] b = ledger.balances().getOrDefault(sellerId, new long[]{0, 0});
        SellerReserve r = settlement.reserveOf(sellerId).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tracked", ledger.startDate().isPresent());
        m.put("payableNow", FinanceMath.rupees(Math.max(0, b[0])));
        m.put("owedToUs", b[0] < 0 ? FinanceMath.rupees(-b[0]) : 0.0);
        m.put("pending", FinanceMath.rupees(b[1]));
        m.put("reserveHeld", r == null ? 0.0 : FinanceMath.rupees(r.getHeldPaise()));
        m.put("reservePercent", r == null ? 0.0 : r.getPercentBp() / 100.0);
        m.put("minimumPayout", FinanceMath.rupees(SettlementService.MIN_PAYOUT_PAISE));
        return ResponseEntity.ok(m);
    }

    @GetMapping("/statement")
    public ResponseEntity<?> statement(@RequestParam(defaultValue = "0") int page) {
        access.require(SellerPermissions.PAYOUTS_VIEW);
        Long sellerId = auth.getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        var p = ledgerRepo.search(sellerId, "", PageRequest.of(Math.max(page, 0), 50));
        List<Map<String, Object>> lines = new ArrayList<>();
        for (LedgerEntry e : p.getContent()) {
            Map<String, Object> m = AdminFinanceController.lineMap(e);
            m.remove("sellerId");
            lines.add(m);
        }
        return ResponseEntity.ok(Map.of("lines", lines, "page", p.getNumber(), "totalPages", p.getTotalPages()));
    }
}
