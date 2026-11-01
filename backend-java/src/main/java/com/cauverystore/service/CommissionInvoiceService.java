package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import com.cauverystore.util.CommissionPolicy;
import com.cauverystore.util.GstComplianceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bills sellers for the marketplace's commission.
 *
 * This is the platform's own outward supply - a taxable service to the seller - and the third
 * of the three documents an order generates, alongside the seller's tax invoice to the consumer
 * and the TCS collection. Until now it did not exist at all, which left the platform's output
 * tax undeclared and sellers unable to claim credit on fees they had already paid.
 *
 * Two things here differ from the product invoice and are easy to get backwards:
 *
 *   Place of supply follows the SELLER, because the marketplace is supplying them. A Tamil Nadu
 *   marketplace billing a Tamil Nadu seller charges CGST+SGST even where the order underneath
 *   shipped to Karnataka on IGST.
 *
 *   Commission is billed monthly in aggregate, not per order. One invoice per seller per month,
 *   which is also what makes re-running a month safe.
 */
@Service
public class CommissionInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(CommissionInvoiceService.class);

    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("MMyyyy");

    private final CommissionInvoiceRepository invoiceRepo;
    private final CommissionRateRepository rateRepo;
    private final GstConfigurationRepository configRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final OrderRepository orderRepo;
    private final GstInvoiceRepository gstInvoiceRepo;
    private final AuditService auditService;

    public CommissionInvoiceService(CommissionInvoiceRepository invoiceRepo,
                                    CommissionRateRepository rateRepo,
                                    GstConfigurationRepository configRepo,
                                    SellerRegistrationRepository sellerRegRepo,
                                    OrderRepository orderRepo,
                                    GstInvoiceRepository gstInvoiceRepo,
                                    AuditService auditService) {
        this.invoiceRepo = invoiceRepo;
        this.rateRepo = rateRepo;
        this.configRepo = configRepo;
        this.sellerRegRepo = sellerRegRepo;
        this.orderRepo = orderRepo;
        this.gstInvoiceRepo = gstInvoiceRepo;
        this.auditService = auditService;
    }

    /** Raised when a commission invoice cannot be issued correctly. */
    public static class CommissionException extends RuntimeException {
        public CommissionException(String message) { super(message); }
    }

    /**
     * The commission rate for a seller and category on a date.
     *
     * Most specific in-force row wins: seller and category, then category, then the platform
     * default. Returns empty rather than assuming zero when nothing matches - a silent zero
     * would issue a nil invoice and quietly forgo the fee.
     */
    public Optional<CommissionRate> resolveRate(Long sellerId, Long categoryId, LocalDate on) {
        LocalDate date = on != null ? on : LocalDate.now();
        return rateRepo.findBySellerIdIsNullOrSellerId(sellerId).stream()
                .filter(r -> r.isInForce(date))
                .filter(r -> r.appliesTo(sellerId, categoryId))
                .max(Comparator.comparingInt(CommissionRate::specificity)
                        .thenComparing(CommissionRate::getEffectiveFrom));
    }

    /**
     * Issues one seller's commission invoice for a month.
     *
     * Idempotent: a period already billed is returned rather than billed twice. Re-running a
     * month must not double-charge a seller, and month-end jobs get re-run.
     */
    @Transactional
    public CommissionInvoice generateForSeller(Long sellerId, String period, String actorEmail) {
        Optional<CommissionInvoice> existing = invoiceRepo.findBySellerIdAndPeriod(sellerId, period);
        if (existing.isPresent()) {
            log.info("Commission for seller {} in {} is already invoiced as {}.",
                    sellerId, period, existing.get().getInvoiceNumber());
            return existing.get();
        }

        GstConfiguration config = configRepo.findAll().stream().findFirst()
                .orElseThrow(() -> new CommissionException(
                        "No marketplace GST configuration exists, so there is no supplier to "
                                + "invoice from. Set one up before billing commission."));

        SellerRegistration seller = sellerRegRepo.findByUserId(sellerId)
                .orElseThrow(() -> new CommissionException(
                        "Seller " + sellerId + " has no registration, so there is no place of "
                                + "supply for the commission invoice."));

        String sellerStateCode = resolveStateCode(seller);
        if (sellerStateCode == null) {
            throw new CommissionException(
                    "Seller " + sellerId + " has no state on their registration. The commission "
                            + "invoice's place of supply is the seller's state, so it cannot be "
                            + "raised without one.");
        }

        YearMonth month = YearMonth.parse(period, PERIOD);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        List<CommissionInvoiceLine> lines = buildLines(sellerId, from, to);
        if (lines.isEmpty()) {
            throw new CommissionException("Seller " + sellerId + " has no billable orders in "
                    + period + ", so there is nothing to invoice.");
        }

        double taxable = round(lines.stream().mapToDouble(CommissionInvoiceLine::getCommissionAmount).sum());
        String marketplaceState = config.getStateCode();
        boolean interState = marketplaceState != null && !marketplaceState.equals(sellerStateCode);
        double gstRate = config.getCommissionGstRate() != null ? config.getCommissionGstRate() : 18.0;

        CommissionInvoice inv = new CommissionInvoice();
        inv.setInvoiceNumber(nextInvoiceNumber(month));
        inv.setInvoiceDate(LocalDate.now());
        inv.setPeriod(period);
        inv.setMarketplaceGstin(config.getGstin());
        inv.setMarketplaceLegalName(config.getLegalName());
        inv.setMarketplaceStateCode(marketplaceState);
        inv.setSellerId(sellerId);
        inv.setSellerGstin(seller.getGstin());
        inv.setSellerLegalName(seller.getBusinessName());
        inv.setSellerStateCode(sellerStateCode);
        inv.setSacCode(config.getCommissionSacCode() != null ? config.getCommissionSacCode() : "998599");
        inv.setTaxableAmount(taxable);
        inv.setGstRate(gstRate);
        inv.setIsInterState(interState);
        // A marketplace registered in a Union Territory without a legislature charges UTGST
        // instead of SGST on intra-UT commission invoices. The amount stays in the state-tax
        // bucket (GSTN reports both under "State/UT Tax"); the flag only drives the label.
        inv.setUtgstApplied(!interState && GstComplianceUtil.isUtgstState(marketplaceState));

        applyTaxSplit(inv, taxable, gstRate, interState);

        inv.setTotalAmount(round(taxable + inv.getTotalTax()));
        for (CommissionInvoiceLine line : lines) line.setCommissionInvoice(inv);
        inv.setLines(lines);

        CommissionInvoice saved = invoiceRepo.save(inv);
        auditService.log(null, actorEmail != null ? actorEmail : "system",
                "COMMISSION_INVOICE_GENERATED", "CommissionInvoice", saved.getId(),
                "Commission invoice " + saved.getInvoiceNumber() + " for seller " + sellerId
                        + " covering " + period + " (" + lines.size() + " orders)", null);
        return saved;
    }

    /**
     * Splits the commission tax.
     *
     * Same rule as a product invoice - halves for intra-state, the whole as IGST for
     * inter-state - but decided against the SELLER's state, since they are the recipient.
     * The total is summed from the rounded halves so the invoice foots.
     */
    private void applyTaxSplit(CommissionInvoice inv, double taxable, double gstRate, boolean interState) {
        if (interState) {
            double igst = round(taxable * gstRate / 100.0);
            inv.setIgstAmount(igst);
            inv.setCgstAmount(0.0);
            inv.setSgstAmount(0.0);
            inv.setTotalTax(igst);
        } else {
            double half = round(taxable * (gstRate / 2.0) / 100.0);
            inv.setCgstAmount(half);
            inv.setSgstAmount(half);
            inv.setIgstAmount(0.0);
            inv.setTotalTax(round(half + half));
        }
    }

    /**
     * One line per billable order in the month.
     *
     * Cancelled and returned orders are excluded: commission follows the sale, and charging a
     * fee on an order the customer sent back would take money for a supply that did not stand.
     */
    private List<CommissionInvoiceLine> buildLines(Long sellerId, LocalDate from, LocalDate to) {
        List<CommissionInvoiceLine> lines = new ArrayList<>();
        for (Order order : orderRepo.findAll()) {
            if (!sellerId.equals(order.getSellerId())) continue;
            if (order.getCreatedAt() == null) continue;
            LocalDate placed = order.getCreatedAt().toLocalDate();
            if (placed.isBefore(from) || placed.isAfter(to)) continue;
            if (!isBillable(order)) continue;

            Long categoryId = categoryOf(order);
            Optional<CommissionRate> rate = resolveRate(sellerId, categoryId, placed);
            if (rate.isEmpty()) {
                log.warn("No commission rate applies to seller {} category {} on {} - order {} "
                        + "left unbilled rather than charged at zero.",
                        sellerId, categoryId, placed, order.getId());
                continue;
            }

            double orderValue = nz(order.getTotalAmount());
            CommissionRate r = rate.get();
            // One percentage for the order, weighted across its items, so a price floor or
            // items from different categories are charged at their own rates.
            double percent = blendedPercentFor(order, sellerId, placed, r);
            double commission = round(orderValue * percent / 100.0 + nz(r.getFixedFee()));

            CommissionInvoiceLine line = new CommissionInvoiceLine();
            line.setOrderId(order.getId());
            line.setGstInvoiceId(gstInvoiceRepo.findByOrderId(order.getId())
                    .map(GstInvoice::getId).orElse(null));
            line.setOrderValue(orderValue);
            line.setRatePercent(percent);
            line.setFixedFee(nz(r.getFixedFee()));
            line.setCommissionAmount(commission);
            lines.add(line);
        }
        return lines;
    }

    /**
     * The percentage for an order: each item at the rate for its own category and price,
     * weighted by what the item contributes. Falls back to the order's resolved rate for an
     * item whose category has no rate of its own, and for an order with nothing to weigh.
     */
    private double blendedPercentFor(Order order, Long sellerId, LocalDate placed, CommissionRate orderRate) {
        List<OrderItem> items = order.getItems();
        if (items == null || items.isEmpty()) return orderRate.getRatePercent();

        double[] values = new double[items.size()];
        double[] percents = new double[items.size()];
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            values[i] = item.getPrice() * Math.max(item.getQuantity(), 0);
            Long itemCategory = item.getProduct() != null && item.getProduct().getCategory() != null
                    ? item.getProduct().getCategory().getId() : null;
            CommissionRate itemRate = resolveRate(sellerId, itemCategory, placed).orElse(orderRate);
            percents[i] = itemRate.percentFor(item.getPrice());
        }
        return CommissionPolicy.blendedPercent(values, percents, orderRate.getRatePercent());
    }

    // Optional collaborators, set by Spring; tests that build this service by hand leave them out
    // and the statement then shows no TCS or payouts rather than failing.
    private TcsRecordRepository tcsRepo;
    private SellerPayoutRepository payoutRepo;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSettlementSources(TcsRecordRepository tcsRepo, SellerPayoutRepository payoutRepo) {
        this.tcsRepo = tcsRepo;
        this.payoutRepo = payoutRepo;
    }

    /** Kept for the admin endpoint that already calls it; the statement is a superset. */
    public Map<String, Object> previewSettlement(Long sellerId, String period) {
        return settlementStatement(sellerId, period, LocalDate.now());
    }

    /**
     * A seller's settlement statement for one month: for each billable order, every deduction
     * between what the customer paid and what the seller receives, and against that, what has
     * been paid out.
     *
     * Worked out from records that are already kept - orders, the commission rate card, TCS
     * records, payouts - rather than stored again, so it always agrees with the commission
     * invoice and GSTR-8. The earlier preview left out two deductions: the GST on the
     * marketplace's fees (billed on the commission invoice) and TCS, so it overstated what was
     * owed by both.
     *
     * Cancelled and returned orders carry no commission and pay nothing, so they are not listed.
     */
    public Map<String, Object> settlementStatement(Long sellerId, String period, LocalDate today) {
        YearMonth month = YearMonth.parse(period, PERIOD);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<CommissionInvoiceLine> lines = buildLines(sellerId, from, to);

        // The same rate the commission invoice for this month charges.
        double gstRate = configRepo.findAll().stream().findFirst()
                .map(GstConfiguration::getCommissionGstRate).filter(r -> r != null).orElse(18.0);

        List<Map<String, Object>> rows = new ArrayList<>();
        double sale = 0, commissionTotal = 0, handlingTotal = 0, gstTotal = 0, feesTotal = 0,
                tcsTotal = 0, payableTotal = 0, dueNow = 0, notYetDue = 0;
        for (CommissionInvoiceLine line : lines) {
            double handling = nz(line.getFixedFee());
            double commission = line.getCommissionAmount() - handling;
            double tcs = tcsFor(line.getOrderId());
            CommissionPolicy.LedgerLine l =
                    CommissionPolicy.ledgerLine(line.getOrderValue(), commission, handling, gstRate, tcs);
            Order order = orderRepo.findById(line.getOrderId()).orElse(null);
            LocalDate delivered = order != null && order.getDeliveredAt() != null
                    ? order.getDeliveredAt().toLocalDate() : null;
            LocalDate payableFrom = CommissionPolicy.payableFrom(delivered);
            boolean due = payableFrom != null && !payableFrom.isAfter(today);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", line.getOrderId());
            row.put("gstInvoiceId", line.getGstInvoiceId());
            row.put("orderValue", l.orderValue());
            row.put("commissionPercent", line.getRatePercent());
            row.put("commission", l.commission());
            row.put("handlingFee", l.handlingFee());
            row.put("gstOnFees", l.gstOnFees());
            row.put("gatewayFee", l.gatewayFee());
            row.put("transferFee", l.transferFee());
            row.put("tcs", l.tcs());
            row.put("payable", l.payable());
            // Older name for the same figure, for callers of the earlier preview.
            row.put("sellerNet", l.payable());
            row.put("deliveredOn", delivered);
            row.put("payableFrom", payableFrom);
            row.put("due", due);
            rows.add(row);

            sale += l.orderValue();
            commissionTotal += l.commission();
            handlingTotal += l.handlingFee();
            gstTotal += l.gstOnFees();
            feesTotal += l.gatewayFee() + l.transferFee();
            tcsTotal += l.tcs();
            payableTotal += l.payable();
            if (due) dueNow += l.payable(); else notYetDue += l.payable();
        }

        List<Map<String, Object>> payouts = new ArrayList<>();
        double paid = 0;
        if (payoutRepo != null) {
            for (SellerPayout p : payoutRepo.findBySellerIdOrderByPaidOnDescIdDesc(sellerId)) {
                if (Boolean.TRUE.equals(p.getVoided()) || !coversMonth(p, from, to)) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.getId());
                m.put("paidOn", p.getPaidOn());
                m.put("amount", round(nz(p.getAmount())));
                m.put("reference", p.getReference());
                payouts.add(m);
                paid += nz(p.getAmount());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sellerId", sellerId);
        result.put("period", period);
        result.put("gstRateOnFees", gstRate);
        result.put("orders", rows);
        result.put("totalOrderValue", round(sale));
        result.put("totalCommission", round(commissionTotal));
        result.put("totalHandlingFees", round(handlingTotal));
        result.put("totalGstOnFees", round(gstTotal));
        result.put("totalPaymentFees", round(feesTotal));
        result.put("totalTcs", round(tcsTotal));
        result.put("totalPayable", round(payableTotal));
        result.put("totalSellerNet", round(payableTotal));
        result.put("payableNow", round(dueNow));
        result.put("notYetPayable", round(notYetDue));
        result.put("payouts", payouts);
        result.put("totalPaid", round(paid));
        result.put("balanceDue", round(dueNow - paid));
        result.put("note", "Payable = order value - commission - handling fee - GST on those fees - "
                + "payment gateway and transfer fees - TCS. An order is payable "
                + CommissionPolicy.PAYOUT_DELAY_DAYS + " days after delivery. TCS is credited to "
                + "your GSTIN and appears in your electronic cash ledger once the marketplace files GSTR-8.");
        return result;
    }

    /** Net TCS on an order: what was collected less any reversal on a return. */
    private double tcsFor(Long orderId) {
        if (tcsRepo == null || orderId == null) return 0.0;
        double t = 0;
        for (TcsRecord r : tcsRepo.findByOrderId(orderId)) {
            double amt = nz(r.getTcsAmount());
            t += TcsRecord.ENTRY_REVERSAL.equals(r.getEntryType()) ? -Math.abs(amt) : amt;
        }
        return round(t);
    }

    /**
     * Whether a payout belongs to this month: by the sales period it was recorded against, or,
     * when none was given, by the month it was paid in.
     */
    static boolean coversMonth(SellerPayout p, LocalDate from, LocalDate to) {
        if (p.getPeriodFrom() != null || p.getPeriodTo() != null) {
            LocalDate a = p.getPeriodFrom() != null ? p.getPeriodFrom() : p.getPeriodTo();
            LocalDate b = p.getPeriodTo() != null ? p.getPeriodTo() : p.getPeriodFrom();
            return !a.isAfter(to) && !b.isBefore(from);
        }
        return p.getPaidOn() != null && !p.getPaidOn().isBefore(from) && !p.getPaidOn().isAfter(to);
    }

    /** Commission follows a sale that stood - not one cancelled or sent back. */
    private boolean isBillable(Order order) {
        String status = order.getStatus();
        if (status == null) return false;
        String s = status.toUpperCase();
        return !s.contains("CANCEL") && !s.contains("RETURN") && !s.contains("REFUND");
    }

    private Long categoryOf(Order order) {
        if (order.getItems() == null) return null;
        return order.getItems().stream()
                .filter(i -> i.getProduct() != null && i.getProduct().getCategory() != null)
                .map(i -> i.getProduct().getCategory().getId())
                .findFirst().orElse(null);
    }

    private String resolveStateCode(SellerRegistration seller) {
        // The GSTIN's first two digits are the state, and are the most reliable source when
        // present - a typed state name can disagree with the registration it belongs to.
        if (seller.getGstinStateCode() != null && !seller.getGstinStateCode().isBlank()) {
            return seller.getGstinStateCode();
        }
        if (seller.getGstin() != null && seller.getGstin().length() >= 2) {
            return seller.getGstin().substring(0, 2);
        }
        return null;
    }

    /** Sequential within the month, capped at Rule 46's 16 characters. */
    private String nextInvoiceNumber(YearMonth month) {
        String prefix = "CM" + month.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int next = invoiceRepo.findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(prefix)
                .map(inv -> {
                    try {
                        return Integer.parseInt(inv.getInvoiceNumber().substring(prefix.length())) + 1;
                    } catch (NumberFormatException e) {
                        return 1;
                    }
                }).orElse(1);
        return prefix + String.format("%04d", next);
    }

    /** Every seller with billable activity in the period. */
    @Transactional
    public Map<String, Object> generateForPeriod(String period, String actorEmail) {
        List<Long> sellerIds = orderRepo.findAll().stream()
                .map(Order::getSellerId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        List<String> issued = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Long sellerId : sellerIds) {
            try {
                issued.add(generateForSeller(sellerId, period, actorEmail).getInvoiceNumber());
            } catch (CommissionException e) {
                // One seller's missing rate or registration must not stop the rest being billed.
                skipped.add("Seller " + sellerId + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("issued", issued);
        result.put("skipped", skipped);
        return result;
    }

    private static double nz(Double d) { return d == null ? 0.0 : d; }
    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
