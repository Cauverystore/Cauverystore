package com.cauverystore.service;

import com.cauverystore.entities.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GSTR-1 Table 12 and B2CS, and GSTR-3B, must be net of the period's credit and debit
 * notes and leave out cancelled invoices. Before this they counted invoices only, so any
 * month with returns over-reported sales.
 */
class GstReportNettingTest {

    private final Gstr1ExportService gstr1 = new Gstr1ExportService();
    private static final LocalDate MAY = LocalDate.of(2026, 5, 1);
    private static final String TN = "33-Tamil Nadu";
    private static final String KA = "29-Karnataka";

    private static GstInvoiceItem item(String hsn, int qty, double taxable, double rate, boolean inter) {
        GstInvoiceItem it = new GstInvoiceItem();
        it.setHsnCode(hsn);
        it.setProductName("Item " + hsn);
        it.setUnitOfMeasure("NOS");
        it.setQuantity(qty);
        it.setTaxableValue(taxable);
        double tax = taxable * rate / 100;
        if (inter) { it.setIgstRate(rate); it.setIgstAmount(tax); }
        else { it.setCgstRate(rate / 2); it.setSgstRate(rate / 2); it.setCgstAmount(tax / 2); it.setSgstAmount(tax / 2); }
        it.setTotalAmount(taxable + tax);
        return it;
    }

    private static GstInvoice invoice(String no, String gstin, String pos, boolean inter, GstInvoiceItem... items) {
        GstInvoice inv = new GstInvoice();
        inv.setInvoiceNumber(no);
        inv.setInvoiceDate(MAY.plusDays(2));
        inv.setBuyerGstin(gstin);
        inv.setPlaceOfSupply(pos);
        inv.setIsInterState(inter);
        inv.setStatus("GENERATED");
        double taxable = 0, total = 0, igst = 0, cgst = 0, sgst = 0;
        for (GstInvoiceItem it : items) {
            taxable += it.getTaxableValue();
            total += it.getTotalAmount();
            igst += it.getIgstAmount() == null ? 0 : it.getIgstAmount();
            cgst += it.getCgstAmount() == null ? 0 : it.getCgstAmount();
            sgst += it.getSgstAmount() == null ? 0 : it.getSgstAmount();
        }
        inv.setTaxableAmount(taxable);
        inv.setTotalAmount(total);
        inv.setIgstAmount(igst);
        inv.setCgstAmount(cgst);
        inv.setSgstAmount(sgst);
        inv.setItems(new ArrayList<>(List.of(items)));
        return inv;
    }

    private static CreditNote credit(String no, String original, String gstin, String pos, boolean inter,
                                     String hsn, int qty, double taxable, double rate) {
        CreditNote cn = new CreditNote();
        cn.setCreditNoteNumber(no);
        cn.setCreditNoteDate(MAY.plusDays(10));
        cn.setOriginalInvoiceNumber(original);
        cn.setBuyerGstin(gstin);
        cn.setPlaceOfSupply(pos);
        cn.setIsInterState(inter);
        GstInvoiceItem src = item(hsn, qty, taxable, rate, inter);
        CreditNoteItem it = new CreditNoteItem();
        it.setHsnCode(hsn);
        it.setUnitOfMeasure("NOS");
        it.setQuantity(qty);
        it.setTaxableValue(taxable);
        it.setIgstRate(src.getIgstRate()); it.setIgstAmount(src.getIgstAmount());
        it.setCgstRate(src.getCgstRate()); it.setCgstAmount(src.getCgstAmount());
        it.setSgstRate(src.getSgstRate()); it.setSgstAmount(src.getSgstAmount());
        it.setTotalAmount(src.getTotalAmount());
        cn.setItems(new ArrayList<>(List.of(it)));
        cn.setTaxableAmount(taxable);
        cn.setTotalAmount(src.getTotalAmount());
        return cn;
    }

