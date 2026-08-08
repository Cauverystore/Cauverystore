package com.cauverystore.service;

import com.cauverystore.entities.TcsRecord;
import com.cauverystore.repository.TcsRecordRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * GSTR-8 - the marketplace's own monthly return of tax collected at source.
 *
 * <h2>Why this is shaped the way it is</h2>
 *
 * Not from a guide or an article. The columns below are taken from the government's own
 * GSTR_8_Offline_Utility.xlsm, sheets "3 TCS" and "3.1 UNRD", which is the file the return is
 * actually uploaded from. Anything that does not match those headings is rejected at upload, so
 * a readable report in our own field names would have been useless for the one purpose this
 * serves.
 *
 * <h2>What the utility asks for that a TCS ledger does not obviously carry</h2>
 *
 * Table 3 is one row per <em>supplier GSTIN and place of supply</em> - not per transaction, and
 * not per supplier. A ledger without the place of supply cannot be turned into this return at
 * all, which is why TcsRecord now records it.
 *
 * Within each row it wants supplies to registered and unregistered persons in separate columns,
 * each with its own returns figure, because section 52 charges TCS on the <em>net</em> of the
 * month. Returns are already held as negative rows, so netting is a sum.
 *
 * Table 3.1 is a separate table for suppliers trading on an enrolment number under Notification
 * 34/2023 rather than a GSTIN. It is empty here and will stay empty until such sellers are
 * supported - but it is generated rather than omitted, because a filer needs to see that the
 * table exists and is nil rather than wonder whether it was forgotten.
 */
@Service
public class Gstr8ExportService {

    private final TcsRecordRepository tcsRepo;

    public Gstr8ExportService(TcsRecordRepository tcsRepo) {
        this.tcsRepo = tcsRepo;
    }

    /** One row of table 3, accumulating the month for a supplier in one state. */
    private static class Bucket {
        String supplierGstin;
        String placeOfSupply;
        double grossRegistered, returnedRegistered;
        double grossUnregistered, returnedUnregistered;
        double tcsIgst, tcsCentral, tcsState;
    }

    /**
     * Table 3 for a period, in the utility's own column names.
     *
     * @param period as the utility writes it, MMyyyy
     */
    public List<Map<String, Object>> table3(String period) {
        Map<String, Bucket> buckets = new TreeMap<>();

        for (TcsRecord r : tcsRepo.findAll()) {
            if (period != null && !period.equals(r.getPeriod())) continue;
            if (r.getSellerGstin() == null || r.getSellerGstin().isBlank()) continue;

            String pos = r.getPlaceOfSupplyState() == null ? "" : r.getPlaceOfSupplyState();
            String key = r.getSellerGstin() + "|" + pos;
            Bucket b = buckets.computeIfAbsent(key, k -> {
                Bucket fresh = new Bucket();
                fresh.supplierGstin = r.getSellerGstin();
                fresh.placeOfSupply = pos;
                return fresh;
            });

            double taxable = nz(r.getTaxableAmount());
            double tcs = nz(r.getTcsAmount());

            // Reversals are held as negative rows, so a return shows up as a negative taxable
            // value. The utility wants it as a positive figure in its own "returned" column.
            if (r.isReversal()) {
                if (r.isCustomerRegistered()) b.returnedRegistered += Math.abs(taxable);
                else b.returnedUnregistered += Math.abs(taxable);
            } else {
                if (r.isCustomerRegistered()) b.grossRegistered += taxable;
                else b.grossUnregistered += taxable;
            }

            // TCS nets across collections and reversals, and the utility splits it by whether
            // the supply was inter-state - integrated tax on one side, central and state tax
            // halved on the other. Done per row because one supplier can have both in a month.
            if (r.isInterStateSupply()) {
                b.tcsIgst += tcs;
            } else {
                b.tcsCentral += tcs / 2.0;
                b.tcsState += tcs / 2.0;
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Bucket b : buckets.values()) {
            double net = (b.grossRegistered - b.returnedRegistered)
                    + (b.grossUnregistered - b.returnedUnregistered);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("GSTIN of supplier", b.supplierGstin);
            row.put("POS", b.placeOfSupply);
            row.put("Gross value of supplies made to registered persons", round(b.grossRegistered));
            row.put("Value of supplies returned by registered persons", round(b.returnedRegistered));
            row.put("Gross value of supplies made to unregistered persons", round(b.grossUnregistered));
            row.put("Value of supplies returned by unregistered persons", round(b.returnedUnregistered));
            row.put("Net amount liable for TCS", round(net));
            row.put("Integrated tax", round(b.tcsIgst));
            row.put("Central tax", round(b.tcsCentral));
            row.put("State/UT tax", round(b.tcsState));
            row.put("Amount of TCS", round(b.tcsIgst + b.tcsCentral + b.tcsState));
            rows.add(row);
        }
        return rows;
    }

    /**
     * Table 3.1 - supplies by suppliers trading on an enrolment number.
     *
     * Always empty today. Notification 34/2023 sellers are not onboarded, and returning an
     * empty table is the honest answer: it says the table was considered and is nil, which is
     * different from it being left out.
     */
    public List<Map<String, Object>> table3_1(String period) {
        return List.of();
    }

    /** The whole return, so a filer can see both tables and what is nil. */
    public Map<String, Object> forPeriod(String period) {
        List<Map<String, Object>> t3 = table3(period);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period);
        out.put("table3_suppliesThroughOperator", t3);
        out.put("table3_1_unregisteredSuppliers", table3_1(period));
        out.put("format", "Columns follow GSTR_8_Offline_Utility.xlsm sheets '3 TCS' and "
                + "'3.1 UNRD'. Anything else is rejected at upload.");

        List<String> problems = new ArrayList<>();
        long missingPos = t3.stream().filter(r -> String.valueOf(r.get("POS")).isBlank()).count();
        if (missingPos > 0) {
            // POS is mandatory in the utility. Saying so here beats finding out at upload.
            problems.add(missingPos + " row(s) have no place of supply. POS is a mandatory field "
                    + "in table 3 and the return will be rejected without it. These are supplies "
                    + "invoiced before the place of supply was recorded on the TCS ledger.");
        }
        out.put("blockingIssues", problems);
        out.put("readyToFile", problems.isEmpty() && !t3.isEmpty());
        return out;
    }

    private double nz(Double d) { return d == null ? 0.0 : d; }

    private double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
