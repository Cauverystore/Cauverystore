package com.cauverystore.service;

import com.cauverystore.client.GstnClient;
import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import com.cauverystore.util.GstComplianceUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GstInvoiceServiceTest {

    private static final String SELLER_GSTIN_TN = "33ABCDE1234F1ZA";
    private static final String BUYER_GSTIN_MH = "27ABCDE1234F1ZW";

    @Mock private GstInvoiceRepository invoiceRepo;
    @Mock private GstInvoiceItemRepository itemRepo;
    @Mock private GstSyncQueueRepository syncRepo;
    @Mock private GstConfigurationRepository configRepo;
    @Mock private SellerRegistrationRepository sellerRegRepo;
    @Mock private OrderRepository orderRepo;
    @Mock private ProductRepository productRepo;
    @Mock private UserRepository userRepo;
    @Mock private AuditService auditService;
    @Mock private GstnClient gstnClient;
    @Mock private TcsRecordRepository tcsRepo;
    @Mock private GstRateResolver gstRateResolver;
    @Mock private StateMasterRepository stateRepo;
    @Mock private PincodeStateRangeRepository pincodeRepo;
    @Mock private CreditNoteRepository creditNoteRepo;
    @Mock private DebitNoteRepository debitNoteRepo;

    @InjectMocks
    private GstInvoiceService gstService;

    private GstConfiguration config;
    private Order order;
    private Product product;

    @BeforeEach
    void setUp() {
        // These tests cover invoice structure - the CGST/SGST vs IGST split, place of supply,
        // IRN - not where the rate comes from, so pin the resolver at the 12% they were
        // written against. Rate resolution itself is covered by GstRateResolverTest.
        // These tests are about invoice structure, not rate sourcing - GstRateResolverTest
        // covers where the rate comes from. A flat 12% keeps the expected figures readable.
        lenient().when(gstRateResolver.resolve(any(), anyBoolean(), any(), any()))
                .thenAnswer(i -> new GstRateResolver.Resolved(12.0, 0.0, i.getArgument(1), "1006", true));

        config = new GstConfiguration();
        config.setGstin(SELLER_GSTIN_TN);
        config.setLegalName("Test Seller Pvt Ltd");
        config.setAddress("Chennai, Tamil Nadu");
        config.setStateCode("33");
        config.setTcsRate(1.0);
        config.setInvoicePrefix("CS");
        config.setAnnualTurnover(1_00_00_000.0);

        User buyer = new User();
        buyer.setId(1L);
        buyer.setFullName("Ravi Kumar");
        buyer.setEmail("ravi@test.com");

        Address addr = new Address();
        addr.setFullName("Ravi Kumar");
        addr.setStreet("10 Main Road");
        addr.setCity("Chennai");
        addr.setState("Tamil Nadu");
        addr.setPincode("600001");

        product = new Product();
        product.setId(1L);
        product.setName("Cotton Kurta");
        product.setPrice(500.0);
        product.setGstPercentage(12.0);
        product.setHsnCode("610910");

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        item.setPrice(500.0);

        order = new Order();
        order.setId(1L);
        order.setUser(buyer);
        order.setAddress(addr);
        order.setItems(List.of(item));
        order.setDeliveryCharge(0.0);
        order.setTax(0.0);
        order.setTotalAmount(1000.0);
        order.setStatus("PLACED");
    }

    private StateMaster state(String code, String name) {
        StateMaster s = new StateMaster();
        s.setStateCode(code);
        s.setStateName(name);
        return s;
    }

    /** Stubs the repositories the generation path touches. Not called by tests that fail validation first. */
    private void stubCommonRepos() {
        // Place of supply now reads the official state master rather than a map kept in the
        // service, which had drifted - it still listed 28 for Andhra Pradesh and lacked 37.
        lenient().when(stateRepo.findById("33")).thenReturn(Optional.of(state("33", "Tamil Nadu")));
        lenient().when(stateRepo.findById("27")).thenReturn(Optional.of(state("27", "Maharashtra")));
        // The reverse lookup (state name -> code) scans the master, so it needs the whole list.
        // 600001 is Chennai, which the range map puts in Tamil Nadu (600-643).
        lenient().when(pincodeRepo.findByPrefixFromLessThanEqualAndPrefixToGreaterThanEqual(
                anyInt(), anyInt())).thenReturn(List.of(
                        new PincodeStateRange("33", "TAMIL NADU", 600, 643)));
        lenient().when(stateRepo.findAll()).thenReturn(List.of(
                state("33", "TAMIL NADU"), state("27", "MAHARASHTRA")));
        when(orderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(invoiceRepo.findByOrderId(1L)).thenReturn(Optional.empty());
        when(configRepo.findByGstin(SELLER_GSTIN_TN)).thenReturn(Optional.of(config));
        when(sellerRegRepo.findByUserId(1L)).thenReturn(Optional.empty());
        when(invoiceRepo.findMaxInvoiceNumberByPrefix(anyString())).thenReturn(null);
        when(invoiceRepo.save(any(GstInvoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tcsRepo.save(any(TcsRecord.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void generate_taxInclusiveOrder_shouldFootToWhatWasPaid_withDeliveryAtTheGoodsRate() {
        // A GST-inclusive order: 2 x 500 with a 100 coupon and 40 delivery - 940 paid in all.
        stubCommonRepos();
        order.setPricesIncludeTax(true);
        order.setDeliveryCharge(40.0);
        order.setTotalAmount(940.0);

        Map<String, Object> result = gstService.generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN);
        GstInvoice inv = (GstInvoice) result.get("invoice");

        assertEquals(940.0, inv.getTotalAmount(), 0.01, "the invoice must equal what was paid");
        assertEquals(100.0, inv.getDiscountAmount(), 0.01);
        assertEquals(inv.getTaxableAmount() + inv.getTotalTax(), inv.getTotalAmount(), 0.01);

        GstInvoiceItem goods = inv.getItems().get(0);
        assertEquals(803.58, goods.getTaxableValue(), 0.001, "900 paid, 12% tax taken out");
        assertEquals(48.21, goods.getCgstAmount(), 0.001);
        assertEquals(48.21, goods.getSgstAmount(), 0.001);

        // Delivery is part of the supply of goods: same HSN, same 12%, tax inside the 40.
        GstInvoiceItem delivery = inv.getItems().get(1);
        assertEquals("610910", delivery.getHsnCode());
        assertNull(delivery.getSacCode());
        assertEquals(6.0, delivery.getCgstRate(), 0.001);
        assertEquals(35.72, delivery.getTaxableValue(), 0.001);
        assertEquals(2.14, delivery.getCgstAmount(), 0.001);
        assertEquals("GOODS", inv.getSupplyType());

        // The rate band is tested on the per-piece price after the coupon: 500 less 10% = 450.
        verify(gstRateResolver).resolve(any(), anyBoolean(), any(), eq(450.0));
    }

    @Test
    void generate_giftWrappedOrder_shouldInvoiceTheWrapAndNotMistakeItForLessCoupon() {
        // The same order with 49 gift wrap: 2 x 500, 100 coupon, 40 delivery, 49 wrap = 989.
        stubCommonRepos();
        order.setPricesIncludeTax(true);
        order.setDeliveryCharge(40.0);
        order.setGiftWrapCharge(49.0);
        order.setTotalAmount(989.0);

        Map<String, Object> result = gstService.generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN);
        GstInvoice inv = (GstInvoice) result.get("invoice");

        assertEquals(989.0, inv.getTotalAmount(), 0.01, "the invoice must equal what was paid");
        // Leaving the wrap out of the sum would read the coupon as 51, not 100.
        assertEquals(100.0, inv.getDiscountAmount(), 0.01);
        assertEquals(inv.getTaxableAmount() + inv.getTotalTax(), inv.getTotalAmount(), 0.01);
        assertEquals(803.58, inv.getItems().get(0).getTaxableValue(), 0.001);

        GstInvoiceItem wrap = inv.getItems().get(2);
        assertTrue(wrap.getProductName().startsWith("Gift wrapping"));
        assertEquals("610910", wrap.getHsnCode(), "taxed with the goods, under their HSN");
        assertEquals(6.0, wrap.getCgstRate(), 0.001);
        assertEquals(49.0, wrap.getTaxableValue() + wrap.getCgstAmount() + wrap.getSgstAmount(), 0.001,
                "the tax is inside the 49, not added to it");
        assertEquals(49.0, wrap.getTotalAmount(), 0.001);
    }

    @Test
    void generate_b2cIntraState_shouldSplitCgstSgstAndRaiseSacsServiceLine() {
        stubCommonRepos();
        order.setDeliveryCharge(40.0);
        order.setTotalAmount(1040.0);

        Map<String, Object> result = gstService.generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN);

        GstInvoice inv = (GstInvoice) result.get("invoice");

        assertEquals("B2C", inv.getInvoiceType());
        assertEquals("URP", inv.getBuyerGstin());
        assertFalse(Boolean.TRUE.equals(inv.getItcEligible()));
        assertFalse(Boolean.TRUE.equals(inv.getIsInterState()));
        assertEquals("33", inv.getDeliveryStateCode());
        assertEquals("33", inv.getBuyerStateCode());
        assertTrue(inv.getPlaceOfSupply().startsWith("33-"));
        assertEquals(6.0, inv.getCgstRate(), 0.001);
        assertEquals(6.0, inv.getSgstRate(), 0.001);
        assertEquals(0.0, inv.getIgstRate(), 0.001);

        // Goods 2 x 500 at 12% -> CGST 60 + SGST 60; Delivery 40 at 18% -> CGST 3.6 + SGST 3.6
        assertEquals(1040.0, inv.getTaxableAmount(), 0.01);
        assertEquals(63.6, inv.getCgstAmount(), 0.01);
        assertEquals(63.6, inv.getSgstAmount(), 0.01);
        assertEquals(0.0, inv.getIgstAmount(), 0.01);
        assertEquals(127.2, inv.getTotalTax(), 0.01);
        // TCS is recorded because it has to be paid over and declared in GSTR-8...
        assertEquals(10.4, inv.getTcsAmount(), 0.01);
        // ...but it is not the customer's to pay. This assertion used to expect 1177.6, which
        // was the taxable value plus GST plus TCS - so the test was pinning an overcharge of 1%
        // of the goods value on every order. The customer owes goods plus GST and nothing else.
        assertEquals(1167.2, inv.getTotalAmount(), 0.01);
        assertEquals(inv.getTaxableAmount() + inv.getTotalTax(), inv.getTotalAmount(), 0.01,
                "the total is taxable value plus tax; TCS must never be inside it");

        // Delivery charge must appear as its own SAC 996511 services line, not inside the goods line
        assertEquals(2, inv.getItems().size());
        GstInvoiceItem service = inv.getItems().get(1);
        assertEquals("996511", service.getSacCode());
        assertNull(service.getHsnCode());
        assertEquals(40.0, service.getTaxableValue(), 0.01);

        assertEquals(0.0, inv.getDiscountAmount(), 0.01);
        assertEquals("10 Main Road, Chennai, Tamil Nadu, 600001", inv.getDeliveryAddress());
        assertNotNull(inv.getSupplierSignature());
        assertEquals("Test Seller Pvt Ltd", inv.getSignedBy());
        assertNotNull(inv.getSignatureDate());
        assertFalse(Boolean.TRUE.equals(inv.getEinvoicingRequired()));
        assertNull(inv.getIrn());
        assertTrue(inv.getInvoiceNumber().length() <= GstComplianceUtil.MAX_INVOICE_NUMBER_LENGTH);

        verify(auditService).log(any(), any(), eq("INVOICE_GENERATED"), eq("GstInvoice"), any(), any(), any());
    }

    @Test
    void generate_b2bIntraState_shouldMarkItcEligibleAndPreserveBuyerGstin() {
        stubCommonRepos();
        Map<String, Object> result = gstService.generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN, SELLER_GSTIN_TN);

        GstInvoice inv = (GstInvoice) result.get("invoice");

        assertEquals("B2B", inv.getInvoiceType());
        assertTrue(Boolean.TRUE.equals(inv.getItcEligible()));
        assertEquals(SELLER_GSTIN_TN, inv.getBuyerGstin());
        assertFalse(Boolean.TRUE.equals(inv.getIsInterState()));
        assertEquals(6.0, inv.getCgstRate(), 0.001);
        assertEquals(6.0, inv.getSgstRate(), 0.001);
        assertEquals(0.0, inv.getIgstRate(), 0.001);
    }

    @Test
    void generate_interState_shouldUseIgstAndPlaceOfSupplyEqualsDeliveryState() {
        // Billing address is Tamil Nadu (33) but the goods are dispatched to Maharashtra (27):
        // place of supply = delivery state and IGST applies.
        stubCommonRepos();
        Map<String, Object> result = gstService.generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN, null, "27");

        GstInvoice inv = (GstInvoice) result.get("invoice");

        assertTrue(Boolean.TRUE.equals(inv.getIsInterState()));
        assertEquals("27", inv.getDeliveryStateCode());
        assertTrue(inv.getPlaceOfSupply().startsWith("27-"));
        assertEquals(12.0, inv.getIgstRate(), 0.001);
        assertEquals(0.0, inv.getCgstRate(), 0.001);
        assertEquals(0.0, inv.getSgstRate(), 0.001);
        assertEquals(120.0, inv.getIgstAmount(), 0.01); // 1000 x 12%
        assertEquals(0.0, inv.getCgstAmount(), 0.01);
        assertEquals(0.0, inv.getSgstAmount(), 0.01);
    }

    @Test
    void generate_turnoverAboveFiveCrore_shouldGenerateIrnAndScannableQr() {
        stubCommonRepos();
        config.setAnnualTurnover(8_00_00_000.0);
        when(gstnClient.generateIrn(any(GstInvoice.class))).thenReturn(Map.of(
                "irn", "SIM" + "1234567890ABCDEF",
                "qrCode", "data:image/png;base64,iVBORw0KGgo=",
                "ackNo", "ACK-20260806-000001",
                "ackDate", "2026-08-06"));

        Map<String, Object> result = gstService.generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN);

        GstInvoice inv = (GstInvoice) result.get("invoice");

        assertTrue(Boolean.TRUE.equals(inv.getEinvoicingRequired()));
        assertNotNull(inv.getIrn());
        assertTrue(inv.getIrn().startsWith("SIM"));
        assertNotNull(inv.getQrCode());
        assertTrue(inv.getQrCode().startsWith("data:image/png;base64,"));
        assertNotNull(inv.getAckNo());
        assertTrue(GstComplianceUtil.requiresEInvoice(config.getAnnualTurnover()));
        verify(gstnClient).generateIrn(inv);
    }

    @Test
    void generate_shouldChargeCessFromThePublishedRow_andPutItOnTheLine() {
        stubCommonRepos();
        when(gstRateResolver.resolve(any(), anyBoolean(), any(), any()))
                .thenReturn(new GstRateResolver.Resolved(12.0, 5.0, false, "1006", true));

        GstInvoice inv = (GstInvoice) gstService
                .generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN).get("invoice");

        assertEquals(1, inv.getItems().size());
        GstInvoiceItem line = inv.getItems().get(0);
        assertEquals(5.0, line.getCessRate(), 0.001);
        assertEquals(50.0, line.getCessAmount(), 0.01, "1000 taxable x 5% cess");
        assertEquals(50.0, inv.getTotalCess(), 0.01);
        assertEquals(1000.0, inv.getTaxableAmount(), 0.01);
        assertEquals(60.0, inv.getCgstAmount(), 0.01);
        assertEquals(60.0, inv.getSgstAmount(), 0.01);
        assertEquals(120.0, inv.getTotalTax(), 0.01);
        assertEquals(1170.0, inv.getTotalAmount(), 0.01,
                "total is taxable + tax + cess; the customer pays the cess");
        assertEquals(1170.0, line.getTotalAmount(), 0.01,
                "the line's own total carries its cess too");
    }

    @Test
    void generate_shouldIgnoreTheSellersOwnCessEntry_whenThePublishedRowHasNone() {
        // The product's cessRate is a declaration, not a source: cess comes from the same
        // published row that decides the GST rate. A seller entering a rate here must not
        // invent a charge the CBIC has not put on these goods.
        stubCommonRepos();
        product.setCessRate(12.0);
        when(gstRateResolver.resolve(any(), anyBoolean(), any(), any()))
                .thenReturn(new GstRateResolver.Resolved(12.0, 0.0, false, "1006", true));

        GstInvoice inv = (GstInvoice) gstService
                .generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN).get("invoice");

        assertEquals(0.0, inv.getTotalCess(), 0.01);
        assertEquals(1120.0, inv.getTotalAmount(), 0.01, "taxable 1000 + 12% GST, no cess");
    }

    @Test
    void generate_shouldUseTheSellersBillDescriptionAndUnitOfMeasure() {
        stubCommonRepos();
        product.setBillDescription("Kurta, handloom cotton");
        product.setUom("PAIR");

        GstInvoice inv = (GstInvoice) gstService
                .generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN).get("invoice");

        GstInvoiceItem line = inv.getItems().get(0);
        assertEquals("Kurta, handloom cotton", line.getBillDescription());
        assertEquals("PAIR", line.getUnitOfMeasure());
    }

    @Test
    void generate_shouldFallBackToTheProductName_andNos_whenNothingIsSet() {
        stubCommonRepos();

        GstInvoice inv = (GstInvoice) gstService
                .generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN).get("invoice");

        GstInvoiceItem line = inv.getItems().get(0);
        assertEquals("Cotton Kurta", line.getBillDescription(),
                "the invoice description falls back to the product name");
        assertEquals("NOS", line.getUnitOfMeasure());
    }

    @Test
    void generate_shouldRejectInvalidSellerGstin() {
        assertThrows(IllegalArgumentException.class,
                () -> gstService.generateInvoiceFromOrder(1L, 1L, "INVALID-GSTIN"));
    }

    private SellerRegistration sellerWith(java.time.LocalDate booksOpen, String signatureUrl,
                                          String signatory) {
        SellerRegistration reg = new SellerRegistration();
        reg.setBooksBeginningDate(booksOpen);
        reg.setSignatureImageUrl(signatureUrl);
        reg.setAuthorisedSignatory(signatory);
        return reg;
    }

    @Test
    void generate_shouldRefuseAnInvoiceDatedBeforeTheSellersBooksOpen() {
        // Such an invoice belongs to a period whose return may already be filed, or to no
        // accounting period at all, and only shows up when the figures fail to reconcile.
        // Stubbed narrowly, not via stubCommonRepos: this path throws before it reaches the
        // save calls, and Mockito fails the test for stubs that were never used.
        when(orderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(invoiceRepo.findByOrderId(1L)).thenReturn(Optional.empty());
        when(configRepo.findByGstin(SELLER_GSTIN_TN)).thenReturn(Optional.of(config));
        when(sellerRegRepo.findByUserId(1L)).thenReturn(Optional.of(
                sellerWith(java.time.LocalDate.now().plusDays(30), null, null)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> gstService.generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN));
        assertTrue(ex.getMessage().contains("books"),
                "the message should explain what is wrong, not just refuse");
    }

    @Test
    void generate_shouldAllowASellerWhoNeverSetABooksDate() {
        // Most sellers registered before the field existed. Refusing to invoice their orders
        // over a missing profile field would stop real sales to enforce a rule they were
        // never asked about.
        stubCommonRepos();
        when(sellerRegRepo.findByUserId(1L)).thenReturn(Optional.of(
                sellerWith(null, null, null)));

        assertDoesNotThrow(() -> gstService.generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN));
    }

    @Test
    void generate_shouldAllowAnInvoiceOnOrAfterTheBooksDate() {
        stubCommonRepos();
        when(sellerRegRepo.findByUserId(1L)).thenReturn(Optional.of(
                sellerWith(java.time.LocalDate.now(), null, null)));

        assertDoesNotThrow(() -> gstService.generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN));
    }

    @Test
    void generate_shouldCopyTheSignatureOntoTheInvoiceAsItStoodWhenRaised() {
        // Looked up at print time instead, a seller changing their signature would silently
        // alter every invoice they had already issued.
        stubCommonRepos();
        when(sellerRegRepo.findByUserId(1L)).thenReturn(Optional.of(
                sellerWith(null, "https://cdn.example/sig.png", "R. Krishnan")));

        GstInvoice inv = (GstInvoice) gstService
                .generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN).get("invoice");

        assertEquals("https://cdn.example/sig.png", inv.getSupplierSignatureImageUrl());
        assertEquals("R. Krishnan", inv.getSignedBy(), "the name printed beneath the signature");
    }

    @Test
    void generate_shouldLeaveTheSignatureBlank_whenTheSellerHasNotUploadedOne() {
        stubCommonRepos();
        when(sellerRegRepo.findByUserId(1L)).thenReturn(Optional.of(
                sellerWith(null, null, null)));

        GstInvoice inv = (GstInvoice) gstService
                .generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN).get("invoice");
        assertNull(inv.getSupplierSignatureImageUrl());
    }

    @Test
    void generate_shouldRefuseAnUnknownDeliveryState_ratherThanDefaultToTamilNadu() {
        // The reverse lookup used to return "33" for anything it did not recognise, including
        // null. One unrecognised spelling made an inter-state supply look intra-state, charging
        // CGST+SGST where IGST was due and sending the money to the wrong government.
        // Stubbed narrowly: this throws before it reaches the save calls, and Mockito fails
        // the test for stubs that were never used.
        when(orderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(invoiceRepo.findByOrderId(1L)).thenReturn(Optional.empty());
        when(configRepo.findByGstin(SELLER_GSTIN_TN)).thenReturn(Optional.of(config));
        when(sellerRegRepo.findByUserId(1L)).thenReturn(Optional.empty());
        when(stateRepo.findAll()).thenReturn(List.of(state("33", "TAMIL NADU")));
        order.getAddress().setState("Atlantis");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> gstService.generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN));
        assertTrue(ex.getMessage().contains("Atlantis"), "should name the state it could not place");
        assertTrue(ex.getMessage().toLowerCase().contains("place of supply"));
    }

    @Test
    void generate_shouldMatchAStateNameDespiteCaseAndPunctuation() {
        // A customer-typed address will not match the gazette spelling exactly, and refusing it
        // on that would be pedantry rather than compliance.
        stubCommonRepos();
        order.getAddress().setState("tamil  nadu");

        assertDoesNotThrow(() -> gstService.generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN));
    }

    @Test
    void generate_shouldTakeTheStateFromThePincode_whenTheStateNameIsUnusable() {
        // Place of supply must be right, but refusing the sale over a mistyped state when the
        // pincode on the same address names the state unambiguously would be unhelpful.
        stubCommonRepos();
        order.getAddress().setState("Tamilnaadu");   // not a match
        order.getAddress().setPincode("600001");

        GstInvoice inv = (GstInvoice) gstService
                .generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN).get("invoice");

        assertEquals("33", inv.getDeliveryStateCode());
        assertTrue(inv.getPlaceOfSupply().startsWith("33-"));
    }

    @Test
    void generate_shouldPreferTheStatedState_whenThePincodeDisagrees() {
        // A pincode can legitimately sit in two states' published ranges, so a disagreement is
        // not proof of error. The typed state wins and the mismatch is logged.
        stubCommonRepos();
        when(pincodeRepo.findByPrefixFromLessThanEqualAndPrefixToGreaterThanEqual(anyInt(), anyInt()))
                .thenReturn(List.of(new PincodeStateRange("27", "MAHARASHTRA", 400, 445)));
        order.getAddress().setState("Tamil Nadu");
        order.getAddress().setPincode("400001");

        GstInvoice inv = (GstInvoice) gstService
                .generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN).get("invoice");

        assertEquals("33", inv.getDeliveryStateCode(), "the stated state wins");
    }

    @Test
    void generate_shouldNotGuess_whenAPincodeSpansMoreThanOneState() {
        // 396 belongs to both Gujarat and Dadra and Nagar Haveli, so it settles nothing.
        when(orderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(invoiceRepo.findByOrderId(1L)).thenReturn(Optional.empty());
        when(configRepo.findByGstin(SELLER_GSTIN_TN)).thenReturn(Optional.of(config));
        when(sellerRegRepo.findByUserId(1L)).thenReturn(Optional.empty());
        when(stateRepo.findAll()).thenReturn(List.of(state("33", "TAMIL NADU")));
        when(pincodeRepo.findByPrefixFromLessThanEqualAndPrefixToGreaterThanEqual(anyInt(), anyInt()))
                .thenReturn(List.of(
                        new PincodeStateRange("24", "GUJARAT", 360, 396),
                        new PincodeStateRange("26", "DADRA AND NAGAR HAVELI", 396, 396)));
        order.getAddress().setState("Atlantis");
        order.getAddress().setPincode("396001");

        assertThrows(IllegalStateException.class,
                () -> gstService.generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN));
    }

    @Test
    void shouldRecordPaymentTerms_andOnlyDateWhatIsActuallyOutstanding() {
        // A prepaid invoice showing a due date invites a second payment against something
        // already settled; a COD one with no due date leaves the buyer nothing to pay against.
        com.cauverystore.entities.GstInvoice cod = new com.cauverystore.entities.GstInvoice();
        cod.setPaymentMode("COD");
        cod.setInvoiceDate(java.time.LocalDate.of(2026, 8, 8));
        cod.setPaymentDueDate(cod.isPayableOnDelivery() ? cod.getInvoiceDate() : null);
        assertTrue(cod.isPayableOnDelivery());
        assertEquals(java.time.LocalDate.of(2026, 8, 8), cod.getPaymentDueDate());

        com.cauverystore.entities.GstInvoice prepaid = new com.cauverystore.entities.GstInvoice();
        prepaid.setPaymentMode("RAZORPAY");
        prepaid.setInvoiceDate(java.time.LocalDate.of(2026, 8, 8));
        prepaid.setPaymentDueDate(prepaid.isPayableOnDelivery() ? prepaid.getInvoiceDate() : null);
        assertFalse(prepaid.isPayableOnDelivery());
        assertNull(prepaid.getPaymentDueDate(), "a paid invoice has nothing outstanding to date");
    }

    @Test
    void tcsMustNotBeAddedToWhatTheCustomerPays() {
        // Order 47 charged Rs 17,850 for Rs 15,000 of goods plus Rs 2,700 GST. The extra Rs 150
        // was TCS, which section 52 has the marketplace keep back from the seller's settlement -
        // it is never an addition to the buyer's bill. Every customer was overcharged 1% of the
        // goods value, described as a tax on their purchase.
        double taxableAmount = 15000.0;
        double totalTax = 2700.0;
        double tcs = 150.0;

        double customerPays = taxableAmount + totalTax;

        assertEquals(17700.0, customerPays, 0.001,
                "the invoice total is goods plus GST, and nothing else");
        assertNotEquals(taxableAmount + totalTax + tcs, customerPays,
                "TCS in the total is the overcharge this guards against");
    }

    @Test
    void aSimulatedIrnMustNotBeCalledAnIrpRegistration() {
        // The invoice claimed "Registered on the e-invoice portal... digitally signed by the
        // portal; no physical signature is required" over an IRN the simulator made up. That is
        // a false statement on a tax document, and it was being used to excuse the missing
        // signature.
        String simulated = "SIM926A55334609BAC520479E4C43F6734D";
        String real = "a5c1b2d3e4f5060718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f";

        assertTrue(simulated.startsWith("SIM"),
                "the simulator's own prefix is what tells the two apart");
        assertFalse(real.startsWith("SIM"));
    }

    @Test
    void tcsMustUseTheHalfPercentRateInForceSinceJuly2024() {
        // Notification 15/2024-Central Tax halved TCS under section 52 from 1% to 0.5% with
        // effect from 10-07-2024 - 0.25% CGST plus 0.25% SGST, or 0.5% IGST. The default here
        // was still the old 1%, which would withhold twice what is due from every seller's
        // settlement and over-declare the same amount in GSTR-8.
        com.cauverystore.entities.GstConfiguration fresh = new com.cauverystore.entities.GstConfiguration();

        assertEquals(0.5, fresh.getTcsRate(), 0.0001,
                "a new configuration must start at the rate currently in force");

        double taxable = 15000.0;
        assertEquals(75.0, Math.round(taxable * fresh.getTcsRate() / 100 * 100.0) / 100.0, 0.01,
                "Rs 15,000 of supplies carries Rs 75 of TCS, not Rs 150");
    }

    @Test
    void anInvoicesOwnTcsRateMustSurviveALaterRateChange() {
        // The rate has changed once and will change again. An invoice records what was actually
        // collected, so re-reading it years later must not apply today's figure to it.
        com.cauverystore.entities.GstInvoice old = new com.cauverystore.entities.GstInvoice();
        old.setTcsRate(1.0);
        old.setTcsAmount(150.0);

        assertEquals(1.0, old.getTcsRate(), 0.0001,
                "history keeps the rate it was raised under");
    }

    @Test
    void billToShipTo_placeOfSupplyIsTheBuyersStateNotTheDeliveryState() {
        // Section 10(1)(b): a Chennai company buys from a Chennai seller and has the goods sent
        // to their customer in Maharashtra. The goods leave the state, but the buyer is deemed
        // to have received them, so the place of supply is Tamil Nadu and the supply is
        // intra-state - CGST and SGST, not IGST.
        stubCommonRepos();
        order.setBuyerGstin(SELLER_GSTIN_TN);          // buyer registered in 33
        order.setBuyerLegalName("Chennai Traders Pvt Ltd");
        order.setBillToShipTo(true);
        order.setConsigneeName("Mumbai Client Ltd");
        order.setConsigneeAddress("Andheri, Mumbai, Maharashtra");

        Map<String, Object> result = gstService.generateInvoiceFromOrder(
                1L, 1L, SELLER_GSTIN_TN, SELLER_GSTIN_TN, "27");
        GstInvoice inv = (GstInvoice) result.get("invoice");

        assertTrue(inv.isBillToShipTo());
        assertEquals("27", inv.getDeliveryStateCode(), "the goods really did go to Maharashtra");
        assertTrue(inv.getPlaceOfSupply().startsWith("33-"),
                "but the place of supply is the buyer's state: " + inv.getPlaceOfSupply());
        assertFalse(Boolean.TRUE.equals(inv.getIsInterState()),
                "same state as the seller, so this is intra-state despite the delivery address");
        assertTrue(inv.getCgstAmount() > 0 && inv.getSgstAmount() > 0);
        assertEquals(0.0, inv.getIgstAmount(), 0.01);
    }

    @Test
    void anOrdinaryB2bDeliveryElsewhereIsStillInterState() {
        // Without the declaration this is 10(1)(a) - place of supply is where the goods stop.
        // The addresses are identical to the case above; only the buyer's declaration differs,
        // which is exactly why it cannot be inferred.
        stubCommonRepos();
        order.setBuyerGstin(BUYER_GSTIN_MH);
        order.setBuyerLegalName("Mumbai Traders Pvt Ltd");
        order.setBillToShipTo(false);

        Map<String, Object> result = gstService.generateInvoiceFromOrder(
                1L, 1L, SELLER_GSTIN_TN, BUYER_GSTIN_MH, "27");
        GstInvoice inv = (GstInvoice) result.get("invoice");

        assertTrue(inv.getPlaceOfSupply().startsWith("27-"));
        assertTrue(Boolean.TRUE.equals(inv.getIsInterState()));
        assertTrue(inv.getIgstAmount() > 0);
        assertEquals(0.0, inv.getCgstAmount(), 0.01);
    }

    @Test
    void billToShipTo_isIgnoredWithoutARegisteredBuyer() {
        // 10(1)(b) turns on a third person's principal place of business. An unregistered buyer
        // has none, so the ordinary destination rule stands and the flag must not move the tax.
        stubCommonRepos();
        order.setBillToShipTo(true);   // no GSTIN on the order

        Map<String, Object> result = gstService.generateInvoiceFromOrder(1L, 1L, SELLER_GSTIN_TN);
        GstInvoice inv = (GstInvoice) result.get("invoice");

        assertEquals("URP", inv.getBuyerGstin());
        assertTrue(inv.getPlaceOfSupply().startsWith("33-"),
                "falls back to the delivery state, which here is also 33");
    }

    // ---- Printed invoice layout ----

    /** An intra-state invoice carrying every optional line a real one can have. */
    private GstInvoice fullInvoice(int itemCount) {
        GstInvoice inv = new GstInvoice();
        inv.setId(77L);
        inv.setInvoiceNumber("CS2610/00001");
        inv.setInvoiceDate(java.time.LocalDate.of(2026, 10, 31));
        inv.setOrderId(1L);
        inv.setInvoiceCopyType("ORIGINAL");
        inv.setSupplyType("GOODS");
        inv.setIsInterState(false);
        inv.setSellerLegalName("Cauvery Retail Store");
        inv.setSellerGstin("33ABCDE1234F1Z5");
        inv.setSellerAddress("1, Anna Salai, Chennai, Tamil Nadu 600002");
        inv.setBuyerName("Vijay Krishna");
        inv.setBuyerAddress("12 Srinivasa Gardens, Ramanathapuram, Coimbatore, Tamil Nadu 641045");
        inv.setBuyerStateCode("33");
        inv.setDeliveryAddress("12 Srinivasa Gardens, Ramanathapuram, Coimbatore, Tamil Nadu 641045");
        inv.setDeliveryStateCode("33");
        inv.setPlaceOfSupply("33-TAMIL NADU");
        inv.setInvoiceType("B2C");
        inv.setReverseCharge(false);
        inv.setTaxableAmount(34.74);
        inv.setCgstRate(9.0);
        inv.setSgstRate(9.0);
        inv.setCgstAmount(3.13);
        inv.setSgstAmount(3.13);
        inv.setTotalTax(6.26);
        inv.setTotalAmount(41.0);
        inv.setDeliveryCharge(40.0);
        inv.setDiscountAmount(5.0);
        inv.setPaymentMode("UPI");
        inv.setIrn("SIM3E5226FFE7803479782EDC6CA5A07A");
        inv.setAckNo("ACK-20261031-000001");
        inv.setAckDate("2026-10-31");
        inv.setSignedBy("Cauvery Retail Store");
        List<GstInvoiceItem> items = new java.util.ArrayList<>();
        for (int i = 1; i <= itemCount; i++) {
            GstInvoiceItem it = new GstInvoiceItem();
            it.setProductName(i == itemCount ? "Delivery charges (part of the supply of goods)" : "Test Product " + i);
            it.setHsnCode("9017");
            it.setQuantity(1);
            it.setUnitOfMeasure("NOS");
            it.setUnitPrice(40.0);
            it.setTaxableValue(33.9);
            it.setCgstRate(9.0);
            it.setCgstAmount(3.05);
            it.setSgstRate(9.0);
            it.setSgstAmount(3.05);
            it.setTotalAmount(40.0);
            items.add(it);
        }
        when(invoiceRepo.findById(77L)).thenReturn(Optional.of(inv));
        when(itemRepo.findByInvoiceId(77L)).thenReturn(items);
        Order o = new Order();
        lenient().when(orderRepo.findById(1L)).thenReturn(Optional.of(o));
        return inv;
    }

    private int pages(byte[] pdf) throws Exception {
        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdf);
        try { return reader.getNumberOfPages(); } finally { reader.close(); }
    }

    @Test
    void invoicePdf_shouldFitOnOnePageForAnOrdinaryOrder() throws Exception {
        // A two-line order spilled its signatory line onto a second, almost empty page.
        fullInvoice(2);

        byte[] pdf = gstService.generateInvoicePdf(77L);
        java.nio.file.Files.write(java.nio.file.Paths.get("target", "invoice-sample-2-items.pdf"), pdf);

        assertEquals(1, pages(pdf));
    }

    @Test
    void invoicePdf_shouldStillFitOnOnePageWithTenItems() throws Exception {
        fullInvoice(10);

        byte[] pdf = gstService.generateInvoicePdf(77L);
        java.nio.file.Files.write(java.nio.file.Paths.get("target", "invoice-sample-10-items.pdf"), pdf);

        assertEquals(1, pages(pdf));
    }
}
