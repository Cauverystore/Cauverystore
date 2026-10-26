package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import com.cauverystore.util.FinanceMath;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Payout runs: gather what sellers are owed, have a second person approve it, record each payment,
 * and check the books against the bank.
 *
 * Rules the code enforces, because a screen cannot be trusted to:
 *  - a run is prepared by one person and approved by another;
 *  - a seller whose balance is not positive, is below the minimum, or has no bank account is held
 *    and carried forward, never paid;
 *  - a ledger line belongs to at most one run;
 *  - a payment is recorded once per bank reference.
 */
@Service
public class SettlementService {

    /** Below this, a payout costs more to send than it is worth; the balance waits for the next run. */
    public static final long MIN_PAYOUT_PAISE = 10_000;
    public static final int MAX_RESERVE_BP = 2_000;

    private final LedgerEntryRepository ledgerRepo;
    private final SettlementRunRepository runRepo;
    private final SettlementItemRepository itemRepo;
    private final SellerReserveRepository reserveRepo;
    private final SellerPayoutRepository payoutRepo;
    private final SellerRegistrationRepository registrationRepo;
    private final UserRepository userRepo;
    private final LedgerService ledger;
    private final AuditService audit;
    private final boolean allowSelfApproval;

    public SettlementService(LedgerEntryRepository ledgerRepo, SettlementRunRepository runRepo,
                             SettlementItemRepository itemRepo, SellerReserveRepository reserveRepo,
                             SellerPayoutRepository payoutRepo, SellerRegistrationRepository registrationRepo,
                             UserRepository userRepo, LedgerService ledger, AuditService audit,
                             @Value("${finance.settlement.allow-self-approval:false}") boolean allowSelfApproval) {
        this.ledgerRepo = ledgerRepo;
        this.runRepo = runRepo;
        this.itemRepo = itemRepo;
        this.reserveRepo = reserveRepo;
        this.payoutRepo = payoutRepo;
        this.registrationRepo = registrationRepo;
        this.userRepo = userRepo;
        this.ledger = ledger;
        this.audit = audit;
        this.allowSelfApproval = allowSelfApproval;
    }

    /** A rule was broken; the message is written for the person at the screen. */
    public static class FinanceException extends RuntimeException {
        public FinanceException(String message) { super(message); }
    }

    // ---------------------------------------------------------------- runs

    @Transactional
    public SettlementRun createRun(LocalDate cutoff, String note, Long userId, String by) {
        if (cutoff == null) cutoff = LocalDate.now();
        if (cutoff.isAfter(LocalDate.now())) throw new FinanceException("The cutoff cannot be in the future.");
        for (SettlementRun open : runRepo.findByStatus(SettlementRun.DRAFT)) {
            throw new FinanceException("Run #" + open.getId() + " is still a draft. Approve or cancel it before preparing another.");
        }
        ledger.sync();

        Map<Long, List<LedgerEntry>> bySeller = new LinkedHashMap<>();
        for (LedgerEntry e : ledgerRepo.findBySettlementRunIdIsNullAndAvailableOnLessThanEqual(cutoff)) {
            bySeller.computeIfAbsent(e.getSellerId(), k -> new ArrayList<>()).add(e);
        }
        if (bySeller.isEmpty()) throw new FinanceException("Nothing is payable on or before " + cutoff + ".");

        SettlementRun run = new SettlementRun();
        run.setCutoffDate(cutoff);
        run.setNote(note);
        run.setCreatedBy(by);
        run = runRepo.save(run);

        long total = 0;
        int payable = 0, held = 0;
        for (Map.Entry<Long, List<LedgerEntry>> en : bySeller.entrySet()) {
            Long sellerId = en.getKey();
            long gross = en.getValue().stream().mapToLong(LedgerEntry::getAmountPaise).sum();
            SettlementItem item = new SettlementItem();
            item.setRunId(run.getId());
            item.setSellerId(sellerId);
            item.setGrossPaise(gross);
            item.setEntryCount(en.getValue().size());

            String hold = holdReason(sellerId, gross);
            if (hold != null) {
                item.setStatus(SettlementItem.HELD);
                item.setHeldReason(hold);
                held++;
            } else {
                long reserve = FinanceMath.scale(gross, reserveBp(sellerId), 10_000);
                item.setStatus(SettlementItem.PAYABLE);
                item.setReservePaise(reserve);
                item.setPayablePaise(gross - reserve);
                total += gross - reserve;
                payable++;
                for (LedgerEntry e : en.getValue()) e.setSettlementRunId(run.getId());
                ledgerRepo.saveAll(en.getValue());
            }
            itemRepo.save(item);
        }
        if (payable == 0) {
            // Nothing to pay: keep no empty draft around blocking the next one.
            run.setStatus(SettlementRun.CANCELLED);
            run.setNote("Nothing payable: every seller was held.");
        }
        run.setTotalPayablePaise(total);
        run.setPayableSellers(payable);
        run.setHeldSellers(held);
        run = runRepo.save(run);
        audit.log(userId, by, "SETTLEMENT_RUN_PREPARED", "SettlementRun", run.getId(),
                payable + " sellers, Rs." + FinanceMath.rupees(total) + ", " + held + " held", null);
        return run;
    }

