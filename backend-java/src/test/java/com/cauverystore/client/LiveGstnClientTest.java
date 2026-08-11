package com.cauverystore.client;

import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.GstInvoiceItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the behaviour that matters even though the portal calls themselves cannot be tested
 * here: that this client never invents an IRN, and that the payload it would send matches the
 * schema the IRP validates against.
 *
 * An invoice carrying a fabricated IRN looks registered to everyone downstream - the buyer may
 * claim input credit against a number the portal has never heard of, and nobody finds out
 * until a reconciliation months later. A missing IRN is a visible problem that can be retried;
 * a fake one is an invisible one that cannot. So every failure path here must throw.
 *
 * The payload tests call buildInvoicePayload directly rather than going through generateIrn,
 * which would need a live session. They pin the two things that caused the first versions to
 * be rejected: the ItemList (mandatory - an invoice with header amounts but no lines is
 * refused outright) and the supplier's state code (Error 2258 rejects a Stcd that does not
 * match the GSTIN's own state).
 */
class LiveGstnClientTest {

    private LiveGstnClient client;
    private GstInvoice invoice;

    @BeforeEach
    void setUp() {
        client = new LiveGstnClient();
        invoice = new GstInvoice();
        invoice.setInvoiceNumber("INV-1");
        invoice.setInvoiceDate(LocalDate.of(2026, 8, 7));
        invoice.setSellerGstin("33AABCU9603R1ZM");
        invoice.setTotalAmount(1000.0);
    }

    private void configure(String clientId, String secret, String user, String pass,
                           String gstin, String publicKey) {
        ReflectionTestUtils.setField(client, "clientId", clientId);
        ReflectionTestUtils.setField(client, "clientSecret", secret);
        ReflectionTestUtils.setField(client, "username", user);
        ReflectionTestUtils.setField(client, "password", pass);
        ReflectionTestUtils.setField(client, "gstin", gstin);
        ReflectionTestUtils.setField(client, "publicKeyBase64", publicKey);
    }

    private GstInvoiceItem goodsItem() {
        GstInvoiceItem item = new GstInvoiceItem();
        item.setProductName("Fizzy drink");
        item.setHsnCode("220210");
        item.setQuantity(2);
        item.setUnitOfMeasure("BTL");
        item.setUnitPrice(50.0);
        item.setTaxableValue(100.0);
        item.setCgstRate(20.0);
        item.setCgstAmount(20.0);
        item.setSgstRate(20.0);
        item.setSgstAmount(20.0);
        item.setIgstRate(0.0);
        item.setIgstAmount(0.0);
        item.setCessRate(0.0);
        item.setCessAmount(0.0);
        return item;
    }

    private GstInvoiceItem serviceItem() {
        GstInvoiceItem item = new GstInvoiceItem();
        item.setProductName("Delivery Charges");
        item.setBillDescription("Delivery charges");
        item.setSacCode("996511");
        item.setQuantity(1);
        item.setTaxableValue(50.0);
        item.setCgstRate(0.0);
        item.setSgstRate(0.0);
        item.setIgstRate(18.0);
        item.setIgstAmount(9.0);
        item.setCessRate(0.0);
        item.setCessAmount(0.0);
        return item;
    }

    @Test
    void shouldRefuseToRegister_whenCredentialsAreMissing() {
        // Half-configured must fail loudly. Quietly falling back to the simulator here would
        // put a made-up IRN on a real invoice, which is the whole thing to avoid.
        configure("id", "", "user", "pass", "33AABCU9603R1ZM", "key");

        LiveGstnClient.IrpException ex = assertThrows(
                LiveGstnClient.IrpException.class, () -> client.generateIrn(invoice));
        assertTrue(ex.getMessage().contains("not fully "),
                "the message should say what is missing, not just that it failed");
    }

    @Test
    void shouldNotReportItselfAsSimulated() {
        // ComplianceService surfaces this to the operator; getting it backwards would tell
        // someone their invoices were real when they were not, or the reverse.
        assertFalse(client.isSimulated());
        assertEquals("GSTN_LIVE", client.getName());
    }

    @Test
    void shouldKnowWhenItsCredentialsAreIncomplete() {
        // The readiness screen answers "is the IRP really on?" from this. Half a configuration
        // is not a configuration - it is the MISCONFIGURED state, which must refuse, not pretend.
        assertFalse(client.isConfigured(), "a blank client is not configured");

        configure("id", "secret", "user", "pass", "33AABCU9603R1ZM", "key");

        assertTrue(client.isConfigured(), "all six credentials present means ready to try the portal");
    }

