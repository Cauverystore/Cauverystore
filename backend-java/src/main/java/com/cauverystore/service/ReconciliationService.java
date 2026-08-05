package com.cauverystore.service;

import com.cauverystore.client.GstnClient;
import com.cauverystore.entities.GstConfiguration;
import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.ReconciliationRecord;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ReconciliationService {

    private final ReconciliationRecordRepository reconRepo;
    private final GstInvoiceRepository invoiceRepo;
    private final GstConfigurationRepository configRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final GstnClient gstnClient;

    private static final List<String> BLOCKED_CREDIT_SECTIONS = List.of(
            "Goods lost, stolen, destroyed or written off",
            "Goods used for personal consumption",
            "Goods on which tax is paid under composition scheme",
            "Goods on which ITC is blocked as per Section 17(5)");

    public ReconciliationService(ReconciliationRecordRepository reconRepo, GstInvoiceRepository invoiceRepo,
                                 GstConfigurationRepository configRepo, SellerRegistrationRepository sellerRegRepo,
                                 GstnClient gstnClient) {
        this.reconRepo = reconRepo;
        this.invoiceRepo = invoiceRepo;
        this.configRepo = configRepo;
        this.sellerRegRepo = sellerRegRepo;
        this.gstnClient = gstnClient;
    }

    @Transactional
    public Map<String, Object> generateReconciliation(Long sellerId, String period) {
        String sellerGstin = resolveGstin(sellerId);
        final String periodKey = (period == null || period.isBlank()) ? currentPeriod() : period;

        Map<String, Object> gstr2b = gstnClient.fetchGstr2bData(sellerGstin, periodKey);

        YearMonth ym = YearMonth.parse(periodKey, DateTimeFormatter.ofPattern("MMyyyy"));
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        List<GstInvoice> invoices = invoiceRepo.findBySellerGstinAndInvoiceDateBetween(sellerGstin, start, end);

        double itcClaimedCgst = 0, itcClaimedSgst = 0, itcClaimedIgst = 0, itcClaimedTotal = 0;
        int eligibleInvoices = 0;
        for (GstInvoice inv : invoices) {
            if (Boolean.TRUE.equals(inv.getItcEligible())) {
                itcClaimedCgst += nz(inv.getCgstAmount());
                itcClaimedSgst += nz(inv.getSgstAmount());
                itcClaimedIgst += nz(inv.getIgstAmount());
                eligibleInvoices++;
            }
        }
        itcClaimedTotal = itcClaimedCgst + itcClaimedSgst + itcClaimedIgst;

        double itcAvailable = ((Number) gstr2b.getOrDefault("totalItcAvailable", 0)).doubleValue();
        double difference = round(itcAvailable - itcClaimedTotal);

        ReconciliationRecord rec = reconRepo.findBySellerGstinAndPeriod(sellerGstin, periodKey)
                .orElseGet(() -> {
                    ReconciliationRecord r = new ReconciliationRecord();
                    r.setSellerGstin(sellerGstin);
                    r.setPeriod(periodKey);
                    r.setGeneratedDate(LocalDate.now());
                    r.setStatus("OPEN");
                    return r;
                });
        rec.setTotalItcAvailable(round(itcAvailable));
        rec.setItcClaimed(round(itcClaimedTotal));
        rec.setItcReversed(0.0);
        rec.setItcNotAvailable(0.0);
        rec.setItcDifference(difference);
        rec.setBlockedCredits(String.join("; ", BLOCKED_CREDIT_SECTIONS));
        rec.setRemarks("GSTR-2B data is simulated. Configure live GSTN credentials for actual reconciliation.");
        ReconciliationRecord saved = reconRepo.save(rec);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reconciliation", saved);
        result.put("gstr2b", gstr2b);
        result.put("claimedFromInvoices", Map.of(
                "cgst", round(itcClaimedCgst),
                "sgst", round(itcClaimedSgst),
                "igst", round(itcClaimedIgst),
                "total", round(itcClaimedTotal),
                "eligibleInvoices", eligibleInvoices));
        result.put("difference", difference);
        result.put("status", Math.abs(difference) < 0.01 ? "MATCHED" : "MISMATCH");
        result.put("message", "Reconciliation generated for " + period);
        return result;
    }

    public Map<String, Object> getReconciliation(Long sellerId, String period) {
        String sellerGstin = resolveGstin(sellerId);
        ReconciliationRecord rec = reconRepo.findBySellerGstinAndPeriod(sellerGstin, period)
                .orElseThrow(() -> new RuntimeException("Reconciliation not found for period " + period));
        return Map.of("reconciliation", rec);
    }

    public Map<String, Object> listReconciliations(Long sellerId) {
        String sellerGstin = resolveGstin(sellerId);
        List<ReconciliationRecord> records = reconRepo.findBySellerGstinOrderByPeriodDesc(sellerGstin);
        return Map.of("content", records, "total", records.size());
    }

    @Transactional
    public Map<String, Object> updateReconciliationStatus(Long reconId, String status) {
        ReconciliationRecord rec = reconRepo.findById(reconId)
                .orElseThrow(() -> new RuntimeException("Reconciliation not found: " + reconId));
        rec.setStatus(status != null ? status : "OPEN");
        ReconciliationRecord saved = reconRepo.save(rec);
        return Map.of("reconciliation", saved, "message", "Reconciliation status updated");
    }

    private String resolveGstin(Long sellerId) {
        return configRepo.findBySellerId(sellerId).map(GstConfiguration::getGstin)
                .orElseGet(() -> sellerRegRepo.findByUserId(sellerId).map(SellerRegistration::getGstin).orElse(""));
    }

    private String currentPeriod() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("MMyyyy"));
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double nz(Double v) {
        return v != null ? v : 0.0;
    }
}