    private String holdReason(Long sellerId, long gross) {
        if (gross <= 0) return "Balance is not positive; carried forward";
        if (gross < MIN_PAYOUT_PAISE) return "Below the minimum payout of Rs." + FinanceMath.rupees(MIN_PAYOUT_PAISE) + "; carried forward";
        if (bankOf(sellerId).isEmpty()) return "No bank account on file";
        return null;
    }

    private int reserveBp(Long sellerId) {
        return reserveRepo.findById(sellerId).map(SellerReserve::getPercentBp).orElse(0);
    }

    @Transactional
    public SettlementRun approve(Long runId, Long userId, String by) {
        SettlementRun run = run(runId);
        if (!SettlementRun.DRAFT.equals(run.getStatus())) throw new FinanceException("Only a draft run can be approved.");
        if (!allowSelfApproval && by != null && by.equalsIgnoreCase(run.getCreatedBy())) {
            throw new FinanceException("A different person must approve this run. It was prepared by " + run.getCreatedBy() + ".");
        }
        // The draft is only a promise until now: check it still adds up before money is committed.
        long lines = ledgerRepo.sumForRun(runId);
        long items = itemRepo.findByRunIdOrderByIdAsc(runId).stream()
                .filter(i -> SettlementItem.PAYABLE.equals(i.getStatus())).mapToLong(SettlementItem::getGrossPaise).sum();
        if (lines != items) throw new FinanceException("The run no longer matches the ledger. Cancel it and prepare a new one.");

        for (SettlementItem i : itemRepo.findByRunIdOrderByIdAsc(runId)) {
            if (SettlementItem.PAYABLE.equals(i.getStatus()) && i.getReservePaise() != 0) {
                SellerReserve r = reserveRepo.findById(i.getSellerId()).orElseGet(() -> newReserve(i.getSellerId()));
                r.setHeldPaise(r.getHeldPaise() + i.getReservePaise());
                r.setUpdatedBy(by);
                reserveRepo.save(r);
            }
        }
        run.setStatus(SettlementRun.APPROVED);
        run.setApprovedBy(by);
        run.setApprovedAt(LocalDateTime.now());
        run = runRepo.save(run);
        audit.log(userId, by, "SETTLEMENT_RUN_APPROVED", "SettlementRun", runId,
                "Rs." + FinanceMath.rupees(run.getTotalPayablePaise()), null);
        return run;
    }

    @Transactional
    public SettlementRun cancel(Long runId, Long userId, String by) {
        SettlementRun run = run(runId);
        if (!SettlementRun.DRAFT.equals(run.getStatus())) throw new FinanceException("Only a draft run can be cancelled.");
        for (LedgerEntry e : ledgerRepo.findBySettlementRunId(runId)) {
            e.setSettlementRunId(null);
            ledgerRepo.save(e);
        }
        run.setStatus(SettlementRun.CANCELLED);
        run = runRepo.save(run);
        audit.log(userId, by, "SETTLEMENT_RUN_CANCELLED", "SettlementRun", runId, null, null);
        return run;
    }