    @Test
    void shouldRefuseFeaturesItHasNotImplementedRatherThanReturnSomething() {
        // These are separate portals with their own schemas. Returning an empty map would read
        // as "no e-way bill needed" instead of "never asked".
        configure("id", "secret", "user", "pass", "33AABCU9603R1ZM", "key");

        assertThrows(LiveGstnClient.IrpException.class, () -> client.generateEwayBill(invoice));
        assertThrows(LiveGstnClient.IrpException.class, () -> client.validateGstin("33AABCU9603R1ZM"));
        assertThrows(LiveGstnClient.IrpException.class, () -> client.fetchGstr2bData("33AABCU9603R1ZM", "082026"));
        assertThrows(LiveGstnClient.IrpException.class, () -> client.fileReturn("33AABCU9603R1ZM", "GSTR1", "082026"));
    }

    @Test
    void shouldStillAnswerFilingDueDates_whichAreStatutoryNotPortalCalls() {
        assertNotNull(client.getFilingDueDate("GSTR1", "082026"));
    }

    @Test
    void shouldFailRatherThanReachTheNetwork_whenNotConfigured() {
        // Nothing is configured at all, which is the state of every environment by default.
        assertThrows(LiveGstnClient.IrpException.class, () -> client.generateIrn(invoice));
    }

    // ------------------------------------------------------------ payload

    @Test
    void payload_shouldCarryAnItemListWithOneEntryPerLine() {
        List<GstInvoiceItem> items = new ArrayList<>();
        items.add(goodsItem());
        items.add(serviceItem());
        invoice.setItems(items);

        Map<String, Object> payload = client.buildInvoicePayload(invoice);

        assertNotNull(payload.get("ItemList"), "a payload without ItemList is rejected by the IRP");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemList = (List<Map<String, Object>>) payload.get("ItemList");
        assertEquals(2, itemList.size());
        assertEquals("1", itemList.get(0).get("SlNo"));
        assertEquals("2", itemList.get(1).get("SlNo"));
    }

    @Test
    void payload_shouldSendGoodsLinesWithTheirHsnAndServicesWithTheirSac() {
        List<GstInvoiceItem> items = new ArrayList<>();
        items.add(goodsItem());
        items.add(serviceItem());
        invoice.setItems(items);

        Map<String, Object> payload = client.buildInvoicePayload(invoice);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemList = (List<Map<String, Object>>) payload.get("ItemList");

        Map<String, Object> goods = itemList.get(0);
        assertEquals("N", goods.get("IsServc"));
        assertEquals("220210", goods.get("HsnCd"));
        assertEquals("Fizzy drink", goods.get("PrdDesc"));

        Map<String, Object> service = itemList.get(1);
        assertEquals("Y", service.get("IsServc"), "delivery is a service and must be flagged so");
        assertEquals("996511", service.get("HsnCd"), "a service line carries its SAC in the HSN cell");
        assertEquals("Delivery charges", service.get("PrdDesc"));
    }

    @Test
    void payload_shouldReconcileEachLineGstAndItemTotal() {
        List<GstInvoiceItem> items = new ArrayList<>();
        items.add(goodsItem());
        items.add(serviceItem());
        invoice.setItems(items);

        Map<String, Object> payload = client.buildInvoicePayload(invoice);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemList = (List<Map<String, Object>>) payload.get("ItemList");

        Map<String, Object> goods = itemList.get(0);
        assertEquals(40.0, goods.get("GstRt"), "intra-state GST is the combined CGST+SGST rate");
        assertEquals(20.0, goods.get("CgstAmt"));
        assertEquals(20.0, goods.get("SgstAmt"));
        assertEquals(0.0, goods.get("IgstAmt"));
        assertEquals(2, goods.get("Qty"));
        assertEquals("BTL", goods.get("Unit"));
        assertEquals(50.0, goods.get("UnitPrice"));
        assertEquals(140.0, goods.get("TotItemVal"),
                "line value must be assessable value plus the GST on it");

        Map<String, Object> service = itemList.get(1);
        assertEquals(18.0, service.get("GstRt"));
        assertEquals(9.0, service.get("IgstAmt"));
        assertEquals(59.0, service.get("TotItemVal"));
    }

    @Test
    void payload_shouldCarryCessOnlyWhereTheLawLeviesIt() {
        GstInvoiceItem taxed = goodsItem();
        taxed.setCessRate(0.0);
        taxed.setCessAmount(0.0);
        GstInvoiceItem panMasala = goodsItem();
        panMasala.setProductName("Pan masala");
        panMasala.setHsnCode("21069020");
        panMasala.setTaxableValue(200.0);
        panMasala.setUnitPrice(200.0);
        panMasala.setQuantity(1);
        panMasala.setCgstAmount(40.0);
        panMasala.setSgstAmount(40.0);
        panMasala.setCessRate(0.0);
        panMasala.setCessAmount(0.0);
        invoice.setItems(List.of(taxed, panMasala));

        Map<String, Object> payload = client.buildInvoicePayload(invoice);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemList = (List<Map<String, Object>>) payload.get("ItemList");

        assertFalse(itemList.get(0).containsKey("CesRt"));
        // Cess has been nil for pan masala since 01-02-2026; a nil-rate line simply omits the
        // cess fields rather than sending a zero, which is what the schema expects.
        assertFalse(itemList.get(1).containsKey("CesRt"));
    }

