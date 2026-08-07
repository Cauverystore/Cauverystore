package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
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
            double commission = round(orderValue * r.getRatePercent() / 100.0 + nz(r.getFixedFee()));

            CommissionInvoiceLine line = new CommissionInvoiceLine();
            line.setOrderId(order.getId());
            line.setGstInvoiceId(gstInvoiceRepo.findByOrderId(order.getId())
                    .map(GstInvoice::getId).orElse(null));
            line.setOrderValue(orderValue);
            line.setRatePercent(r.getRatePercent());
            line.setFixedFee(nz(r.getFixedFee()));
            line.setCommissionAmount(commission);
            lines.add(line);
        }
        return lines;
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