    private static DebitNote debit(String no, String gstin, String pos, String hsn, int qty, double taxable, double rate) {
        DebitNote dn = new DebitNote();
        dn.setDebitNoteNumber(no);
        dn.setDebitNoteDate(MAY.plusDays(12));
        dn.setBuyerGstin(gstin);
        dn.setPlaceOfSupply(pos);
        dn.setIsInterState(false);
        GstInvoiceItem src = item(hsn, qty, taxable, rate, false);
        DebitNoteItem it = new DebitNoteItem();
        it.setHsnCode(hsn);
        it.setUnitOfMeasure("NOS");
        it.setQuantity(qty);
        it.setTaxableValue(taxable);
        it.setCgstRate(src.getCgstRate()); it.setCgstAmount(src.getCgstAmount());
        it.setSgstRate(src.getSgstRate()); it.setSgstAmount(src.getSgstAmount());
        it.setTotalAmount(src.getTotalAmount());
        dn.setItems(new ArrayList<>(List.of(it)));
        dn.setTaxableAmount(taxable);
        dn.setTotalAmount(src.getTotalAmount());
        return dn;
    }

    private static Map<String, Object> row(List<Map<String, Object>> rows, String key, Object value) {
        return rows.stream().filter(r -> value.equals(r.get(key))).findFirst()
                .orElseThrow(() -> new AssertionError("no row with " + key + "=" + value + " in " + rows));
    }

    @Test
    void hsnSummaryIsNetOfCreditAndDebitNotes() {
        GstInvoice sale = invoice("INV-1", null, TN, false, item("1006", 10, 1000, 5, false));
        CreditNote ret = credit("CN-1", "INV-1", "URP", TN, false, "1006", 2, 200, 5);
        DebitNote extra = debit("DN-1", null, TN, "1006", 1, 100, 5);

        var out = gstr1.allSections(List.of(sale), List.of(ret), List.of(extra), MAY, null, null, Set.of());
        Map<String, Object> r = row(out.get("hsn_b2c"), "HSN", "1006");
        assertEquals(9.0, (double) r.get("Total Quantity"), 0.001);
        assertEquals(900.0, (double) r.get("Taxable Value"), 0.001);
        assertEquals(22.5, (double) r.get("Central Tax Amount"), 0.001);
        assertEquals(22.5, (double) r.get("State/UT Tax Amount"), 0.001);
        assertTrue(out.get("hsn_b2b").isEmpty());
    }

    @Test
    void aFullyReturnedHsnDropsOutOfTable12() {
        GstInvoice sale = invoice("INV-1", "33AAAAA0000A1Z5", TN, false, item("8516", 1, 2000, 18, false));
        CreditNote ret = credit("CN-1", "INV-1", "33AAAAA0000A1Z5", TN, false, "8516", 1, 2000, 18);
        var out = gstr1.allSections(List.of(sale), List.of(ret), List.of(), MAY, null, null, Set.of());
        assertTrue(out.get("hsn_b2b").isEmpty(), "nothing left to report");
        assertEquals(1, out.get("cdn").size(), "the note itself is still listed in 9B");
    }

    @Test
    void consumerNotesAreNettedIntoB2cs() {
        GstInvoice sale = invoice("INV-1", null, KA, true, item("1006", 10, 1000, 5, true));
        CreditNote ret = credit("CN-1", "INV-1", "URP", KA, true, "1006", 3, 300, 5);
        var out = gstr1.allSections(List.of(sale), List.of(ret), List.of(), MAY, null, null, Set.of());
        Map<String, Object> r = row(out.get("b2cs"), "Place Of Supply", KA);
        assertEquals(700.0, (double) r.get("Taxable Value"), 0.001);
        assertTrue(out.get("cdnur").isEmpty());
        assertTrue(out.get("cdn").isEmpty(), "consumer notes never go to the registered-buyer table");
    }

    @Test
    void notesOnB2clInvoicesGoToCdnurNotB2cs() {
        GstInvoice big = invoice("INV-9", null, KA, true, item("8471", 1, 150_000, 18, true));
        CreditNote ret = credit("CN-9", "INV-9", "URP", KA, true, "8471", 1, 150_000, 18);
        var out = gstr1.allSections(List.of(big), List.of(ret), List.of(), MAY, null, null, Set.of());
        assertEquals(1, out.get("b2cl").size());
        assertTrue(out.get("b2cs").isEmpty());
        Map<String, Object> r = out.get("cdnur").get(0);
        assertEquals("B2CL", r.get("UR Type"));
        assertEquals("C", r.get("Note Type"));
        assertEquals(150_000.0, (double) r.get("Taxable Value"), 0.001);
        assertArrayEquals(Gstr1ExportService.CDNUR_HEADERS, r.keySet().toArray(new String[0]));
    }