    @Test
    void payload_shouldEmitCessFields_whenRateIsPositive() {
        GstInvoiceItem item = goodsItem();
        item.setTaxableValue(100.0);
        item.setCessRate(12.0);
        item.setCessAmount(12.0);
        item.setCgstAmount(14.0);
        item.setSgstAmount(14.0);
        invoice.setItems(List.of(item));

        Map<String, Object> payload = client.buildInvoicePayload(invoice);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemList = (List<Map<String, Object>>) payload.get("ItemList");

        assertEquals(12.0, itemList.get(0).get("CesRt"));
        assertEquals(12.0, itemList.get(0).get("CesAmt"));
        assertEquals(140.0, itemList.get(0).get("TotItemVal"), "cess counts into the line total");
    }

    @Test
    void payload_shouldDeriveSellerStateFromTheGstin_soError2258CannotTrigger() {
        invoice.setSellerLegalName("Cauvery Stores");
        invoice.setSellerAddress("Trichy");
        invoice.setBuyerGstin("29AABCP9603R1ZH");
        invoice.setBuyerName("Buyer Co");
        invoice.setBuyerAddress("Bangalore");
        invoice.setBuyerStateCode("29");
        invoice.setDeliveryStateCode("29");
        invoice.setTaxableAmount(150.0);
        invoice.setCgstAmount(0.0);
        invoice.setSgstAmount(0.0);
        invoice.setIgstAmount(9.0);
        invoice.setTotalCess(0.0);
        invoice.setTotalAmount(159.0);

        Map<String, Object> payload = client.buildInvoicePayload(invoice);

        @SuppressWarnings("unchecked")
        Map<String, Object> seller = (Map<String, Object>) payload.get("SellerDtls");
        assertEquals("33", seller.get("Stcd"), "state code must come from the GSTIN, not the address");
        assertEquals("Cauvery Stores", seller.get("LglNm"));
        assertEquals("Trichy", seller.get("Addr1"));

        @SuppressWarnings("unchecked")
        Map<String, Object> buyer = (Map<String, Object>) payload.get("BuyerDtls");
        assertEquals("29", buyer.get("Stcd"));
        assertEquals("29AABCP9603R1ZH", buyer.get("Gstin"));
    }

    @Test
    void payload_shouldMarkUnregisteredBuyerAsUrpAndB2C() {
        invoice.setBuyerGstin(null);
        invoice.setBuyerName("Cash Customer");
        invoice.setDeliveryStateCode("07");
        invoice.setTaxableAmount(100.0);
        invoice.setCgstAmount(20.0);
        invoice.setSgstAmount(20.0);
        invoice.setTotalAmount(140.0);

        Map<String, Object> payload = client.buildInvoicePayload(invoice);

        @SuppressWarnings("unchecked")
        Map<String, Object> tran = (Map<String, Object>) payload.get("TranDtls");
        assertEquals("B2C", tran.get("SupTyp"));
        @SuppressWarnings("unchecked")
        Map<String, Object> buyer = (Map<String, Object>) payload.get("BuyerDtls");
        assertEquals("URP", buyer.get("Gstin"));
        assertEquals("07", buyer.get("Pos"));
        assertFalse(buyer.containsKey("Stcd"),
                "an unregistered buyer has no GSTIN from which to derive a state code");
    }

    @Test
    void payload_shouldTotalTheHeader_andDeclareNilStateCess() {
        invoice.setBuyerGstin("29AABCP9603R1ZH");
        invoice.setTaxableAmount(150.0);
        invoice.setCgstAmount(0.0);
        invoice.setSgstAmount(0.0);
        invoice.setIgstAmount(9.0);
        invoice.setTotalCess(0.0);
        invoice.setTotalAmount(159.0);

        Map<String, Object> payload = client.buildInvoicePayload(invoice);

        @SuppressWarnings("unchecked")
        Map<String, Object> val = (Map<String, Object>) payload.get("ValDtls");
        assertEquals(150.0, val.get("AssVal"));
        assertEquals(0.0, val.get("CessVal"));
        assertEquals(0.0, val.get("StCesVal"), "no state cess exists; the field is sent as zero");
        assertEquals(159.0, val.get("TotInvVal"));
    }

    @Test
    void payload_shouldDefaultMissingLineFigures_ratherThanSendNull() {
        GstInvoiceItem sparse = new GstInvoiceItem();
        sparse.setProductName("Sparse");
        invoice.setItems(List.of(sparse));

        Map<String, Object> payload = client.buildInvoicePayload(invoice);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemList = (List<Map<String, Object>>) payload.get("ItemList");
        Map<String, Object> line = itemList.get(0);
        assertEquals(1, line.get("Qty"));
        assertEquals("NOS", line.get("Unit"));
        assertEquals(0.0, line.get("GstRt"));
        assertEquals(0.0, line.get("TotItemVal"));
    }
}