    /** Records that one seller in an approved run has been paid at the bank. */
    @Transactional
    public SettlementItem markPaid(Long runId, Long itemId, String utr, LocalDate paidOn, String method, Long userId, String by) {
        SettlementRun run = run(runId);
        if (!SettlementRun.APPROVED.equals(run.getStatus())) throw new FinanceException("Only an approved run can be paid.");
        SettlementItem item = itemRepo.findById(itemId).filter(i -> runId.equals(i.getRunId()))
                .orElseThrow(() -> new FinanceException("That line is not in this run."));
        if (!SettlementItem.PAYABLE.equals(item.getStatus())) throw new FinanceException("This line is not waiting for payment.");
        if (utr == null || utr.isBlank()) throw new FinanceException("Enter the bank or UPI reference (UTR).");
        utr = utr.trim();
        if (utr.length() > 64) throw new FinanceException("The reference is too long.");
        if (paidOn == null) paidOn = LocalDate.now();
        if (paidOn.isAfter(LocalDate.now())) throw new FinanceException("Record a payment once it has been sent.");
        if (payoutRepo.existsBySellerIdAndReferenceIgnoreCaseAndVoidedFalse(item.getSellerId(), utr)) {
            throw new FinanceException("Reference " + utr + " is already recorded for this seller.");
        }

        SellerPayout p = new SellerPayout();
        p.setSellerId(item.getSellerId());
        p.setAmount(FinanceMath.rupees(item.getPayablePaise()));
        p.setPaidOn(paidOn);
        p.setReference(utr);
        p.setMethod(method == null || method.isBlank() ? "BANK_TRANSFER" : method.toUpperCase());
        p.setPeriodTo(run.getCutoffDate());
        p.setNote("Settlement run #" + runId);
        p.setRecordedBy(by);
        p.setSettlementItemId(item.getId());
        p = payoutRepo.save(p);

        item.setStatus(SettlementItem.PAID);
        item.setUtr(utr);
        item.setPaidOn(paidOn);
        item.setPayoutId(p.getId());
        itemRepo.save(item);

        boolean allPaid = itemRepo.findByRunIdOrderByIdAsc(runId).stream()
                .noneMatch(i -> SettlementItem.PAYABLE.equals(i.getStatus()));
        if (allPaid) { run.setStatus(SettlementRun.PAID); runRepo.save(run); }
        audit.log(userId, by, "SETTLEMENT_PAID", "SettlementItem", item.getId(),
                "Rs." + p.getAmount() + " to seller " + item.getSellerId() + ", ref " + utr, null);
        return item;
    }

    /** A CSV the bank's bulk-transfer upload can start from. Only for an approved run. */
    public String bankFile(Long runId) {
        SettlementRun run = run(runId);
        if (!SettlementRun.APPROVED.equals(run.getStatus()) && !SettlementRun.PAID.equals(run.getStatus())) {
            throw new FinanceException("The bank file is available once the run is approved.");
        }
        StringBuilder sb = new StringBuilder("Beneficiary name,Account number,IFSC,Amount (INR),Reference,Narration\n");
        for (SettlementItem i : itemRepo.findByRunIdOrderByIdAsc(runId)) {
            if (!SettlementItem.PAYABLE.equals(i.getStatus())) continue;
            SellerRegistration reg = bankOf(i.getSellerId()).orElse(null);
            if (reg == null) continue;
            sb.append(csv(reg.getBankAccountName())).append(',').append(csv(reg.getBankAccountNumber())).append(',')
              .append(csv(reg.getBankIfsc())).append(',').append(FinanceMath.rupees(i.getPayablePaise())).append(',')
              .append("CS-R").append(runId).append("-S").append(i.getSellerId()).append(',')
              .append(csv("Cauvery Store payout run " + runId)).append('\n');
        }
        return sb.toString();
    }

    private Optional<SellerRegistration> bankOf(Long sellerId) {
        return registrationRepo.findByUserId(sellerId)
                .filter(r -> r.getBankAccountNumber() != null && !r.getBankAccountNumber().isBlank()
                        && r.getBankIfsc() != null && !r.getBankIfsc().isBlank());
    }

    private static String csv(String v) {
        if (v == null) return "";
        String s = v.replace("\"", "\"\"");
        // A cell starting with = + - @ is read as a formula by Excel.
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s + "\"" : s;
    }

    // ------------------------------------------------------------ reserves

    @Transactional
    public SellerReserve setReservePercent(Long sellerId, int percentBp, Long userId, String by) {
        if (percentBp < 0 || percentBp > MAX_RESERVE_BP) {
            throw new FinanceException("The reserve must be between 0% and " + (MAX_RESERVE_BP / 100) + "%.");
        }
        if (userRepo.findById(sellerId).filter(u -> u.getRole() == Role.SELLER).isEmpty()) {
            throw new FinanceException("No seller with id " + sellerId + ".");
        }
        SellerReserve r = reserveRepo.findById(sellerId).orElseGet(() -> newReserve(sellerId));
        r.setPercentBp(percentBp);
        r.setUpdatedBy(by);
        r = reserveRepo.save(r);
        audit.log(userId, by, "SELLER_RESERVE_SET", "SellerReserve", sellerId, (percentBp / 100.0) + "%", null);
        return r;
    }

