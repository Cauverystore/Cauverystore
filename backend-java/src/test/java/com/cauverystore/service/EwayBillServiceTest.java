package com.cauverystore.service;

import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.GstInvoiceItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EwayBillServiceTest {

    private static GstInvoiceItem goods(String hsn, double taxable, double cgstRate) {
        GstInvoiceItem i = new GstInvoiceItem();
        i.setHsnCode(hsn);
        i.setProductName("Item " + hsn);
        i.setQuantity(2);
        i.setUnitOfMeasure("NOS");
        i.setTaxableValue(taxable);
        i.setCgstRate(cgstRate); i.setSgstRate(cgstRate); i.setIgstRate(0.0);
        double half = Math.round(taxable * cgstRate) / 100.0;
        i.setCgstAmount(half); i.setSgstAmount(half); i.setIgstAmount(0.0);
        return i;
    }

    private static GstInvoiceItem delivery(double taxable) {
        GstInvoiceItem i = goods(null, taxable, 9.0);
        i.setSacCode("996812");
        return i;
    }

    private static GstInvoice invoice(GstInvoiceItem... items) {
        GstInvoice inv = new GstInvoice();
        inv.setInvoiceNumber("CS/2026/0042");
        inv.setInvoiceDate(LocalDate.of(2026, 11, 1));
        inv.setSellerGstin("33ABCDE1234F1Z7");
        inv.setSellerLegalName("Kaveri Silks");
        inv.setSellerAddress("12 Bazaar Street, Kanchipuram, Tamil Nadu 631501");
        inv.setBuyerName("Ravi Kumar");
        inv.setDeliveryAddress("4 Lake Road, Bengaluru, Karnataka, 560001");
        inv.setPlaceOfSupply("29-Karnataka");
        inv.setDeliveryStateCode("29");
        inv.setItems(new ArrayList<>(List.of(items)));
        double total = 0;
        for (GstInvoiceItem i : items) total += i.getTaxableValue() + i.getCgstAmount() + i.getSgstAmount();
        inv.setTotalAmount(Math.round(total * 100) / 100.0);
        return inv;
    }

    @Test
    void theThresholdIsOnValuePlusTax_notTaxableValueAlone() {
        // 48,000 taxable at 5% is 50,400 with tax: over the limit, though the old check
        // (taxable > 50,000) said no e-way bill was needed.
        GstInvoice inv = invoice(goods("5007", 48_000, 2.5));
        assertEquals(50_400.0, EwayBillService.consignmentValue(inv), 0.01);
        assertTrue(EwayBillService.isRequired(inv));
    }

    @Test
    void exemptGoodsAndTheDeliveryChargeDoNotCount() {
        GstInvoice inv = invoice(goods("5007", 40_000, 2.5), goods("0713", 20_000, 0.0), delivery(500));
        assertEquals(42_000.0, EwayBillService.consignmentValue(inv), 0.01);
        assertFalse(EwayBillService.isRequired(inv));
    }

    @Test
    void exactlyFiftyThousandIsNotOverTheLimit() {
        GstInvoice inv = invoice(goods("5007", 50_000, 0.0001 / 2));
        inv.getItems().get(0).setCgstAmount(0.0);
        inv.getItems().get(0).setSgstAmount(0.0);
        assertFalse(EwayBillService.isRequired(inv));
    }

    @Test
    void validityIsADayPer200KmRunningToMidnightOfTheNextDay() {
        LocalDateTime made = LocalDateTime.of(2026, 11, 1, 10, 0);
        assertEquals(LocalDate.of(2026, 11, 2), EwayBillService.validUpto(made, 0, false));
        assertEquals(LocalDate.of(2026, 11, 2), EwayBillService.validUpto(made, 200, false));
        assertEquals(LocalDate.of(2026, 11, 3), EwayBillService.validUpto(made, 201, false));
        assertEquals(LocalDate.of(2026, 11, 4), EwayBillService.validUpto(made, 350 + 250, false));
        assertEquals(LocalDate.of(2026, 11, 4), EwayBillService.validUpto(made, 50, true), "20 km a day for ODC");
    }

    @Test
    void recordingTakesOnlyARealTwelveDigitNumber() {
        GstInvoice inv = invoice(goods("5007", 60_000, 2.5));
        assertThrows(IllegalArgumentException.class,
                () -> EwayBillService.record(inv, "EWB000000004201", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> EwayBillService.record(inv, "1234", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> EwayBillService.record(inv, "331000123456", LocalDateTime.of(2026, 10, 30, 9, 0), null),
                "cannot predate the invoice");

        EwayBillService.record(inv, "3310 0012 3456", LocalDateTime.of(2026, 11, 1, 18, 0), null);
        assertEquals("331000123456", inv.getEwayBillNumber());
        assertEquals(EwayBillService.GENERATED, inv.getEwayBillStatus());
        assertEquals(LocalDate.of(2026, 11, 2), inv.getEwayBillExpiry());
    }

    @Test
    void theBulkFileCarriesPartAForThePortal() throws Exception {
        GstInvoice inv = invoice(goods("50072010", 60_000, 2.5), delivery(500));

        JsonNode root = new ObjectMapper().readTree(new EwayBillService().bulkJson(List.of(inv)));
        JsonNode b = root.path("billLists").get(0);

        assertEquals("O", b.path("supplyType").asText());
        assertEquals("INV", b.path("docType").asText());
        assertEquals("01/11/2026", b.path("docDate").asText());
        assertEquals("URP", b.path("toGstin").asText(), "a consumer has no GSTIN");
        assertEquals(631501, b.path("fromPincode").asInt());
        assertEquals(560001, b.path("toPincode").asInt());
        assertEquals(33, b.path("fromStateCode").asInt());
        assertEquals(29, b.path("toStateCode").asInt());
        assertEquals(1, b.path("transactionType").asInt());
        assertEquals("0", b.path("transDistance").asText(), "0 asks the portal to work it out");
        assertEquals(60_000.0, b.path("totalValue").asDouble(), 0.01);
        assertEquals(1_500.0, b.path("cgstValue").asDouble(), 0.01);
        assertTrue(b.path("otherValue").asDouble() > 500, "delivery charge is other value, not goods");
        assertEquals(1, b.path("itemList").size(), "only goods are listed");
        assertEquals(50072010L, b.path("mainHsnCode").asLong());
        assertEquals("NOS", b.path("itemList").get(0).path("qtyUnit").asText());
        assertEquals("", b.path("vehicleNo").asText(), "Part B is the courier's");
    }

    @Test
    void theBulkFileRefusesAnInvoiceWithoutPinCodes() {
        GstInvoice inv = invoice(goods("5007", 60_000, 2.5));
        inv.setDeliveryAddress("Somewhere in Bengaluru");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new EwayBillService().bulkJson(List.of(inv)));
        assertTrue(e.getMessage().contains("PIN"));
    }

    @Test
    void addressLinesDropThePinAndFindThePlace() {
        String[] a = EwayBillService.addressLines("4 Lake Road, Bengaluru, Karnataka, 560001");
        assertEquals("4 Lake Road, Bengaluru, Karnataka", a[0]);
        assertEquals("Bengaluru", a[2]);
    }
}
