package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import com.cauverystore.util.CommissionPolicy;
import com.cauverystore.util.FinanceMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * The seller money ledger: what each delivered order and each refund did to what a seller is owed.
 *
 * Posting is idempotent (every line has a unique key) and never edits history. Orders are posted
 * once they are delivered, and only from the date the finance team set as the ledger's start, so
 * switching this on does not re-open orders that were already paid by hand.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);
    public static final String START_KEY = "finance.ledgerStartDate";

    /** Razorpay keeps its fee when it refunds a customer. True would hand that cost back to the seller; it is a business decision. */
    static final boolean REVERSE_PAYMENT_FEES_ON_REFUND = false;

    private final LedgerEntryRepository ledgerRepo;
    private final OrderRepository orderRepo;
    private final RefundRepository refundRepo;
    private final TcsRecordRepository tcsRepo;
    private final CommissionInvoiceService commissionService;
    private final PlatformSettingRepository settingRepo;

    public LedgerService(LedgerEntryRepository ledgerRepo, OrderRepository orderRepo, RefundRepository refundRepo,
                         TcsRecordRepository tcsRepo, CommissionInvoiceService commissionService,
                         PlatformSettingRepository settingRepo) {
        this.ledgerRepo = ledgerRepo;
        this.orderRepo = orderRepo;
        this.refundRepo = refundRepo;
        this.tcsRepo = tcsRepo;
        this.commissionService = commissionService;
        this.settingRepo = settingRepo;
    }

    /** What a sync found. */
    public record SyncResult(boolean configured, int ordersPosted, int refundsPosted,
                             List<Long> ordersWithoutRate, List<Long> refundsSkipped) { }

    public Optional<LocalDate> startDate() {
        return settingRepo.findById(START_KEY).map(PlatformSetting::getSettingValue)
                .filter(v -> v != null && !v.isBlank()).map(v -> LocalDate.parse(v.trim()));
    }

    @Transactional
    public void setStartDate(LocalDate date) {
        PlatformSetting s = settingRepo.findById(START_KEY).orElseGet(PlatformSetting::new);
        s.setSettingKey(START_KEY);
        s.setSettingValue(date.toString());
        s.setCategory("finance");
        s.setDescription("Delivered orders from this date are booked to the seller ledger.");
        settingRepo.save(s);
    }

    @Transactional
    @Scheduled(initialDelay = 300_000, fixedDelayString = "${finance.ledger.sync-ms:3600000}")
    public void scheduledSync() {
        try {
            SyncResult r = sync();
            if (r.ordersPosted() + r.refundsPosted() > 0) {
                log.info("Ledger sync posted {} orders and {} refunds", r.ordersPosted(), r.refundsPosted());
            }
        } catch (Exception e) {
            log.warn("Ledger sync failed: {}", e.getMessage());
        }
    }

    /** Books every delivered order and refund not yet in the ledger. Safe to run as often as wanted. */
    @Transactional
    public SyncResult sync() {
        Optional<LocalDate> start = startDate();
        if (start.isEmpty()) return new SyncResult(false, 0, 0, List.of(), List.of());

        int orders = 0, refunds = 0;
        List<Long> noRate = new ArrayList<>();
        for (Order o : orderRepo.findByStatusIn(List.of("DELIVERED"))) {
            if (o.getSellerId() == null || o.getDeliveredAt() == null) continue;
            if (o.getDeliveredAt().toLocalDate().isBefore(start.get())) continue;
            if (ledgerRepo.existsByRefKey(key(o.getId(), LedgerEntry.SALE))) continue;
            if (postOrder(o)) orders++; else noRate.add(o.getId());
        }

        List<Long> skipped = new ArrayList<>();
        for (Refund r : refundRepo.findAll()) {
            if (r.getOrderId() == null || r.getAmount() == null || r.getAmount() <= 0) continue;
            if (ledgerRepo.existsByRefKey(refundKey(r.getId(), LedgerEntry.SALE))) continue;
            Order o = orderRepo.findById(r.getOrderId()).orElse(null);
            if (o == null || o.getSellerId() == null) continue;
            // Nothing to reverse if the order never reached the ledger (cancelled before delivery).
            if (!ledgerRepo.existsByRefKey(key(o.getId(), LedgerEntry.SALE))) { skipped.add(r.getId()); continue; }
            postRefund(o, r);
            refunds++;
        }
        return new SyncResult(true, orders, refunds, noRate, skipped);
    }

    /** @return false when no commission rate applies, so the order was left unposted. */
    @Transactional
    public boolean postOrder(Order o) {
        Optional<CommissionInvoiceService.OrderCommission> quote = commissionService.quote(o);
        if (quote.isEmpty()) return false;

        long sale = FinanceMath.paise(o.getTotalAmount());
        long handling = FinanceMath.paise(quote.get().fixedFee());
        long commission = Math.max(0, FinanceMath.paise(quote.get().commission()) - handling);

        Optional<TcsRecord> tcsRow = tcsRepo.findByOrderIdAndEntryType(o.getId(), TcsRecord.ENTRY_COLLECTION);
        boolean tcsEstimated = tcsRow.isEmpty() || tcsRow.get().getTcsAmount() == null;
        long tcs = tcsEstimated ? FinanceMath.estimateTcs(sale) : FinanceMath.paise(tcsRow.get().getTcsAmount());

        FinanceMath.Breakdown b = FinanceMath.breakdown(sale, commission, handling, tcs);
        LocalDate delivered = o.getDeliveredAt().toLocalDate();
        LocalDate availableOn = CommissionPolicy.payableFrom(delivered);
        String ord = "Order #" + o.getId();

        add(o.getSellerId(), o.getId(), null, LedgerEntry.SALE, "ORDER", b.sale(), availableOn,
                key(o.getId(), LedgerEntry.SALE), ord + " delivered " + delivered, "system");
        add(o.getSellerId(), o.getId(), null, LedgerEntry.COMMISSION, "ORDER", -b.commission(), availableOn,
                key(o.getId(), LedgerEntry.COMMISSION), ord + " commission " + quote.get().percent() + "%", "system");
        add(o.getSellerId(), o.getId(), null, LedgerEntry.HANDLING_FEE, "ORDER", -b.handling(), availableOn,
                key(o.getId(), LedgerEntry.HANDLING_FEE), ord + " handling fee", "system");
        add(o.getSellerId(), o.getId(), null, LedgerEntry.PAYMENT_FEE, "ORDER", -b.paymentFees(), availableOn,
                key(o.getId(), LedgerEntry.PAYMENT_FEE), ord + " payment gateway and transfer fees (with GST)", "system");
        add(o.getSellerId(), o.getId(), null, LedgerEntry.GST_ON_FEES, "ORDER", -b.gstOnFees(), availableOn,
                key(o.getId(), LedgerEntry.GST_ON_FEES), ord + " GST on commission and handling", "system");
        add(o.getSellerId(), o.getId(), null, LedgerEntry.TCS, "ORDER", -b.tcs(), availableOn,
                key(o.getId(), LedgerEntry.TCS), ord + (tcsEstimated ? " tax collected at source (estimated)" : " tax collected at source"), "system");
        return true;
    }

    /** Reverses the share of an order's lines that a refund covers. */
    @Transactional
    public void postRefund(Order o, Refund r) {
        List<LedgerEntry> lines = ledgerRepo.findByOrderIdOrderByIdAsc(o.getId());
        long saleLine = 0;
        for (LedgerEntry e : lines) if (e.getRefundId() == null && LedgerEntry.SALE.equals(e.getEntryType())) saleLine = e.getAmountPaise();
        long refund = FinanceMath.paise(r.getAmount());
        boolean full = refund >= saleLine;
        LocalDate today = LocalDate.now();

        for (LedgerEntry e : lines) {
            if (e.getRefundId() != null || !"ORDER".equals(e.getSource())) continue;
            String type = e.getEntryType();
            if (LedgerEntry.PAYMENT_FEE.equals(type) && !REVERSE_PAYMENT_FEES_ON_REFUND) continue;
            if (LedgerEntry.HANDLING_FEE.equals(type) && !full) continue;
            long amount = -FinanceMath.prorate(e.getAmountPaise(), refund, saleLine);
            if (full && LedgerEntry.HANDLING_FEE.equals(type)) amount = -e.getAmountPaise();
            if (amount == 0) continue;
            add(o.getSellerId(), o.getId(), r.getId(), type, "REFUND", amount, today,
                    refundKey(r.getId(), type), "Refund #" + r.getId() + " on order #" + o.getId()
                            + (full ? " (full)" : " (part)"), "system");
        }
        // Keep the marker line even for a refund that reverses nothing else, so it is never posted twice.
        if (!ledgerRepo.existsByRefKey(refundKey(r.getId(), LedgerEntry.SALE))) {
            add(o.getSellerId(), o.getId(), r.getId(), LedgerEntry.SALE, "REFUND", -refund, today,
                    refundKey(r.getId(), LedgerEntry.SALE), "Refund #" + r.getId() + " on order #" + o.getId(), "system");
        }
    }

    /** A manual line (correction, penalty, reserve release). {@code amountPaise} is signed from the seller's point of view. */
    @Transactional
    public LedgerEntry postManual(Long sellerId, String type, long amountPaise, String description, String by) {
        if (amountPaise == 0) throw new IllegalArgumentException("The amount cannot be zero.");
        return add(sellerId, null, null, type, "MANUAL", amountPaise, LocalDate.now(),
                "MANUAL:" + UUID.randomUUID(), description, by);
    }

    private LedgerEntry add(Long sellerId, Long orderId, Long refundId, String type, String source, long amount,
                            LocalDate availableOn, String refKey, String description, String by) {
        LedgerEntry e = new LedgerEntry();
        e.setSellerId(sellerId);
        e.setOrderId(orderId);
        e.setRefundId(refundId);
        e.setEntryType(type);
        e.setSource(source);
        e.setAmountPaise(amount);
        e.setAvailableOn(availableOn);
        e.setRefKey(refKey);
        e.setDescription(description != null && description.length() > 255 ? description.substring(0, 255) : description);
        e.setCreatedBy(by);
        return ledgerRepo.save(e);
    }

    static String key(Long orderId, String type) { return "ORDER:" + orderId + ":" + type; }
    static String refundKey(Long refundId, String type) { return "REFUND:" + refundId + ":" + type; }

    /** [available, pending] in paise for each seller with an unpaid balance. */
    public Map<Long, long[]> balances() {
        Map<Long, long[]> out = new LinkedHashMap<>();
        for (Object[] row : ledgerRepo.balances(LocalDate.now())) {
            out.put(((Number) row[0]).longValue(), new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return out;
    }
}