    /** Returns held money to the seller's balance, where the next run pays it out. */
    @Transactional
    public SellerReserve releaseReserve(Long sellerId, long amountPaise, String reason, Long userId, String by) {
        SellerReserve r = reserveRepo.findById(sellerId).orElseThrow(() -> new FinanceException("This seller has no reserve."));
        if (amountPaise <= 0 || amountPaise > r.getHeldPaise()) {
            throw new FinanceException("Release between Rs.0.01 and the Rs." + FinanceMath.rupees(r.getHeldPaise()) + " held.");
        }
        if (reason == null || reason.isBlank()) throw new FinanceException("Say why the reserve is being released.");
        r.setHeldPaise(r.getHeldPaise() - amountPaise);
        r.setUpdatedBy(by);
        reserveRepo.save(r);
        ledger.postManual(sellerId, LedgerEntry.RESERVE_RELEASE, amountPaise, "Reserve released: " + reason.trim(), by);
        audit.log(userId, by, "SELLER_RESERVE_RELEASED", "SellerReserve", sellerId,
                "Rs." + FinanceMath.rupees(amountPaise) + ": " + reason, null);
        return r;
    }

    private SellerReserve newReserve(Long sellerId) {
        SellerReserve r = new SellerReserve();
        r.setSellerId(sellerId);
        return r;
    }

    public Optional<SellerReserve> reserveOf(Long sellerId) { return reserveRepo.findById(sellerId); }

    // --------------------------------------------------------- adjustments

    @Transactional
    public LedgerEntry adjust(Long sellerId, String type, long amountPaise, String reason, Long userId, String by) {
        if (!Set.of(LedgerEntry.ADJUSTMENT, LedgerEntry.PENALTY).contains(type)) throw new FinanceException("Choose adjustment or penalty.");
        if (reason == null || reason.trim().length() < 5) throw new FinanceException("Give a reason of at least a few words; the seller sees it.");
        if (amountPaise == 0) throw new FinanceException("The amount cannot be zero.");
        if (Math.abs(amountPaise) > 1_000_000_00L) throw new FinanceException("That amount is unusually large. Check it.");
        if (LedgerEntry.PENALTY.equals(type) && amountPaise > 0) amountPaise = -amountPaise;
        if (userRepo.findById(sellerId).filter(u -> u.getRole() == Role.SELLER).isEmpty()) {
            throw new FinanceException("No seller with id " + sellerId + ".");
        }
        LedgerEntry e = ledger.postManual(sellerId, type, amountPaise, reason.trim(), by);
        audit.log(userId, by, "LEDGER_" + type, "LedgerEntry", e.getId(),
                "Rs." + FinanceMath.rupees(amountPaise) + " seller " + sellerId + ": " + reason, null);
        return e;
    }

    // -------------------------------------------------------- reconciliation

    /** One line from the bank statement. */
    public record StatementLine(String reference, long amountPaise) { }

    /** Matches statement lines to recorded payments; matched payments are stamped reconciled. */
    @Transactional
    public List<Map<String, Object>> reconcile(List<StatementLine> lines, Long userId, String by) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (StatementLine l : lines) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reference", l.reference());
            m.put("amount", FinanceMath.rupees(l.amountPaise()));
            List<SellerPayout> found = payoutRepo.findByReferenceIgnoreCaseAndVoidedFalse(l.reference());
            if (found.isEmpty()) {
                m.put("result", "NOT_RECORDED");
                m.put("detail", "The bank shows a payment we have no record of.");
            } else if (found.size() > 1) {
                m.put("result", "DUPLICATE_REFERENCE");
                m.put("detail", "More than one payment uses this reference.");
            } else {
                SellerPayout p = found.get(0);
                if (FinanceMath.paise(p.getAmount()) == l.amountPaise()) {
                    if (p.getReconciledAt() == null) { p.setReconciledAt(LocalDateTime.now()); payoutRepo.save(p); }
                    m.put("result", "MATCHED");
                    m.put("detail", "Seller " + p.getSellerId() + ", payout #" + p.getId());
                } else {
                    m.put("result", "AMOUNT_DIFFERS");
                    m.put("detail", "We recorded Rs." + p.getAmount() + " for seller " + p.getSellerId() + ".");
                }
            }
            out.add(m);
        }
        audit.log(userId, by, "BANK_RECONCILIATION", "SellerPayout", null, lines.size() + " statement lines", null);
        return out;
    }

    private SettlementRun run(Long id) {
        return runRepo.findById(id).orElseThrow(() -> new FinanceException("No run with id " + id + "."));
    }
}
