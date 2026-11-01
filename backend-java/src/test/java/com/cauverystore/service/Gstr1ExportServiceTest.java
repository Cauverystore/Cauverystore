package com.cauverystore.service;

import com.cauverystore.entities.CreditNote;
import com.cauverystore.entities.CreditNoteItem;
import com.cauverystore.entities.DebitNote;
import com.cauverystore.entities.DebitNoteItem;
import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.GstInvoiceItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the shape of the filing data rather than the numbers alone.
 *
 * A GSTR-1 upload is rejected wholesale on a wrong column heading, a date in the wrong format
 * or a section reported per invoice that should have been aggregated - so those are what these
 * assert. The seller only finds out otherwise at the portal, on the filing deadline.
 */
class Gstr1ExportServiceTest {

    private Gstr1ExportService service;

    @BeforeEach
    void setUp() {
        service = new Gstr1ExportService();
    }

    private GstInvoiceItem item(String hsn, int qty, double taxable, double cgst, double sgst,
                                double igst, double cgstAmt, double sgstAmt, double igstAmt) {
        GstInvoiceItem i = new GstInvoiceItem();
        i.setHsnCode(hsn);
        i.setProductName("Item " + hsn);
        i.setQuantity(qty);
        i.setUnitOfMeasure("NOS");
        i.setTaxableValue(taxable);
        i.setCgstRate(cgst); i.setSgstRate(sgst); i.setIgstRate(igst);
        i.setCgstAmount(cgstAmt); i.setSgstAmount(sgstAmt); i.setIgstAmount(igstAmt);
        i.setTotalAmount(taxable + cgstAmt + sgstAmt + igstAmt);
        return i;
    }

    private GstInvoice invoice(String number, String buyerGstin, boolean interState,
                               double taxable, GstInvoiceItem... items) {
        GstInvoice inv = new GstInvoice();
        inv.setInvoiceNumber(number);
        inv.setInvoiceDate(LocalDate.of(2026, 8, 6));
        inv.setBuyerGstin(buyerGstin);
        inv.setBuyerName("Buyer");
        inv.setIsInterState(interState);
        inv.setPlaceOfSupply("33-Tamil Nadu");
        inv.setTaxableAmount(taxable);
        inv.setTotalAmount(taxable * 1.05);
        inv.setItems(new ArrayList<>(List.of(items)));
        return inv;
    }

    @Test
    void shouldEmitOneRowPerTaxRate_notOnePerInvoice() {
        // An order of rice at 5% and a kettle at 18% is one invoice but two GSTR-1 rows,
        // because taxable value is reported against the rate it was taxed at. Taking the rate
        // off the invoice header - which holds only one - would silently merge them.
        GstInvoice inv = invoice("INV-1", "33AABCU9603R1ZM", false, 1000.0,
                item("1006", 2, 400.0, 2.5, 2.5, 0.0, 10.0, 10.0, 0.0),
                item("8516", 1, 600.0, 9.0, 9.0, 0.0, 54.0, 54.0, 0.0));

        List<Map<String, Object>> rows = service.b2b(List.of(inv));

        assertEquals(2, rows.size());
        assertEquals(5.0, rows.get(0).get("Rate"));
        assertEquals(400.0, rows.get(0).get("Taxable Value"));
        assertEquals(18.0, rows.get(1).get("Rate"));
        assertEquals(600.0, rows.get(1).get("Taxable Value"));
    }

    @Test
    void shouldFormatDatesTheWayTheUtilityParsesThem() {
        // ISO dates are rejected on import.
        GstInvoice inv = invoice("INV-1", "33AABCU9603R1ZM", false, 100.0,
                item("1006", 1, 100.0, 2.5, 2.5, 0.0, 2.5, 2.5, 0.0));

        assertEquals("06-Aug-2026", service.b2b(List.of(inv)).get(0).get("Invoice date"));
    }

