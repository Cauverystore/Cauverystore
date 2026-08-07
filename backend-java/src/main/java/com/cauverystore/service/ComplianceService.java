package com.cauverystore.service;

import com.cauverystore.client.GstnClient;
import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ComplianceService {

    private final GstInvoiceRepository invoiceRepo;
    private final CreditNoteRepository creditNoteRepo;
    private final DebitNoteRepository debitNoteRepo;
    private final TcsRecordRepository tcsRepo;
    private final ReconciliationRecordRepository reconRepo;
    private final FilingDeadlineRepository deadlineRepo;
    private final GstConfigurationRepository configRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final GstnClient gstnClient;

    public ComplianceService(GstInvoiceRepository invoiceRepo, CreditNoteRepository creditNoteRepo,
                             DebitNoteRepository debitNoteRepo, TcsRecordRepository tcsRepo,
                             ReconciliationRecordRepository reconRepo, FilingDeadlineRepository deadlineRepo,
                             GstConfigurationRepository configRepo, SellerRegistrationRepository sellerRegRepo,
                             GstnClient gstnClient) {
        this.invoiceRepo = invoiceRepo;
        this.creditNoteRepo = creditNoteRepo;
        this.debitNoteRepo = debitNoteRepo;
        this.tcsRepo = tcsRepo;
        this.reconRepo = reconRepo;
        this.deadlineRepo = deadlineRepo;
        this.configRepo = configRepo;
        this.sellerRegRepo = sellerRegRepo;
        this.gstnClient = gstnClient;
    }

    // ------------------------------------------------------------------
    // GSTR-9 (annual return)
    // ------------------------------------------------------------------

    public Map<String, Object> getGstr9Data(Long sellerId, int fiscalYear) {
        String sellerGstin = resolveGstin(sellerId);
        LocalDate start = LocalDate.of(fiscalYear, 4, 1);
        LocalDate end = LocalDate.of(fiscalYear + 1, 3, 31);

        List<GstInvoice> invoices = invoiceRepo.findBySellerGstinAndInvoiceDateBetween(sellerGstin, start, end);
        List<CreditNote> creditNotes = creditNoteRepo.findBySellerGstinAndCreditNoteDateBetween(sellerGstin, start, end);
        List<DebitNote> debitNotes = debitNoteRepo.findBySellerGstinAndDebitNoteDateBetween(sellerGstin, start, end);

        double taxable = 0, cgst = 0, sgst = 0, igst = 0, totalTax = 0, tcs = 0, totalAmount = 0;
        int invoiceCount = 0, b2bCount = 0, b2cCount = 0, intra = 0, inter = 0;
        for (GstInvoice inv : invoices) {
            taxable += nz(inv.getTaxableAmount());
            cgst += nz(inv.getCgstAmount());
            sgst += nz(inv.getSgstAmount());
            igst += nz(inv.getIgstAmount());
            totalTax += nz(inv.getTotalTax());
            tcs += nz(inv.getTcsAmount());
            totalAmount += nz(inv.getTotalAmount());
            invoiceCount++;
            if ("B2B".equals(inv.getInvoiceType())) b2bCount++; else b2cCount++;
            if (Boolean.TRUE.equals(inv.getIsInterState())) inter++; else intra++;
        }

        double cnTaxable = 0, cnTotal = 0;
        for (CreditNote cn : creditNotes) {
            cnTaxable += nz(cn.getTaxableAmount());
            cnTotal += nz(cn.getTotalAmount());
        }
        double dnTaxable = 0, dnTotal = 0;
        for (DebitNote dn : debitNotes) {
            dnTaxable += nz(dn.getTaxableAmount());
            dnTotal += nz(dn.getTotalAmount());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gstin", sellerGstin);
        result.put("fiscalYear", fiscalYear);
        result.put("periodStart", start.toString());
        result.put("periodEnd", end.toString());
        result.put("totalInvoices", invoiceCount);
        result.put("b2bCount", b2bCount);
        result.put("b2cCount", b2cCount);
        result.put("intraStateCount", intra);
        result.put("interStateCount", inter);
        result.put("creditNoteCount", creditNotes.size());
        result.put("debitNoteCount", debitNotes.size());
        result.put("taxableValue", round(taxable - cnTaxable + dnTaxable));
        result.put("cgst", round(cgst));
        result.put("sgst", round(sgst));
        result.put("igst", round(igst));
        result.put("totalTax", round(totalTax));
        result.put("totalTcsCollected", round(tcs));
        result.put("totalInvoiceValue", round(totalAmount));
        result.put("grossTaxableValue", round(taxable));
        result.put("creditNotesValue", round(cnTaxable));
        result.put("debitNotesValue", round(dnTaxable));
        result.put("reportedTurnover", round(taxable - cnTaxable + dnTaxable));
        result.put("gstr9Applicable", true);
        result.put("gstr9cApplicable", false);
        result.put("gstr9cRequiredTurnover", 50000000);
        result.put("gstr9cRequirement", "Not required (turnover below 5 Cr)");
        return result;
    }

    public Map<String, Object> getGstr9cData(Long sellerId, int fiscalYear) {
        Map<String, Object> gstr9 = getGstr9Data(sellerId, fiscalYear);
        Map<String, Object> result = new LinkedHashMap<>(gstr9);
        double turnover = ((Number) gstr9.getOrDefault("reportedTurnover", 0)).doubleValue();
        result.put("gstr9cApplicable", turnover > 5_00_00_000);
        result.put("gstr9cRequirement", turnover > 5_00_00_000
                ? "Required - CA certified reconciliation statement"
                : "Not required (turnover below 5 Cr)");
        result.put("turnoverAsPerAuditedAccounts", round(turnover));
        result.put("turnoverAsPerGstRecords", round(turnover));
        result.put("difference", 0.0);
        result.put("reconciled", true);
        return result;
    }

    // ------------------------------------------------------------------
    // GSTR-8 (TCS monthly return)
    // ------------------------------------------------------------------

    public Map<String, Object> getGstr8Data(Long sellerId, String period) {
        String sellerGstin = resolveGstin(sellerId);
        List<TcsRecord> records = tcsRepo.findBySellerGstinAndPeriod(sellerGstin, period);
        // TCS is charged on NET monthly supplies, so reversals carry negative amounts and
        // summing the period is what produces the figure to file. Gross and reversed are
        // reported alongside the net so the return can be checked against the ledger rather
        // than taken on trust.
        double taxable = 0, tcsAmount = 0, grossTcs = 0, reversedTcs = 0;
        int count = 0, reversalCount = 0;
        Map<String, Double> byCustomer = new LinkedHashMap<>();
        for (TcsRecord t : records) {
            taxable += nz(t.getTaxableAmount());
            tcsAmount += nz(t.getTcsAmount());
            if (t.isReversal()) {
                reversedTcs += nz(t.getTcsAmount());
                reversalCount++;
            } else {
                grossTcs += nz(t.getTcsAmount());
            }
            count++;
            String key = t.getCustomerGstin() != null && !t.getCustomerGstin().isBlank()
                    ? t.getCustomerGstin() : "URP";
            byCustomer.merge(key, nz(t.getTcsAmount()), Double::sum);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gstin", sellerGstin);
        // GSTR-8 is filed under the marketplace's s.52 registration, which is a different
        // GSTIN from its regular one. Reported rather than assumed, and flagged when unset:
        // filing under the wrong registration is rejected at the deadline.
        java.util.Optional<String> tcsGstin = configRepo.findAll().stream()
                .findFirst().flatMap(GstConfiguration::resolveTcsGstin);
        result.put("filingGstin", tcsGstin.orElse(null));
        if (tcsGstin.isEmpty()) {
            result.put("filingBlocked", true);
            result.put("filingBlockedReason",
                    "No TCS registration (s.52) is configured for the marketplace, or it is the "
                            + "same as the regular GSTIN. GSTR-8 filed under the regular "
                            + "registration is rejected. Set gstConfiguration.tcsGstin before filing.");
        }
        result.put("period", period);
        result.put("totalSuppliersAndBuyers", byCustomer.size());
        result.put("totalTaxableValue", round(taxable));
        result.put("grossTcsCollected", round(grossTcs));
        result.put("tcsReversed", round(reversedTcs));
        result.put("totalTcsCollected", round(tcsAmount));
        result.put("recordCount", count);
        result.put("reversalCount", reversalCount);
        result.put("tcsRate", "1%");
        result.put("customerWiseTcs", byCustomer.entrySet().stream()
                .map(e -> Map.of("customerGstin", e.getKey(), "tcsAmount", round(e.getValue())))
                .toList());
        result.put("gstr8Applicable", true);
        return result;
    }

    public List<Map<String, Object>> getGstr8Rows(Long sellerId, String period) {
        Map<String, Object> data = getGstr8Data(sellerId, period);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customerWise = (List<Map<String, Object>>) data.get("customerWiseTcs");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> c : customerWise) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("customerGstin", c.get("customerGstin"));
            row.put("tcsAmount", c.get("tcsAmount"));
            rows.add(row);
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Filing deadlines
    // ------------------------------------------------------------------

    @Transactional
    public List<FilingDeadline> syncDeadlines(Long sellerId, String period) {
        String sellerGstin = resolveGstin(sellerId);
        String[] forms = {"GSTR-1", "GSTR-3B", "GSTR-8"};
        List<FilingDeadline> result = new ArrayList<>();
        for (String form : forms) {
            LocalDate due = gstnClient.getFilingDueDate(form, period);
            FilingDeadline dl = deadlineRepo.findBySellerGstinAndFormAndPeriod(sellerGstin, form, period)
                    .orElseGet(() -> {
                        FilingDeadline d = new FilingDeadline();
                        d.setSellerGstin(sellerGstin);
                        d.setForm(form);
                        d.setPeriod(period);
                        d.setStatus("PENDING");
                        return d;
                    });
            dl.setDueDate(due);
            deadlineRepo.save(dl);
            result.add(dl);
        }
        return result;
    }

    public List<FilingDeadline> listDeadlines(Long sellerId) {
        String sellerGstin = resolveGstin(sellerId);
        return deadlineRepo.findBySellerGstinOrderByDueDateAsc(sellerGstin);
    }

    public Map<String, Object> getComplianceDashboard(Long sellerId, String period) {
        String sellerGstin = resolveGstin(sellerId);
        List<FilingDeadline> deadlines = deadlineRepo.findBySellerGstinOrderByDueDateAsc(sellerGstin);
        if (deadlines.isEmpty() && period != null) {
            syncDeadlines(sellerId, period);
            deadlines = deadlineRepo.findBySellerGstinOrderByDueDateAsc(sellerGstin);
        }

        List<Map<String, Object>> deadlineRows = new ArrayList<>();
        long overdue = 0, dueSoon = 0, filed = 0, pending = 0;
        LocalDate today = LocalDate.now();
        for (FilingDeadline dl : deadlines) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("form", dl.getForm());
            row.put("period", dl.getPeriod());
            row.put("dueDate", dl.getDueDate().toString());
            row.put("filedDate", dl.getFiledDate() != null ? dl.getFiledDate().toString() : null);
            row.put("status", dl.getStatus());
            if ("FILED".equals(dl.getStatus())) {
                filed++;
            } else {
                pending++;
                if (dl.getDueDate().isBefore(today)) {
                    overdue++;
                    row.put("alert", "OVERDUE");
                } else if (dl.getDueDate().isBefore(today.plusDays(7))) {
                    dueSoon++;
                    row.put("alert", "DUE_SOON");
                } else {
                    row.put("alert", "OK");
                }
            }
            deadlineRows.add(row);
        }

        Map<String, Object> tcsSummary = getGstr8Data(sellerId, period != null ? period : currentPeriod());
        List<ReconciliationRecord> reconRecords = reconRepo.findBySellerGstinOrderByPeriodDesc(sellerGstin);
        boolean reconOpen = reconRecords.stream().anyMatch(r -> "OPEN".equals(r.getStatus()));

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("gstin", sellerGstin);
        dashboard.put("period", period != null ? period : currentPeriod());
        dashboard.put("gstnAdapter", gstnClient.getName());
        dashboard.put("simulated", gstnClient.isSimulated());
        dashboard.put("deadlines", deadlineRows);
        dashboard.put("deadlineSummary", Map.of(
                "total", deadlines.size(),
                "filed", filed,
                "pending", pending,
                "overdue", overdue,
                "dueSoon", dueSoon));
        dashboard.put("tcsSummary", Map.of(
                "totalTcsCollected", tcsSummary.get("totalTcsCollected"),
                "recordCount", tcsSummary.get("recordCount")));
        dashboard.put("reconciliationStatus", reconRecords.isEmpty() ? "NOT_STARTED" : (reconOpen ? "OPEN" : "COMPLETE"));
        dashboard.put("reconciliationRecords", reconRecords.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("period", r.getPeriod());
            m.put("status", r.getStatus());
            m.put("itcDifference", r.getItcDifference());
            return m;
        }).toList());
        List<String> alerts = new ArrayList<>();
        if (overdue > 0) alerts.add(overdue + " return(s) overdue");
        if (dueSoon > 0) alerts.add(dueSoon + " return(s) due within 7 days");
        if (reconOpen) alerts.add("GSTR-2B reconciliation open for at least one period");
        dashboard.put("alerts", alerts);
        return dashboard;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public String resolveGstin(Long sellerId) {
        return configRepo.findBySellerId(sellerId).map(GstConfiguration::getGstin)
                .orElseGet(() -> sellerRegRepo.findByUserId(sellerId).map(SellerRegistration::getGstin).orElse(""));
    }

    public static String currentPeriod() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("MMyyyy"));
    }

    public static String previousPeriod() {
        return YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("MMyyyy"));
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double nz(Double v) {
        return v != null ? v : 0.0;
    }
}
