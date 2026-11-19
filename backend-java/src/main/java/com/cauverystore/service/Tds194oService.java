package com.cauverystore.service;

import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.entities.TcsRecord;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.repository.TcsRecordRepository;
import com.cauverystore.util.Tds194oCalculator;
import com.cauverystore.util.Tds194oCalculator.Entry;
import com.cauverystore.util.Tds194oCalculator.Participant;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Income-tax TDS the marketplace must deduct from each seller's sales (section 194-O, now
 * section 393(1) Sl. No. 8(v) of the Income-tax Act 2025): a report per financial year.
 *
 * <p>It reads the TCS ledger that GSTR-8 is filed from, so the two returns can never disagree
 * about what a seller sold or returned. It writes nothing and does not touch payouts: payouts
 * are entered by an admin after the bank transfer, and this only says how much should have been
 * held back from the money owed. Depositing the tax and filing the TDS return are done outside
 * this system.
 *
 * <h2>What it cannot see</h2>
 * Only sales that carried section 52 TCS are in the ledger. A sale with no TCS row is missing
 * here too, and the report says so rather than implying it is complete.
 */
@Service
public class Tds194oService {

    private final TcsRecordRepository tcsRepo;
    private final SellerRegistrationRepository sellerRegRepo;

    public Tds194oService(TcsRecordRepository tcsRepo, SellerRegistrationRepository sellerRegRepo) {
        this.tcsRepo = tcsRepo;
        this.sellerRegRepo = sellerRegRepo;
    }

    /** @param fy the financial year as "2026-27" (or just "2026") */
    public Map<String, Object> report(String fy) {
        int start = parseFinancialYear(fy);
        LocalDate from = LocalDate.of(start, 4, 1);
        LocalDate to = LocalDate.of(start + 1, 3, 31);

        Map<Long, List<TcsRecord>> bySeller = new TreeMap<>();
        int withoutSeller = 0;
        for (TcsRecord r : tcsRepo.findByTransactionDateBetween(from, to)) {
            if (r.getSellerId() == null) { withoutSeller++; continue; }
            bySeller.computeIfAbsent(r.getSellerId(), k -> new ArrayList<>()).add(r);
        }

        List<Map<String, Object>> sellers = new ArrayList<>();
        double totalTds = 0;
        List<String> issues = new ArrayList<>();
        if (withoutSeller > 0) {
            issues.add(withoutSeller + " ledger row(s) have no seller recorded and are left out.");
        }

        for (Map.Entry<Long, List<TcsRecord>> e : bySeller.entrySet()) {
            Long sellerId = e.getKey();
            SellerRegistration reg = sellerRegRepo.findByUserId(sellerId).orElse(null);
            boolean constitutionKnown = reg != null && notBlank(reg.getConstitutionOfBusiness());
            boolean individualOrHuf = constitutionKnown && isIndividualOrHuf(reg.getConstitutionOfBusiness());
            boolean idFurnished = reg != null && (notBlank(reg.getPanNumber()) || notBlank(reg.getAadhaarNumber()));

            List<Entry> entries = new ArrayList<>();
            for (TcsRecord r : e.getValue()) {
                if (r.getTaxableAmount() == null || r.getTransactionDate() == null) continue;
                entries.add(new Entry(r.getId(), r.getReversesId(), r.getTransactionDate(),
                        Math.abs(r.getTaxableAmount()), r.isReversal()));
            }
            Tds194oCalculator.Result res =
                    Tds194oCalculator.compute(new Participant(individualOrHuf, idFurnished), entries);

            List<String> flags = new ArrayList<>();
            if (reg == null) flags.add("No seller registration found: treated as having no PAN/Aadhaar.");
            else if (!constitutionKnown) flags.add("Constitution of business not recorded: treated as not an "
                    + "individual/HUF, so no exemption applied. Record it to apply the Rs 5 lakh limit.");
            if (reg != null && !idFurnished) flags.add("No PAN or Aadhaar on file: higher rate applies.");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sellerId", sellerId);
            row.put("businessName", reg != null ? reg.getBusinessName() : null);
            row.put("individualOrHuf", individualOrHuf);
            row.put("panOrAadhaarFurnished", idFurnished);
            row.put("grossSales", res.grossSales());
            row.put("returns", res.returns());
            row.put("netSales", res.netSales());
            row.put("tdsToDeduct", res.tds());
            row.put("flags", flags);
            row.put("lines", res.lines());
            sellers.add(row);
            totalTds += res.tds();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("financialYear", start + "-" + String.format("%02d", (start + 1) % 100));
        out.put("from", from.toString());
        out.put("to", to.toString());
        out.put("sellers", sellers);
        out.put("totalTdsToDeduct", Math.round(totalTds * 100.0) / 100.0);
        out.put("issues", issues);
        out.put("notes", List.of(
                "Base is the sale value without GST, net of returns, from the same ledger GSTR-8 uses.",
                "Only sales that carried section 52 TCS appear here.",
                "Where a sale takes a seller past the Rs 5 lakh limit, tax is taken on that whole sale; "
                        + "confirm this with your CA.",
                "This report does not deposit the tax or file the TDS return."));
        return out;
    }

    static int parseFinancialYear(String fy) {
        if (fy == null || !fy.trim().matches("\\d{4}(-\\d{2})?")) {
            throw new IllegalArgumentException("Financial year must look like 2026-27");
        }
        int start = Integer.parseInt(fy.trim().substring(0, 4));
        if (fy.trim().length() == 7) {
            int end = Integer.parseInt(fy.trim().substring(5));
            if (end != (start + 1) % 100) {
                throw new IllegalArgumentException("Financial year must span consecutive years, like 2026-27");
            }
        }
        return start;
    }

    /** Sole proprietors are individuals for this purpose; a HUF is named as such. */
    static boolean isIndividualOrHuf(String constitution) {
        String c = constitution.toLowerCase(Locale.ROOT);
        return c.contains("proprietor") || c.contains("individual") || c.contains("huf")
                || c.contains("hindu undivided");
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