    @Test
    void shouldUseTheUtilitysOwnColumnHeadings() {
        // The importer matches on these strings; our own field names would be rejected.
        assertEquals("GSTIN/UIN of Recipient", Gstr1ExportService.B2B_HEADERS[0]);
        assertEquals("Place Of Supply", Gstr1ExportService.B2B_HEADERS[5]);
        assertEquals("HSN", Gstr1ExportService.HSN_HEADERS[0]);
        assertEquals("Integrated Tax Amount", Gstr1ExportService.HSN_HEADERS[6]);

        GstInvoice inv = invoice("INV-1", "33AABCU9603R1ZM", false, 100.0,
                item("1006", 1, 100.0, 2.5, 2.5, 0.0, 2.5, 2.5, 0.0));
        assertEquals(List.of(Gstr1ExportService.B2B_HEADERS),
                List.copyOf(service.b2b(List.of(inv)).get(0).keySet()));
    }

    @Test
    void shouldSeparateB2bFromConsumerSupplies() {
        GstInvoice registered = invoice("INV-1", "33AABCU9603R1ZM", false, 100.0,
                item("1006", 1, 100.0, 2.5, 2.5, 0.0, 2.5, 2.5, 0.0));
        GstInvoice consumer = invoice("INV-2", null, false, 100.0,
                item("1006", 1, 100.0, 2.5, 2.5, 0.0, 2.5, 2.5, 0.0));

        assertEquals(1, service.b2b(List.of(registered, consumer)).size());
        assertEquals(1, service.b2cs(List.of(registered, consumer)).size());
    }

    @Test
    void shouldReportLargeInterStateConsumerSalesSeparatelyFromSmallOnes() {
        // B2CL is per invoice; anything below the threshold belongs in the aggregated B2CS.
        GstInvoice large = invoice("INV-1", null, true, 150000.0,
                item("8516", 1, 150000.0, 0.0, 0.0, 18.0, 0.0, 0.0, 27000.0));
        GstInvoice small = invoice("INV-2", null, true, 500.0,
                item("8516", 1, 500.0, 0.0, 0.0, 18.0, 0.0, 0.0, 90.0));

        assertEquals(1, service.b2cl(List.of(large, small)).size());
        assertEquals("INV-1", service.b2cl(List.of(large, small)).get(0).get("Invoice Number"));
        assertEquals(1, service.b2cs(List.of(large, small)).size(),
                "only the small one belongs in B2CS");
    }

    @Test
    void shouldAggregateB2csRatherThanListEveryOrder() {
        // Hundreds of small orders must collapse to a few rows keyed on place of supply and
        // rate. Emitting one row per invoice is the usual reason a B2CS import is rejected.
        List<GstInvoice> many = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            many.add(invoice("INV-" + i, null, false, 100.0,
                    item("1006", 1, 100.0, 2.5, 2.5, 0.0, 2.5, 2.5, 0.0)));
        }

        List<Map<String, Object>> rows = service.b2cs(many);

