package com.cauverystore.service;

import com.cauverystore.entities.CreditNote;
import com.cauverystore.entities.CreditNoteItem;
import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.GstInvoiceItem;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A Tally import fails on a voucher that does not balance or a file that is not well-formed
 * XML, and posts to the wrong place when a debit is written as a credit. Those are what these
 * check.
 */
class TallyExportServiceTest {

    private final TallyExportService service = new TallyExportService();

    private GstInvoiceItem goods(String hsn, double taxable, double cgstRate, double cgst) {
        GstInvoiceItem i = new GstInvoiceItem();
        i.setHsnCode(hsn);
        i.setProductName("Item " + hsn);
        i.setQuantity(1);
        i.setTaxableValue(taxable);
        i.setCgstRate(cgstRate); i.setSgstRate(cgstRate); i.setIgstRate(0.0);
        i.setCgstAmount(cgst); i.setSgstAmount(cgst); i.setIgstAmount(0.0);
        return i;
    }

    private GstInvoice invoice(String number, String gstin, String name, GstInvoiceItem... items) {
        GstInvoice inv = new GstInvoice();
        inv.setInvoiceNumber(number);
        inv.setInvoiceDate(LocalDate.of(2026, 11, 1));
        inv.setBuyerGstin(gstin);
        inv.setBuyerName(name);
        inv.setPlaceOfSupply("33-Tamil Nadu");
        double taxable = 0, cgst = 0;
        for (GstInvoiceItem i : items) { taxable += i.getTaxableValue(); cgst += i.getCgstAmount(); }
        inv.setTaxableAmount(taxable);
        inv.setCgstAmount(cgst);
        inv.setSgstAmount(cgst);
        inv.setIgstAmount(0.0);
        inv.setTotalCess(0.0);
        inv.setTotalAmount(Math.round((taxable + 2 * cgst) * 100.0) / 100.0);
        inv.setItems(new ArrayList<>(List.of(items)));
        return inv;
    }