    @Test
    void aNoteOnAnEarlierPeriodsB2clInvoiceAlsoGoesToCdnur() {
        CreditNote ret = credit("CN-9", "INV-APR-9", "URP", KA, true, "8471", 1, 150_000, 18);
        var out = gstr1.allSections(List.of(), List.of(ret), List.of(), MAY, null, null, Set.of("INV-APR-9"));
        assertEquals(1, out.get("cdnur").size());
        assertTrue(out.get("b2cs").isEmpty());
    }

    @Test
    void cancelledInvoicesAreLeftOutOfGstr1() {
        GstInvoice sale = invoice("INV-1", "33AAAAA0000A1Z5", TN, false, item("1006", 1, 500, 5, false));
        GstInvoice gone = invoice("INV-2", "33AAAAA0000A1Z5", TN, false, item("1006", 1, 700, 5, false));
        gone.setStatus("CANCELLED");
        var out = gstr1.allSections(List.of(sale, gone), List.of(), List.of(), MAY, null, null, Set.of());
        assertEquals(1, out.get("b2b").size());
        assertEquals(500.0, (double) row(out.get("hsn_b2b"), "HSN", "1006").get("Taxable Value"), 0.001);
    }

    @Test
    @SuppressWarnings("unchecked")
    void gstr3bNetsBothNoteKindsAndSplitsNilRatedAndInterStateConsumers() {
        GstInvoice local = invoice("INV-1", "33AAAAA0000A1Z5", TN, false,
                item("1006", 10, 1000, 5, false), item("0701", 5, 400, 0, false));
        GstInvoice toKarnataka = invoice("INV-2", null, KA, true, item("8516", 1, 2000, 18, true));
        GstInvoice gone = invoice("INV-3", null, KA, true, item("8516", 1, 9999, 18, true));
        gone.setStatus("CANCELLED");
        CreditNote ret = credit("CN-1", "INV-2", "URP", KA, true, "8516", 1, 500, 18);
        DebitNote extra = debit("DN-1", "33AAAAA0000A1Z5", TN, "1006", 0, 100, 5);

        Map<String, Object> s = Gstr3bBuilder.build("33BBBBB0000B1Z5", MAY, MAY.plusDays(30),
                List.of(local, toKarnataka, gone), List.of(ret), List.of(extra));

        Map<String, Object> a = (Map<String, Object>) s.get("table3_1_outwardSupplies");
        assertEquals(1000 + 2000 - 500 + 100, (double) a.get("taxableValue"), 0.001);
        assertEquals(360 - 90, (double) a.get("igst"), 0.001);
        assertEquals(25 + 2.5, (double) a.get("cgst"), 0.001);
        assertEquals(25 + 2.5, (double) a.get("sgst"), 0.001);

        assertEquals(400.0, (double) ((Map<String, Object>) s.get("table3_1c_nilExempt")).get("taxableValue"), 0.001);

        List<Map<String, Object>> t32 = (List<Map<String, Object>>) s.get("table3_2_interStateUnregistered");
        assertEquals(1, t32.size());
        assertEquals(KA, t32.get(0).get("placeOfSupply"));
        assertEquals(1500.0, (double) t32.get(0).get("taxableValue"), 0.001);
        assertEquals(270.0, (double) t32.get(0).get("igst"), 0.001);

        assertEquals(2, s.get("invoiceCount"));
        assertEquals(1, s.get("cancelledInvoiceCount"));
        assertEquals(1, s.get("debitNoteCount"));
        assertFalse(s.containsKey("table3_1b_itc"), "sales tax is not input credit");
    }

    @Test
    void gstr3bCsvRowsFollowThePortalTableOrder() {
        Map<String, Object> s = Gstr3bBuilder.build("33BBBBB0000B1Z5", MAY, MAY.plusDays(30),
                List.of(invoice("INV-2", null, KA, true, item("8516", 1, 2000, 18, true))), List.of(), List.of());
        List<Map<String, Object>> rows = CreditNoteService.gstr3bRows(s);
        assertTrue(((String) rows.get(0).get("section")).startsWith("3.1(a)"));
        assertTrue(((String) rows.get(2).get("section")).startsWith("3.1(c)"));
        assertTrue(((String) rows.get(5).get("section")).startsWith("3.2"));
        assertEquals(KA, rows.get(5).get("placeOfSupply"));
    }
}
