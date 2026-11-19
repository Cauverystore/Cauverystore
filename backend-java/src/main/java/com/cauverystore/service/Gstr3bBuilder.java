package com.cauverystore.service;

import com.cauverystore.entities.CreditNote;
import com.cauverystore.entities.DebitNote;
import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.GstInvoiceItem;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The outward-supply tables of GSTR-3B, laid out the way the portal asks for them.
 *
 * <ul>
 *   <li>3.1(a) outward taxable supplies, other than zero-rated, nil-rated and exempt</li>
 *   <li>3.1(c) other outward supplies: nil-rated and exempt (value only, no tax)</li>
 *   <li>3.2 of the supplies in 3.1(a), the inter-state ones to unregistered persons,
 *       by place of supply (value and IGST)</li>
 * </ul>
 *
 * Every figure is invoices less credit notes plus debit notes for the period, worked from the
 * item lines so a mixed order lands its 0% lines in 3.1(c) and the rest in 3.1(a). Cancelled
 * invoices and notes are left out.
 *
 * Not produced: Table 4 (input tax credit) needs purchase invoices and GSTR-2B, which this
 * system does not hold. 3.1.1 (supplies where the operator pays tax under s.9(5)) is nil for
 * goods sold through the marketplace, which collects TCS under s.52 instead. 3.1(b), (d) and
 * (e) - exports, reverse charge inward supplies and non-GST supplies - do not arise here.
 */
public final class Gstr3bBuilder {

    private Gstr3bBuilder() { }

    /** Running totals for one table. */
    private static final class Bucket {
        double taxable, igst, cgst, sgst, cess;

