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
import static org.mockito.ArgumentMatchers.anyString;
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

    @InjectMocks
    private GstInvoiceService gstService;

    private GstConfiguration config;
    private Order order;
    private Product product;

    @BeforeEach
    void setUp() {
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

    /** Stubs the repositories the generation path touches. Not called by tests that fail validation first. */
    private void stubCommonRepos() {
        when(orderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(invoiceRepo.findByOrderId(1L)).thenReturn(Optional.empty());
        when(configRepo.findByGstin(SELLER_GSTIN_TN)).thenReturn(Optional.of(config));
        when(sellerRegRepo.findByUserId(1L)).thenReturn(Optional.empty());
        when(invoiceRepo.findMaxInvoiceNumberByPrefix(anyString())).thenReturn(null);
        when(invoiceRepo.save(any(GstInvoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tcsRepo.save(any(TcsRecord.class))).thenAnswer(inv -> inv.getArgument(0));
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
        assertEquals(10.4, inv.getTcsAmount(), 0.01); // 1% TCS on taxable value
        assertEquals(1177.6, inv.getTotalAmount(), 0.01);

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
    void generate_shouldRejectInvalidSellerGstin() {
        assertThrows(IllegalArgumentException.class,
                () -> gstService.generateInvoiceFromOrder(1L, 1L, "INVALID-GSTIN"));
    }
}