    private static Document parse(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static BigDecimal sum(TallyExportService.Voucher v) {
        return v.entries().stream().map(TallyExportService.Entry::credit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal amount(TallyExportService.Voucher v, String ledger) {
        return v.entries().stream().filter(e -> e.ledger().equals(ledger))
                .map(TallyExportService.Entry::credit).findFirst().orElse(null);
    }

    @Test
    void aSaleDebitsTheCustomerAndCreditsSalesAndTax_perRate() {
        GstInvoice inv = invoice("CS/1", null, "Walk-in",
                goods("1006", 100.0, 2.5, 2.5), goods("8516", 200.0, 9.0, 18.0));

        TallyExportService.Voucher v = service.salesVoucher(inv);

        assertEquals(0, sum(v).signum(), "a voucher has to balance or Tally refuses it");
        assertEquals(TallyExportService.CONSUMER_LEDGER, v.party(), "consumers share one ledger");
        assertEquals(new BigDecimal("-341.00"), amount(v, TallyExportService.CONSUMER_LEDGER),
                "the customer is debited the invoice total");
        assertEquals(new BigDecimal("100.00"), amount(v, "Sales @ 5%"));
        assertEquals(new BigDecimal("200.00"), amount(v, "Sales @ 18%"));
        assertEquals(new BigDecimal("20.50"), amount(v, TallyExportService.CGST_LEDGER));
        assertNull(amount(v, TallyExportService.ROUND_OFF_LEDGER), "nothing to round");
    }

    @Test
    void aRegisteredBuyerGetsTheirOwnLedgerWithTheirGstin() {
        TallyExportService.Voucher v = service.salesVoucher(
                invoice("CS/2", "33AAACR5055K1Z5", "Ram Traders", goods("1006", 100.0, 2.5, 2.5)));

        assertEquals("Ram Traders", v.party());
        assertEquals("33AAACR5055K1Z5", v.partyGstin());
        assertEquals("Tamil Nadu", v.placeOfSupply());
    }

    @Test
    void aPaisaLeftOverGoesToRoundOff() {
        GstInvoice inv = invoice("CS/3", null, "x", goods("1006", 100.0, 2.5, 2.5));
        inv.setTotalAmount(105.01);

        TallyExportService.Voucher v = service.salesVoucher(inv);

        assertEquals(0, sum(v).signum());
        assertEquals(new BigDecimal("0.01"), amount(v, TallyExportService.ROUND_OFF_LEDGER));
    }

    @Test
    void aDeliveryChargeLinePostsToServiceIncome() {
        GstInvoiceItem delivery = goods(null, 50.0, 9.0, 4.5);
        delivery.setSacCode("996812");
        TallyExportService.Voucher v = service.salesVoucher(
                invoice("CS/4", null, "x", goods("1006", 100.0, 2.5, 2.5), delivery));

        assertEquals(new BigDecimal("50.00"), amount(v, "Service Income @ 18%"));
        assertEquals(0, sum(v).signum());
    }

    @Test
    void aCreditNoteIsTheMirrorOfASale_andSettlesTheOriginalBill() throws Exception {
        CreditNote cn = new CreditNote();
        cn.setCreditNoteNumber("CN/1");
        cn.setCreditNoteDate(LocalDate.of(2026, 11, 3));
        cn.setOriginalInvoiceNumber("CS/1");
        cn.setBuyerGstin("URP");
        cn.setPlaceOfSupply("33-Tamil Nadu");
        cn.setTaxableAmount(100.0);
        cn.setCgstAmount(2.5);
        cn.setSgstAmount(2.5);
        cn.setTotalAmount(105.0);
        CreditNoteItem item = new CreditNoteItem();
        item.setTaxableValue(100.0);
        item.setCgstRate(2.5);
        item.setSgstRate(2.5);
        cn.setItems(new ArrayList<>(List.of(item)));

        TallyExportService.Voucher v = service.creditNoteVoucher(cn);

        assertEquals("Credit Note", v.type());
        assertEquals(0, sum(v).signum());
        assertEquals(new BigDecimal("105.00"), amount(v, TallyExportService.CONSUMER_LEDGER),
                "the customer is credited");
        assertEquals(new BigDecimal("-100.00"), amount(v, "Sales @ 5%"), "sales are debited");
        assertEquals("CS/1", v.billRef());
        assertFalse(v.newBill(), "against the invoice it reduces, not a new bill");

        String xml = service.vouchers(List.of(), List.of(cn), List.of());
        Document doc = parse(xml);
        assertEquals("Agst Ref", doc.getElementsByTagName("BILLTYPE").item(0).getTextContent());
    }

    @Test
    void theVouchersFileIsWellFormed_skipsCancelledInvoices_andMarksDebitsDeemedPositive() throws Exception {
        GstInvoice ok = invoice("CS/5", null, "x", goods("1006", 100.0, 2.5, 2.5));
        GstInvoice cancelled = invoice("CS/6", null, "x", goods("1006", 100.0, 2.5, 2.5));
        cancelled.setStatus("CANCELLED");

        Document doc = parse(service.vouchers(List.of(ok, cancelled), List.of(), List.of()));

        NodeList vouchers = doc.getElementsByTagName("VOUCHER");
        assertEquals(1, vouchers.getLength());
        Element v = (Element) vouchers.item(0);
        assertEquals("Sales", v.getAttribute("VCHTYPE"));
        assertEquals("20261101", v.getElementsByTagName("DATE").item(0).getTextContent());
        Element first = (Element) v.getElementsByTagName("ALLLEDGERENTRIES.LIST").item(0);
        assertEquals("Yes", first.getElementsByTagName("ISDEEMEDPOSITIVE").item(0).getTextContent());
        assertEquals("-105.00", first.getElementsByTagName("AMOUNT").item(0).getTextContent());
    }

    @Test
    void theLedgersFileCreatesEveryLedgerTheVouchersUse_andEscapesNames() throws Exception {
        GstInvoice inv = invoice("CS/7", "33AAACR5055K1Z5", "Ram & Sons <Wholesale>",
                goods("1006", 100.0, 2.5, 2.5));

        String xml = service.ledgers(List.of(inv), List.of(), List.of());
        Document doc = parse(xml);

        List<String> names = new ArrayList<>();
        NodeList ledgers = doc.getElementsByTagName("LEDGER");
        for (int i = 0; i < ledgers.getLength(); i++) {
            names.add(((Element) ledgers.item(i)).getAttribute("NAME"));
        }
        assertTrue(names.contains("Sales @ 5%"), names.toString());
        assertTrue(names.contains(TallyExportService.CGST_LEDGER));
        assertTrue(names.contains(TallyExportService.ROUND_OFF_LEDGER));
        assertTrue(names.contains("Ram & Sons <Wholesale>"), "escaped in the file, intact once read");
        assertTrue(xml.contains("<PARENT>Duties &amp; Taxes</PARENT>"));
        assertTrue(xml.contains("<PARTYGSTIN>33AAACR5055K1Z5</PARTYGSTIN>"));
    }
}