        assertEquals(1, rows.size(), "50 orders at one rate and place of supply is one row");
        assertEquals(5000.0, rows.get(0).get("Taxable Value"));
        assertEquals("OE", rows.get(0).get("Type"), "the utility takes E or OE, nothing else");
        assertEquals("", rows.get(0).get("E-Commerce GSTIN"));
    }

    @Test
    void b2cs_throughTheMarketplace_shouldBeTypeEWithItsGstin() {
        GstInvoice inv = invoice("INV-1", null, false, 100.0,
                item("1006", 1, 100.0, 2.5, 2.5, 0.0, 2.5, 2.5, 0.0));

        Map<String, Object> row = service.b2cs(List.of(inv), "33AABCC1234D1ZT").get(0);

        assertEquals("E", row.get("Type"));
        assertEquals("33AABCC1234D1ZT", row.get("E-Commerce GSTIN"));
        assertEquals("33AABCC1234D1ZT", service.b2b(List.of(
                invoice("INV-2", "33AAACR5055K1Z5", false, 100.0,
                        item("1006", 1, 100.0, 2.5, 2.5, 0.0, 2.5, 2.5, 0.0))),
                "33AABCC1234D1ZT").get(0).get("E-Commerce GSTIN"));
    }

    @Test
    void headers_shouldMatchTheOfflineToolTemplatesColumnForColumn() {
        // Copied from the GST Returns Offline Tool V3.2.4 templates. The utility reads by
        // position, so order matters as much as spelling.
        assertEquals("Type,Place Of Supply,Rate,Applicable % of Tax Rate,Taxable Value,"
                + "Cess Amount,E-Commerce GSTIN", String.join(",", Gstr1ExportService.B2CS_HEADERS));
        assertEquals("GSTIN/UIN of Recipient,Receiver Name,Note Number,Note Date,Note Type,"
                + "Place Of Supply,Reverse Charge,Note Supply Type,Note Value,"
                + "Applicable % of Tax Rate,Rate,Taxable Value,Cess Amount",
                String.join(",", Gstr1ExportService.CDN_HEADERS));
        assertEquals("HSN,Description,UQC,Total Quantity,Total Value,Taxable Value,"
                + "Integrated Tax Amount,Central Tax Amount,State/UT Tax Amount,Cess Amount,Rate",
                String.join(",", Gstr1ExportService.HSN_HEADERS));
        assertEquals("Nature of Supply,GSTIN of E-Commerce Operator,E-Commerce Operator Name,"
                + "Net value of supplies,Integrated tax,Central tax,State/UT tax,Cess",
                String.join(",", Gstr1ExportService.ECO_HEADERS));
        assertEquals("GSTIN/UIN of Recipient,Receiver Name,Invoice Number,Invoice date,"
                + "Invoice Value,Place Of Supply,Reverse Charge,Applicable % of Tax Rate,"
                + "Invoice Type,E-Commerce GSTIN,Rate,Taxable Value,Cess Amount",
                String.join(",", Gstr1ExportService.B2B_HEADERS));
        assertEquals("Invoice Number,Invoice date,Invoice Value,Place Of Supply,"
                + "Applicable % of Tax Rate,Rate,Taxable Value,Cess Amount,E-Commerce GSTIN",
                String.join(",", Gstr1ExportService.B2CL_HEADERS));
    }

    @Test
    void hsn_shouldWriteTheUnitAsCodeAndName() {
        assertEquals("NOS-NUMBERS", Gstr1ExportService.uqcLabel(null));
        assertEquals("NOS-NUMBERS", Gstr1ExportService.uqcLabel("NOS"));
        assertEquals("KGS-KILOGRAMS", Gstr1ExportService.uqcLabel("kg"));
        assertEquals("PCS-PIECES", Gstr1ExportService.uqcLabel("pcs"));
        assertEquals("OTH-OTHERS", Gstr1ExportService.uqcLabel("hours"));
    }

    @Test
    void eco_shouldReportNetSuppliesThroughTheMarketplace() {
        GstInvoice a = invoice("INV-1", null, false, 1000.0,
                item("1006", 1, 1000.0, 2.5, 2.5, 0.0, 25.0, 25.0, 0.0));
        a.setCgstAmount(25.0);
        a.setSgstAmount(25.0);
        CreditNote cn = new CreditNote();
        cn.setTaxableAmount(200.0);
        cn.setCgstAmount(5.0);
        cn.setSgstAmount(5.0);

        List<Map<String, Object>> rows = service.eco(List.of(a), List.of(cn), List.of(),
                "33AABCC1234D1ZT", "Cauvery Store");

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("Liable to collect tax u/s 52(TCS)", row.get("Nature of Supply"));
        assertEquals("33AABCC1234D1ZT", row.get("GSTIN of E-Commerce Operator"));
        assertEquals(800.0, row.get("Net value of supplies"));
        assertEquals(20.0, row.get("Central tax"));
        assertEquals(List.of(), service.eco(List.of(a), List.of(), List.of(), null, null),
                "no operator, no Table 14");
    }

    @Test
    void hsnSummary_shouldTotalAcrossEveryInvoiceInThePeriod() {
        // Mandatory in GSTR-1 and previously not produced at all.
        GstInvoice a = invoice("INV-1", null, false, 400.0,
                item("1006", 2, 400.0, 2.5, 2.5, 0.0, 10.0, 10.0, 0.0));
        GstInvoice b = invoice("INV-2", null, false, 200.0,
                item("1006", 1, 200.0, 2.5, 2.5, 0.0, 5.0, 5.0, 0.0));

        List<Map<String, Object>> hsn = service.hsnSummary(List.of(a, b));

        assertEquals(1, hsn.size());
        assertEquals("1006", hsn.get(0).get("HSN"));
        assertEquals(3.0, hsn.get(0).get("Total Quantity"));
        assertEquals(600.0, hsn.get(0).get("Taxable Value"));
        assertEquals(15.0, hsn.get(0).get("Central Tax Amount"));
        assertEquals(15.0, hsn.get(0).get("State/UT Tax Amount"));
    }

    @Test
    void hsnSummary_shouldKeepTheSameCodeApartWhenTaxedAtDifferentRates() {
        // Rice is 5% packaged and nil loose; one HSN row covering both would misstate the
        // return, so the summary is keyed on code AND rate.
        GstInvoice inv = invoice("INV-1", null, false, 300.0,
                item("1006", 1, 200.0, 2.5, 2.5, 0.0, 5.0, 5.0, 0.0),
                item("1006", 1, 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));

        List<Map<String, Object>> hsn = service.hsnSummary(List.of(inv));

        assertEquals(2, hsn.size());
    }

    @Test
    void hsnSummary_shouldSkipItemsWithNoCode() {
        // A product that never got classified cannot be reported against an HSN.
        GstInvoice inv = invoice("INV-1", null, false, 100.0,
                item(null, 1, 100.0, 2.5, 2.5, 0.0, 2.5, 2.5, 0.0));

        assertTrue(service.hsnSummary(List.of(inv)).isEmpty());
    }

    @Test
    void cdn_shouldListCreditAndDebitNotesAgainstRegisteredRecipientsOnly() {
        // Notes to a GSTIN go in table 9A/9B; notes to URP consumers are netted inside B2CS
        // and listing them here would double-report the reversal.
        CreditNote cn = new CreditNote();
        cn.setCreditNoteNumber("CN-1");
        cn.setCreditNoteDate(LocalDate.of(2026, 8, 7));
        cn.setBuyerGstin("33AABCU9603R1ZM");
        cn.setBuyerName("Registered Buyer");
        cn.setPlaceOfSupply("33-Tamil Nadu");
        cn.setTotalAmount(106.0);
        CreditNoteItem cnItem = new CreditNoteItem();
        cnItem.setProductName("Item");
        cnItem.setHsnCode("1006");
        cnItem.setTaxableValue(100.0);
        cnItem.setCgstRate(2.5); cnItem.setSgstRate(2.5); cnItem.setIgstRate(0.0);
        cn.setItems(new ArrayList<>(List.of(cnItem)));

        CreditNote consumer = new CreditNote();
        consumer.setCreditNoteNumber("CN-2");
        consumer.setBuyerGstin("URP");
        consumer.setBuyerName("Consumer");
        consumer.setPlaceOfSupply("33-Tamil Nadu");
        consumer.setTotalAmount(10.0);
        consumer.setItems(new ArrayList<>());

        List<Map<String, Object>> rows = service.cdn(List.of(cn, consumer), List.of());

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("33AABCU9603R1ZM", row.get("GSTIN/UIN of Recipient"));
        assertEquals("CN-1", row.get("Note Number"));
        assertEquals("07-Aug-2026", row.get("Note Date"));
        assertEquals("Regular B2B", row.get("Note Supply Type"));
        assertEquals("C", row.get("Note Type"));
        assertEquals(100.0, row.get("Taxable Value"));
        assertEquals(5.0, row.get("Rate"));
    }

    @Test
    void cdn_shouldSplitANoteByRateAndMarkDebitNotesD() {
        // A note that reversed lines at 5% and 18% reports one row per rate, like the invoice
        // it corrects.
        DebitNote dn = new DebitNote();
        dn.setDebitNoteNumber("DN-1");
        dn.setDebitNoteDate(LocalDate.of(2026, 8, 8));
        dn.setBuyerGstin("33AABCU9603R1ZM");
        dn.setBuyerName("Registered Buyer");
        dn.setPlaceOfSupply("33-Tamil Nadu");
        dn.setTotalAmount(600.0);
        DebitNoteItem a = new DebitNoteItem();
        a.setTaxableValue(200.0); a.setCgstRate(2.5); a.setSgstRate(2.5); a.setIgstRate(0.0);
        DebitNoteItem b = new DebitNoteItem();
        b.setTaxableValue(300.0); b.setCgstRate(9.0); b.setSgstRate(9.0); b.setIgstRate(0.0);
        dn.setItems(new ArrayList<>(List.of(a, b)));

        List<Map<String, Object>> rows = service.cdn(List.of(), List.of(dn));

        assertEquals(2, rows.size());
        assertEquals("D", rows.get(0).get("Note Type"));
        assertEquals("DN-1", rows.get(0).get("Note Number"));
        assertEquals(5.0, rows.get(0).get("Rate"));
        assertEquals(200.0, rows.get(0).get("Taxable Value"));
        assertEquals(18.0, rows.get(1).get("Rate"));
        assertEquals(300.0, rows.get(1).get("Taxable Value"));
    }

    @Test
    void headersFor_shouldRefuseAnUnknownSection() {
        assertThrows(IllegalArgumentException.class, () -> service.headersFor("gstr9"));
    }

    @Test
    void allSections_shouldReturnEveryTableTheReturnNeeds() {
        GstInvoice inv = invoice("INV-1", null, false, 100.0,
                item("1006", 1, 100.0, 2.5, 2.5, 0.0, 2.5, 2.5, 0.0));

        assertEquals(List.of("b2b", "b2cl", "b2cs", "cdn", "cdnur", "hsn_b2b", "hsn_b2c", "eco"),
                List.copyOf(service.allSections(List.of(inv)).keySet()));
    }

    @Test
    void allSections_shouldKeepTheSingleHsnListOnlyBeforeMay2025() {
        GstInvoice inv = invoice("INV-1", null, false, 100.0,
                item("1006", 1, 100.0, 2.5, 2.5, 0.0, 2.5, 2.5, 0.0));

        assertTrue(service.allSections(List.of(inv), List.of(), List.of(), LocalDate.of(2025, 4, 1))
                .containsKey("hsn"), "April 2025 return: one list");
        assertFalse(service.allSections(List.of(inv), List.of(), List.of(), LocalDate.of(2025, 5, 1))
                .containsKey("hsn"), "May 2025 return: split lists only");
    }

    @Test
    void hsnSummary_shouldSplitRegisteredBuyersFromConsumers() {
        // From May 2025 the portal rejects one combined Table 12. A buyer with a GSTIN is B2B,
        // exactly as in the b2b section, so the HSN and invoice tables agree.
        GstInvoice registered = invoice("INV-1", "33AAACR5055K1Z5", false, 400.0,
                item("1006", 2, 400.0, 2.5, 2.5, 0.0, 10.0, 10.0, 0.0));
        GstInvoice consumer = invoice("INV-2", null, false, 200.0,
                item("1006", 1, 200.0, 2.5, 2.5, 0.0, 5.0, 5.0, 0.0));
        GstInvoice blankGstin = invoice("INV-3", " ", false, 100.0,
                item("1006", 1, 100.0, 2.5, 2.5, 0.0, 2.5, 2.5, 0.0));

        List<Map<String, Object>> b2b = service.hsnSummaryB2b(List.of(registered, consumer, blankGstin));
        List<Map<String, Object>> b2c = service.hsnSummaryB2c(List.of(registered, consumer, blankGstin));

        assertEquals(1, b2b.size());
        assertEquals(400.0, b2b.get(0).get("Taxable Value"));
        assertEquals(1, b2c.size());
        assertEquals(300.0, b2c.get(0).get("Taxable Value"), "a blank GSTIN is a consumer");
        assertEquals(2.0, b2c.get(0).get("Total Quantity"));
        assertArrayEquals(Gstr1ExportService.HSN_HEADERS, service.headersFor("hsn_b2b"));
        assertArrayEquals(Gstr1ExportService.HSN_HEADERS, service.headersFor("hsn_b2c"));
    }
}