        void add(int sign, double taxable, double igst, double cgst, double sgst, double cess) {
            this.taxable += sign * taxable;
            this.igst += sign * igst;
            this.cgst += sign * cgst;
            this.sgst += sign * sgst;
            this.cess += sign * cess;
        }

        Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taxableValue", round(taxable));
            m.put("igst", round(igst));
            m.put("cgst", round(cgst));
            m.put("sgst", round(sgst));
            m.put("cess", round(cess));
            m.put("totalTax", round(igst + cgst + sgst + cess));
            return m;
        }
    }

    public static Map<String, Object> build(String gstin, LocalDate start, LocalDate end,
                                            List<GstInvoice> invoices, List<CreditNote> creditNotes,
                                            List<DebitNote> debitNotes) {
        Bucket taxable = new Bucket();
        Bucket nilExempt = new Bucket();
        Map<String, Bucket> interStateUnregistered = new TreeMap<>();
        double grossInvoices = 0, grossCredit = 0, grossDebit = 0;
        int invoiceCount = 0, cancelledCount = 0, b2bCount = 0, b2cCount = 0;

        for (GstInvoice inv : invoices == null ? List.<GstInvoice>of() : invoices) {
            if (Gstr1ExportService.cancelled(inv.getStatus())) { cancelledCount++; continue; }
            invoiceCount++;
            if (isBlank(inv.getBuyerGstin())) b2cCount++; else b2bCount++;
            boolean unregisteredInterState = isBlank(inv.getBuyerGstin()) && Boolean.TRUE.equals(inv.getIsInterState());
            List<GstInvoiceItem> items = inv.getItems() == null ? List.of() : inv.getItems();
            if (items.isEmpty()) {
                grossInvoices += nz(inv.getTaxableAmount());
                double rate = nz(inv.getIgstRate()) + nz(inv.getCgstRate()) + nz(inv.getSgstRate());
                place(1, rate, nz(inv.getTaxableAmount()), nz(inv.getIgstAmount()), nz(inv.getCgstAmount()),
                        nz(inv.getSgstAmount()), nz(inv.getTotalCess()), taxable, nilExempt,
                        unregisteredInterState ? bucketFor(interStateUnregistered, inv.getPlaceOfSupply()) : null);
                continue;
            }
            for (GstInvoiceItem it : items) {
                grossInvoices += nz(it.getTaxableValue());
                double rate = nz(it.getIgstRate()) + nz(it.getCgstRate()) + nz(it.getSgstRate());
                place(1, rate, nz(it.getTaxableValue()), nz(it.getIgstAmount()), nz(it.getCgstAmount()),
                        nz(it.getSgstAmount()), nz(it.getCessAmount()), taxable, nilExempt,
                        unregisteredInterState ? bucketFor(interStateUnregistered, inv.getPlaceOfSupply()) : null);
            }
        }

        int creditCount = 0, debitCount = 0;
        for (Gstr1ExportService.NoteDoc note : Gstr1ExportService.notes(creditNotes, debitNotes)) {
            if (note.sign < 0) creditCount++; else debitCount++;
            boolean unregisteredInterState = !note.registered() && note.interState;
            for (Gstr1ExportService.NoteLine line : note.lines) {
                if (note.sign < 0) grossCredit += line.taxable; else grossDebit += line.taxable;
                place(note.sign, line.rate, line.taxable, line.igst, line.cgst, line.sgst, 0, taxable, nilExempt,
                        unregisteredInterState ? bucketFor(interStateUnregistered, note.placeOfSupply) : null);
            }
        }

        List<Map<String, Object>> table32 = new ArrayList<>();
        for (Map.Entry<String, Bucket> e : interStateUnregistered.entrySet()) {
            if (Math.abs(e.getValue().taxable) < 0.005 && Math.abs(e.getValue().igst) < 0.005) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("placeOfSupply", e.getKey());
            row.put("taxableValue", round(e.getValue().taxable));
            row.put("igst", round(e.getValue().igst));
            table32.add(row);
        }

        Map<String, Object> nil = new LinkedHashMap<>();
        nil.put("taxableValue", round(nilExempt.taxable));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gstin", gstin);
        result.put("periodStart", start.toString());
        result.put("periodEnd", end.toString());
        result.put("table3_1_outwardSupplies", taxable.asMap());
        result.put("table3_1c_nilExempt", nil);
        result.put("table3_2_interStateUnregistered", table32);
        result.put("invoiceCount", invoiceCount);
        result.put("cancelledInvoiceCount", cancelledCount);
        result.put("creditNoteCount", creditCount);
        result.put("debitNoteCount", debitCount);
        result.put("b2bCount", b2bCount);
        result.put("b2cCount", b2cCount);
        result.put("grossTaxableValue", round(grossInvoices));
        result.put("grossCreditNotesValue", round(grossCredit));
        result.put("grossDebitNotesValue", round(grossDebit));
        result.put("notProduced", "Table 4 (input tax credit) needs your purchase invoices and GSTR-2B; "
                + "take it from GSTR-2B on the portal. 3.1.1 is nil for goods sold through the marketplace "
                + "(it collects TCS under s.52, it does not pay your tax under s.9(5)).");
        return result;
    }

    /** One line into 3.1(a) or 3.1(c), and into its 3.2 place-of-supply bucket when there is one. */
    private static void place(int sign, double rate, double value, double igst, double cgst, double sgst,
                              double cess, Bucket taxable, Bucket nilExempt, Bucket interState) {
        if (rate <= 0 && igst == 0 && cgst == 0 && sgst == 0) {
            nilExempt.add(sign, value, 0, 0, 0, 0);
            return;
        }
        taxable.add(sign, value, igst, cgst, sgst, cess);
        if (interState != null) interState.add(sign, value, igst, 0, 0, 0);
    }

    private static Bucket bucketFor(Map<String, Bucket> buckets, String pos) {
        return buckets.computeIfAbsent(pos == null ? "" : pos, k -> new Bucket());
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank() || "URP".equalsIgnoreCase(s.trim()); }
    private static double nz(Double d) { return d == null ? 0.0 : d; }
    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
