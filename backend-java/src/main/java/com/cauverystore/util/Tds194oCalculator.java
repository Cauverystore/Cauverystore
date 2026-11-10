package com.cauverystore.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Income-tax TDS on sales a seller makes through the marketplace: section 194-O of the
 * Income-tax Act 1961, section 393(1) Sl. No. 8(v) of the Income-tax Act 2025 from 1 April 2026.
 *
 * <p>Pure arithmetic with no Spring or database, so a CA can read it and a test can pin it. The
 * rate, the no-PAN rate and the rupee limit all come from {@link StatutoryRates}, by the date of
 * each sale; nothing is defaulted here.
 *
 * <h2>The rules this applies</h2>
 * <ul>
 *   <li><b>Base:</b> the sale value without GST (the caller passes the taxable value). Returns
 *       reduce the tax by reversing the tax charged on the sale they cancel.</li>
 *   <li><b>Exemption:</b> an individual or HUF who has furnished PAN or Aadhaar and whose gross
 *       sales through the marketplace in the financial year have not exceeded the limit (Rs 5
 *       lakh) has no tax deducted.</li>
 *   <li><b>Higher rate:</b> a participant who has furnished neither PAN nor Aadhaar is charged
 *       the higher rate from the first rupee, and never gets the exemption.</li>
 *   <li><b>Companies, firms and LLPs</b> get no exemption: tax from the first rupee.</li>
 * </ul>
 *
 * <h2>The one judgement call</h2>
 * When a sale is the one that takes the year's gross past the limit, this deducts on that whole
 * sale. Earlier sales in the year, which were exempt when made, are not reopened. Deducting only
 * on the part above the limit is the other reading; the result carries a note on the sale where
 * this happens so the CA can confirm which treatment to follow.
 */
public final class Tds194oCalculator {

    private Tds194oCalculator() { }

    /** Facts about the seller that decide the exemption. */
    public record Participant(boolean individualOrHuf, boolean panOrAadhaarFurnished) { }

    /**
     * One ledger row. {@code taxable} is always a positive magnitude; a return sets
     * {@code reversal} and points {@code reversesId} at the sale it cancels.
     */
    public record Entry(Long id, Long reversesId, LocalDate date, double taxable, boolean reversal) { }

    /** The result for one ledger row. {@code tds} is negative on a return. */
    public record Line(Long id, LocalDate date, boolean reversal, double taxable,
                       double ratePercent, double tds, String note) { }

    public record Result(List<Line> lines, double grossSales, double returns, double tds) {
        /** Gross sales less returns: what the tax is on, before any exemption. */
        public double netSales() { return round2(grossSales - returns); }
    }

    public static Result compute(Participant participant, List<Entry> entries) {
        List<Entry> ordered = new ArrayList<>(entries);
        // Sales before returns on the same day, so a same-day return finds its sale.
        ordered.sort(Comparator.comparing(Entry::date)
                .thenComparing(Entry::reversal)
                .thenComparing(e -> e.id() == null ? Long.MAX_VALUE : e.id()));

        Map<Integer, Double> grossByYear = new HashMap<>();
        Map<Long, Line> salesById = new HashMap<>();
        List<Line> lines = new ArrayList<>();
        double gross = 0, returns = 0, tds = 0;

        for (Entry e : ordered) {
            if (e.reversal()) {
                Line l = reversalLine(e, salesById);
                lines.add(l);
                returns += e.taxable();
                tds += l.tds();
                continue;
            }

            int year = financialYearStart(e.date());
            double cumulative = grossByYear.merge(year, e.taxable(), Double::sum);
            gross += e.taxable();

            Line l;
            boolean mayBeExempt = participant.individualOrHuf() && participant.panOrAadhaarFurnished();
            double limit = StatutoryRates.tds194oExemptLimitOn(e.date());
            if (mayBeExempt && cumulative <= limit) {
                l = new Line(e.id(), e.date(), false, e.taxable(), 0.0, 0.0,
                        "Within the Rs " + (long) limit + " yearly limit: no tax deducted");
            } else {
                double rate = StatutoryRates.tds194oRateOn(e.date(), participant.panOrAadhaarFurnished());
                String note = null;
                if (mayBeExempt) {
                    // The sale that crossed the limit. See the class comment.
                    note = "Takes the year's sales past Rs " + (long) limit
                            + ": tax deducted on the whole sale. Confirm this treatment with your CA.";
                } else if (!participant.panOrAadhaarFurnished()) {
                    note = "No PAN or Aadhaar on file: higher rate applies from the first rupee";
                } else if (!participant.individualOrHuf()) {
                    note = "Not an individual or HUF: no exemption limit";
                }
                l = new Line(e.id(), e.date(), false, e.taxable(), rate, round2(e.taxable() * rate / 100.0), note);
            }
            if (e.id() != null) salesById.put(e.id(), l);
            lines.add(l);
            tds += l.tds();
        }
        return new Result(List.copyOf(lines), round2(gross), round2(returns), round2(tds));
    }

    /** A return gives back the same share of the tax as the share of the sale it returns. */
    private static Line reversalLine(Entry e, Map<Long, Line> salesById) {
        Line sale = e.reversesId() == null ? null : salesById.get(e.reversesId());
        if (sale == null || sale.taxable() <= 0) {
            return new Line(e.id(), e.date(), true, e.taxable(), 0.0, 0.0,
                    "The sale this return cancels is not in this period: tax not adjusted here");
        }
        double share = Math.min(1.0, e.taxable() / sale.taxable());
        return new Line(e.id(), e.date(), true, e.taxable(), sale.ratePercent(),
                -round2(sale.tds() * share), "Return of a sale: tax given back in proportion");
    }

    /** The calendar year in which the Indian financial year containing {@code d} starts. */
    public static int financialYearStart(LocalDate d) {
        return d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
