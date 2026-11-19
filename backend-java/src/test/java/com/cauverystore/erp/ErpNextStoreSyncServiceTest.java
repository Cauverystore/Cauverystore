package com.cauverystore.erp;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The store's own trading going into ERPNext. ERPNext itself is a mock: these tests check what
 * the website sends and how it reacts, not ERPNext's own accounting.
 */
class ErpNextStoreSyncServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long ADMIN = 1L, SELLER = 50L;

    private final ErpNextClient erp = mock(ErpNextClient.class);
    private final ErpNextSetupService setup = mock(ErpNextSetupService.class);
    private final ErpSyncRecordRepository records = mock(ErpSyncRecordRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final GstInvoiceRepository invoices = mock(GstInvoiceRepository.class);
    private final CreditNoteRepository creditNotes = mock(CreditNoteRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final RefundRepository refunds = mock(RefundRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final PurchaseOrderRepository purchaseOrders = mock(PurchaseOrderRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GstConfigurationRepository gstConfigs = mock(GstConfigurationRepository.class);

    private ErpNextStoreSyncService service;
    /** In-memory erp_sync_records. */
    private final Map<String, ErpSyncRecord> store = new HashMap<>();
    private long nextRecordId = 1;

    private Product shirt;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new ErpNextStoreSyncService(erp, setup, records, products, invoices, creditNotes, payments,
                refunds, orders, purchaseOrders, users, gstConfigs);
        ReflectionTestUtils.setField(service, "company", "Cauvery Store");
        ReflectionTestUtils.setField(service, "abbr", "CS");
        ReflectionTestUtils.setField(service, "businessCustomerGroup", "Commercial");
        ReflectionTestUtils.setField(service, "individualCustomerGroup", "Individual");
        ReflectionTestUtils.setField(service, "supplierGroup", "All Supplier Groups");
        ReflectionTestUtils.setField(service, "territory", "India");
        ReflectionTestUtils.setField(service, "warehouseSetting", "");
        ReflectionTestUtils.setField(service, "bankAccount", "HDFC - CS");
        ReflectionTestUtils.setField(service, "razorpayAccount", "Razorpay Clearing - CS");
        ReflectionTestUtils.setField(service, "codAccount", "");
        ReflectionTestUtils.setField(service, "submitInvoices", true);
        ReflectionTestUtils.setField(service, "requireMatchingTotals", true);
        ReflectionTestUtils.setField(service, "itemTaxTemplate", "GST {rate}% - {abbr}");
        ReflectionTestUtils.setField(service, "nilTaxTemplate", "Nil-Rated - {abbr}");
        ReflectionTestUtils.setField(service, "stockAlignEnabled", true);

        when(erp.isConfigured()).thenReturn(true);
        when(erp.list(anyString(), anyMap(), anyList(), anyInt())).thenReturn(JSON.createArrayNode());

        when(records.findByEntityTypeAndEntityId(anyString(), anyLong())).thenAnswer(a ->
                Optional.ofNullable(store.get(a.getArgument(0) + ":" + a.getArgument(1))));
        when(records.save(any())).thenAnswer(a -> {
            ErpSyncRecord r = a.getArgument(0);
            if (r.getId() == null) ReflectionTestUtils.setField(r, "id", nextRecordId++);
            store.put(r.getEntityType() + ":" + r.getEntityId(), r);
            return r;
        });

        User admin = new User();
        admin.setId(ADMIN);
        admin.setRole(Role.ADMIN);
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of(admin));
        when(users.findByRole(Role.SUPER_ADMIN)).thenReturn(List.of());

        shirt = new Product();
        shirt.setId(7L);
        shirt.setName("Cotton Shirt");
        shirt.setHsnCode("6205");
        shirt.setGstPercentage(5.0);
        shirt.setPrice(525.0);
        shirt.setStock(10);
        shirt.setActive(true);
        shirt.setSellerId(ADMIN);

        order = new Order();
        order.setId(100L);
        OrderItem oi = new OrderItem();
        oi.setProduct(shirt);
        oi.setQuantity(2);
        oi.setPrice(525.0);
        order.setItems(List.of(oi));
        when(orders.findById(100L)).thenReturn(Optional.of(order));

        when(products.findAll()).thenReturn(List.of());
        when(invoices.findAll()).thenReturn(List.of());
        when(creditNotes.findAll()).thenReturn(List.of());
        when(refunds.findAll()).thenReturn(List.of());
        when(purchaseOrders.findAll()).thenReturn(List.of());
    }

    // ------------------------------------------------------------ helpers

    private static ObjectNode doc(String name, int docstatus, double grandTotal) {
        ObjectNode n = JSON.createObjectNode();
        n.put("name", name);
        n.put("docstatus", docstatus);
        n.put("grand_total", grandTotal);
        return n;
    }

    private void itemAlreadySynced(Product p) {
        ErpSyncRecord r = new ErpSyncRecord();
        r.setEntityType(ErpSyncRecord.ITEM);
        r.setEntityId(p.getId());
        r.setStatus(ErpSyncRecord.SYNCED);
        r.setErpDocName(ErpNextStoreSyncService.itemCode(p.getId()));
        store.put(ErpSyncRecord.ITEM + ":" + p.getId(), r);
    }

    private GstInvoice invoice(long sellerId, boolean inter) {
        GstInvoice inv = new GstInvoice();
        inv.setId(500L);
        inv.setInvoiceNumber("CS/26-27/0001");
        inv.setOrderId(100L);
        inv.setSellerId(sellerId);
        inv.setCustomerId(9L);
        inv.setBuyerName("Priya");
        inv.setBuyerStateCode(inter ? "29" : "33");
        inv.setPlaceOfSupply(inter ? "29-Karnataka" : "33-Tamil Nadu");
        inv.setIsInterState(inter);
        inv.setInvoiceDate(LocalDate.of(2026, 11, 14));
        inv.setStatus("PENDING_DISPATCH");

        GstInvoiceItem shirtLine = new GstInvoiceItem();
        shirtLine.setProductName("Cotton Shirt");
        shirtLine.setHsnCode("6205");
        shirtLine.setQuantity(2);
        shirtLine.setTaxableValue(1000.0);
        GstInvoiceItem delivery = new GstInvoiceItem();
        delivery.setProductName("Delivery charge");
        delivery.setHsnCode("6205");
        delivery.setQuantity(1);
        delivery.setTaxableValue(40.0);
        if (inter) {
            shirtLine.setIgstRate(5.0); delivery.setIgstRate(5.0);
        } else {
            shirtLine.setCgstRate(2.5); shirtLine.setSgstRate(2.5);
            delivery.setCgstRate(2.5); delivery.setSgstRate(2.5);
        }
        inv.setItems(List.of(shirtLine, delivery));
        inv.setTotalAmount(1092.0);
        return inv;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> created(String doctype) {
        ArgumentCaptor<Map<String, Object>> c = ArgumentCaptor.forClass(Map.class);
        verify(erp, atLeastOnce()).create(eq(doctype), c.capture());
        return c.getValue();
    }

    // ------------------------------------------------------------ products

    @Test
    void storeProductBecomesAStockItem() {
        when(products.findAll()).thenReturn(List.of(shirt));
        when(erp.exists("Item", "CS-P7")).thenReturn(false);

        service.syncAll(false);

        Map<String, Object> item = created("Item");
        assertEquals("CS-P7", item.get("item_code"));
        assertEquals("6205", item.get("gst_hsn_code"));
        assertEquals(1, item.get("is_stock_item"));
        assertEquals(ErpNextSetupService.PRODUCT_GROUP, item.get("item_group"));
        assertEquals("P7", item.get(ErpNextSetupService.REF_FIELD));
        assertEquals(ErpSyncRecord.SYNCED, store.get("ITEM:7").getStatus());
        verify(setup).ensureHsn(eq("6205"), any());
    }

    @Test
    void sellersProductsStayOut() {
        shirt.setSellerId(SELLER);
        when(products.findAll()).thenReturn(List.of(shirt));

        service.syncAll(false);

        verify(erp, never()).create(eq("Item"), anyMap());
        assertTrue(store.isEmpty());
    }

    @Test
    void productWithoutHsnFailsWithAPlainReason() {
        shirt.setHsnCode(null);
        when(products.findAll()).thenReturn(List.of(shirt));

        service.syncAll(false);

        ErpSyncRecord r = store.get("ITEM:7");
        assertEquals(ErpSyncRecord.FAILED, r.getStatus());
        assertTrue(r.getErrorMessage().contains("no HSN code"));
    }

    @Test
    void changedProductIsSentAgainUnchangedIsNot() {
        when(products.findAll()).thenReturn(List.of(shirt));
        when(erp.exists("Item", "CS-P7")).thenReturn(false, true);

        service.syncAll(false);
        service.syncAll(false);                        // nothing changed
        verify(erp, never()).update(eq("Item"), anyString(), anyMap());

        shirt.setName("Cotton Shirt - Blue");
        service.syncAll(false);
        verify(erp).update(eq("Item"), eq("CS-P7"), argThat(m -> "Cotton Shirt - Blue".equals(m.get("item_name"))));
    }

    // ------------------------------------------------------------ customer invoices

    @Test
    void storeInvoiceBecomesASalesInvoiceThatTakesStockOut() {
        itemAlreadySynced(shirt);
        GstInvoice inv = invoice(ADMIN, false);
        when(invoices.findAll()).thenReturn(List.of(inv));
        when(erp.create(eq("Customer"), anyMap())).thenReturn(doc("Priya [C9]", 0, 0));
        when(erp.create(eq("Address"), anyMap())).thenReturn(doc("Priya-Billing", 0, 0));
        when(erp.create(eq("Sales Invoice"), anyMap())).thenReturn(doc("SINV-0001", 0, 1092.0));

        service.syncAll(false);

        Map<String, Object> si = created("Sales Invoice");
        assertEquals("Priya [C9]", si.get("customer"));
        assertEquals("INV-CS/26-27/0001", si.get(ErpNextSetupService.REF_FIELD));
        assertEquals("Output GST In-state - CS", si.get("taxes_and_charges"));
        assertEquals("33-Tamil Nadu", si.get("place_of_supply"));
        assertEquals(1, si.get("update_stock"));
        assertEquals("Stores - CS", si.get("set_warehouse"));

        List<Map<String, Object>> items = (List<Map<String, Object>>) si.get("items");
        assertEquals("CS-P7", items.get(0).get("item_code"));
        assertEquals(2, items.get(0).get("qty"));
        assertEquals(500.0, items.get(0).get("rate"));
        assertEquals("GST 5% - CS", items.get(0).get("item_tax_template"));
        assertEquals("Stores - CS", items.get(0).get("warehouse"));
        assertEquals(ErpNextSetupService.CHARGES_ITEM, items.get(1).get("item_code"));
        assertFalse(items.get(1).containsKey("warehouse"), "a delivery charge is not stock");

        verify(erp).submit("Sales Invoice", "SINV-0001");
        assertEquals("SINV-0001", store.get("SALES_INVOICE:500").getErpDocName());
    }

    @Test
    void interStateInvoiceUsesTheOutOfStateTemplate() {
        itemAlreadySynced(shirt);
        when(invoices.findAll()).thenReturn(List.of(invoice(ADMIN, true)));
        when(erp.create(eq("Customer"), anyMap())).thenReturn(doc("Priya [C9]", 0, 0));
        when(erp.create(eq("Address"), anyMap())).thenReturn(doc("A1", 0, 0));
        when(erp.create(eq("Sales Invoice"), anyMap())).thenReturn(doc("SINV-0002", 0, 1092.0));

        service.syncAll(false);

        assertEquals("Output GST Out-state - CS", created("Sales Invoice").get("taxes_and_charges"));
    }

    @Test
    void sellersInvoicesStayOut() {
        when(invoices.findAll()).thenReturn(List.of(invoice(SELLER, false)));

        service.syncAll(false);

        verify(erp, never()).create(eq("Sales Invoice"), anyMap());
    }

    @Test
    void mismatchedTotalsAreLeftAsADraftAndReported() {
        itemAlreadySynced(shirt);
        when(invoices.findAll()).thenReturn(List.of(invoice(ADMIN, false)));
        when(erp.create(eq("Customer"), anyMap())).thenReturn(doc("Priya [C9]", 0, 0));
        when(erp.create(eq("Address"), anyMap())).thenReturn(doc("A1", 0, 0));
        when(erp.create(eq("Sales Invoice"), anyMap())).thenReturn(doc("SINV-0003", 0, 1250.0));

        service.syncAll(false);

        verify(erp, never()).submit(eq("Sales Invoice"), anyString());
        ErpSyncRecord r = store.get("SALES_INVOICE:500");
        assertEquals(ErpSyncRecord.FAILED, r.getStatus());
        assertTrue(r.getErrorMessage().contains("Total mismatch"));
    }

    @Test
    void anInvoiceAlreadyInTheErpIsNotMadeTwice() {
        itemAlreadySynced(shirt);
        when(invoices.findAll()).thenReturn(List.of(invoice(ADMIN, false)));
        when(erp.findNameByField("Sales Invoice", ErpNextSetupService.REF_FIELD, "INV-CS/26-27/0001"))
                .thenReturn("SINV-0009");
        when(erp.get("Sales Invoice", "SINV-0009")).thenReturn(doc("SINV-0009", 1, 1092.0));

        service.syncAll(false);

        verify(erp, never()).create(eq("Sales Invoice"), anyMap());
        assertEquals("SINV-0009", store.get("SALES_INVOICE:500").getErpDocName());
    }

    @Test
    void anInvoiceWaitsForItsProduct() {
        when(invoices.findAll()).thenReturn(List.of(invoice(ADMIN, false)));
        when(erp.create(eq("Customer"), anyMap())).thenReturn(doc("Priya [C9]", 0, 0));
        when(erp.create(eq("Address"), anyMap())).thenReturn(doc("A1", 0, 0));

        service.syncAll(false);

        ErpSyncRecord r = store.get("SALES_INVOICE:500");
        assertEquals(ErpSyncRecord.WAITING, r.getStatus());
        assertEquals(0, r.getAttempts(), "waiting is not a failed attempt");
    }

    @Test
    void anInvoiceCancelledOnTheWebsiteIsCancelledInTheErp() {
        GstInvoice inv = invoice(ADMIN, false);
        inv.setStatus("CANCELLED");
        when(invoices.findAll()).thenReturn(List.of(inv));
        ErpSyncRecord r = new ErpSyncRecord();
        r.setEntityType(ErpSyncRecord.SALES_INVOICE);
        r.setEntityId(500L);
        r.setStatus(ErpSyncRecord.SYNCED);
        r.setErpDocName("SINV-0001");
        store.put("SALES_INVOICE:500", r);
        when(erp.get("Sales Invoice", "SINV-0001")).thenReturn(doc("SINV-0001", 1, 1092.0));

        service.syncAll(false);

        verify(erp).cancel("Sales Invoice", "SINV-0001");
        assertEquals(ErpSyncRecord.CANCELLED, r.getStatus());
    }

    // ------------------------------------------------------------ payments

    private void invoiceSynced(int docstatus) {
        ErpSyncRecord r = new ErpSyncRecord();
        r.setEntityType(ErpSyncRecord.SALES_INVOICE);
        r.setEntityId(500L);
        r.setStatus(ErpSyncRecord.SYNCED);
        r.setErpDocName("SINV-0001");
        store.put("SALES_INVOICE:500", r);
        ObjectNode d = doc("SINV-0001", docstatus, 1092.0);
        d.put("customer", "Priya [C9]");
        d.put("outstanding_amount", 1092.0);
        when(erp.get("Sales Invoice", "SINV-0001")).thenReturn(d);
    }

    @Test
    void aPaidOrderIsRecordedAgainstItsInvoice() {
        invoiceSynced(1);
        order.setPaid(true);
        order.setPaymentMethod("RAZORPAY");
        when(invoices.findAll()).thenReturn(List.of(invoice(ADMIN, false)));
        Payment p = new Payment();
        p.setRazorpayPaymentId("pay_ABC");
        when(payments.findByOrderId(100L)).thenReturn(Optional.of(p));
        when(erp.create(eq("Payment Entry"), anyMap())).thenReturn(doc("ACC-PAY-0001", 0, 0));

        service.syncAll(false);

        Map<String, Object> pe = created("Payment Entry");
        assertEquals("Receive", pe.get("payment_type"));
        assertEquals("Razorpay Clearing - CS", pe.get("paid_to"));
        assertEquals(1092.0, pe.get("paid_amount"));
        assertEquals("pay_ABC", pe.get("reference_no"));
        List<Map<String, Object>> refs = (List<Map<String, Object>>) pe.get("references");
        assertEquals("SINV-0001", refs.get(0).get("reference_name"));
        verify(erp).submit("Payment Entry", "ACC-PAY-0001");
    }

    @Test
    void cashOnDeliveryFallsBackToTheBankAccount() {
        invoiceSynced(1);
        order.setPaid(true);
        order.setPaymentMethod("COD");
        when(invoices.findAll()).thenReturn(List.of(invoice(ADMIN, false)));
        when(erp.create(eq("Payment Entry"), anyMap())).thenReturn(doc("ACC-PAY-0002", 0, 0));

        service.syncAll(false);

        Map<String, Object> pe = created("Payment Entry");
        assertEquals("HDFC - CS", pe.get("paid_to"));
        assertEquals("COD-ORDER-100", pe.get("reference_no"));
    }

    @Test
    void aPaymentWaitsWhileItsInvoiceIsADraft() {
        invoiceSynced(0);
        order.setPaid(true);
        when(invoices.findAll()).thenReturn(List.of(invoice(ADMIN, false)));

        service.syncAll(false);

        verify(erp, never()).create(eq("Payment Entry"), anyMap());
        assertEquals(ErpSyncRecord.WAITING, store.get("CUSTOMER_PAYMENT:100").getStatus());
    }

    @Test
    void anUnpaidOrderSendsNoPayment() {
        invoiceSynced(1);
        when(invoices.findAll()).thenReturn(List.of(invoice(ADMIN, false)));

        service.syncAll(false);

        verify(erp, never()).create(eq("Payment Entry"), anyMap());
    }

    @Test
    void aCompletedRefundIsPaidOutToTheCustomer() {
        invoiceSynced(1);
        Refund r = new Refund();
        r.setId(3L);
        r.setOrderId(100L);
        r.setAmount(1092.0);
        r.setStatus("COMPLETED");
        r.setGatewayRefundId("rfnd_1");
        when(refunds.findAll()).thenReturn(List.of(r));
        when(invoices.findByOrderId(100L)).thenReturn(Optional.of(invoice(ADMIN, false)));
        when(erp.create(eq("Payment Entry"), anyMap())).thenReturn(doc("ACC-PAY-0003", 0, 0));

        service.syncAll(false);

        Map<String, Object> pe = created("Payment Entry");
        assertEquals("Pay", pe.get("payment_type"));
        assertEquals("Priya [C9]", pe.get("party"));
        assertEquals("Razorpay Clearing - CS", pe.get("paid_from"));
        assertEquals("rfnd_1", pe.get("reference_no"));
    }

    // ------------------------------------------------------------ credit notes

    private CreditNote creditNote(String referenceType) {
        CreditNote cn = new CreditNote();
        cn.setId(40L);
        cn.setCreditNoteNumber("CN/0001");
        cn.setInvoiceId(500L);
        cn.setOrderId(100L);
        cn.setSellerId(ADMIN);
        cn.setOriginalInvoiceNumber("CS/26-27/0001");
        cn.setNoteType("CREDIT");
        cn.setReferenceType(referenceType);
        cn.setIsInterState(false);
        cn.setCreditNoteDate(LocalDate.of(2026, 11, 20));
        CreditNoteItem line = new CreditNoteItem();
        line.setProductName("Cotton Shirt");
        line.setHsnCode("6205");
        line.setQuantity(1);
        line.setTaxableValue(500.0);
        line.setCgstRate(2.5);
        line.setSgstRate(2.5);
        cn.setItems(List.of(line));
        cn.setTotalAmount(525.0);
        return cn;
    }

    @Test
    void aReturnPutsStockBackAgainstTheOriginalInvoice() {
        itemAlreadySynced(shirt);
        invoiceSynced(1);
        when(creditNotes.findAll()).thenReturn(List.of(creditNote("RETURN")));
        when(erp.create(eq("Sales Invoice"), anyMap())).thenReturn(doc("SINV-RET-1", 0, -525.0));

        service.syncAll(false);

        Map<String, Object> ret = created("Sales Invoice");
        assertEquals(1, ret.get("is_return"));
        assertEquals("SINV-0001", ret.get("return_against"));
        assertEquals(1, ret.get("update_stock"));
        List<Map<String, Object>> items = (List<Map<String, Object>>) ret.get("items");
        assertEquals(-1, items.get(0).get("qty"));
        assertEquals("CN-CN/0001", ret.get(ErpNextSetupService.REF_FIELD));
        verify(erp).submit("Sales Invoice", "SINV-RET-1");
    }

    @Test
    void aPriceAdjustmentMovesNoStock() {
        itemAlreadySynced(shirt);
        invoiceSynced(1);
        when(creditNotes.findAll()).thenReturn(List.of(creditNote("INVOICE")));
        when(erp.create(eq("Sales Invoice"), anyMap())).thenReturn(doc("SINV-RET-2", 0, -525.0));

        service.syncAll(false);

        Map<String, Object> ret = created("Sales Invoice");
        assertFalse(ret.containsKey("update_stock"));
    }

    @Test
    void aCreditNoteWaitsForItsInvoice() {
        itemAlreadySynced(shirt);
        when(creditNotes.findAll()).thenReturn(List.of(creditNote("RETURN")));

        service.syncAll(false);

        assertEquals(ErpSyncRecord.WAITING, store.get("SALES_RETURN:40").getStatus());
    }

    // ------------------------------------------------------------ purchases

    @Test
    void aReceivedPurchaseOrderBecomesADraftPurchaseBill() {
        itemAlreadySynced(shirt);
        com.cauverystore.entities.Supplier sup = new com.cauverystore.entities.Supplier();
        sup.setId(4L);
        sup.setName("DeoDap");
        sup.setGstin("24AAAAA0000A1Z5");    // Gujarat, so out of state for a Tamil Nadu store
        PurchaseOrder po = new PurchaseOrder();
        po.setId(60L);
        po.setPoNumber("PO-0001");
        po.setStatus("RECEIVED");
        po.setSupplier(sup);
        po.setOrderDate(LocalDate.of(2026, 11, 1));
        PurchaseOrderItem line = new PurchaseOrderItem();
        line.setProduct(shirt);
        line.setQuantity(20);
        line.setReceivedQuantity(18);
        line.setUnitPrice(300.0);
        po.setItems(List.of(line));
        when(purchaseOrders.findAll()).thenReturn(List.of(po));
        GstConfiguration storeConfig = new GstConfiguration();
        storeConfig.setStateCode("33");
        when(gstConfigs.findByIsActiveTrue()).thenReturn(List.of(storeConfig));
        when(erp.create(eq("Supplier"), anyMap())).thenReturn(doc("DeoDap", 0, 0));
        when(erp.create(eq("Address"), anyMap())).thenReturn(doc("DeoDap-Billing", 0, 0));
        when(erp.create(eq("Purchase Invoice"), anyMap())).thenReturn(doc("PINV-0001", 0, 0));

        service.syncAll(false);

        Map<String, Object> pi = created("Purchase Invoice");
        assertEquals("DeoDap", pi.get("supplier"));
        assertEquals("Input GST Out-state - CS", pi.get("taxes_and_charges"));
        assertEquals(1, pi.get("update_stock"));
        List<Map<String, Object>> items = (List<Map<String, Object>>) pi.get("items");
        assertEquals(18, items.get(0).get("qty"), "what was received, not what was ordered");
        assertEquals(300.0, items.get(0).get("rate"));
        verify(erp, never()).submit(eq("Purchase Invoice"), anyString());
        assertEquals("PINV-0001", store.get("PURCHASE_BILL:60").getErpDocName());
    }

    @Test
    void anOpenPurchaseOrderIsNotSent() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(61L);
        po.setStatus("CONFIRMED");
        when(purchaseOrders.findAll()).thenReturn(List.of(po));

        service.syncAll(false);

        verify(erp, never()).create(eq("Purchase Invoice"), anyMap());
    }

    // ------------------------------------------------------------ stock

    @Test
    void stockIsNotAlignedWhileDocumentsAreDrafts() {
        ReflectionTestUtils.setField(service, "submitInvoices", false);

        Map<String, Object> r = service.alignStock();

        assertTrue(String.valueOf(r.get("skipped")).contains("drafts"));
        verify(erp, never()).create(eq("Stock Reconciliation"), anyMap());
    }

    @Test
    void stockDifferencesAreReconciledToTheWebsiteCount() {
        itemAlreadySynced(shirt);
        when(products.findAll()).thenReturn(List.of(shirt));
        ObjectNode bin = JSON.createObjectNode();
        bin.put("actual_qty", 4.0);
        when(erp.list(eq("Bin"), anyMap(), anyList(), anyInt())).thenReturn(JSON.createArrayNode().add(bin));
        when(erp.create(eq("Stock Reconciliation"), anyMap())).thenReturn(doc("MAT-RECO-1", 0, 0));

        Map<String, Object> r = service.alignStock();

        assertEquals(1, r.get("itemsChanged"));
        Map<String, Object> reco = created("Stock Reconciliation");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) reco.get("items");
        assertEquals(10, rows.get(0).get("qty"));
        verify(erp).submit("Stock Reconciliation", "MAT-RECO-1");
    }

    @Test
    void matchingStockChangesNothing() {
        itemAlreadySynced(shirt);
        when(products.findAll()).thenReturn(List.of(shirt));
        ObjectNode bin = JSON.createObjectNode();
        bin.put("actual_qty", 10.0);
        when(erp.list(eq("Bin"), anyMap(), anyList(), anyInt())).thenReturn(JSON.createArrayNode().add(bin));

        assertEquals(0, service.alignStock().get("itemsChanged"));
        verify(erp, never()).create(eq("Stock Reconciliation"), anyMap());
    }

    // ------------------------------------------------------------ small rules

    @Test
    void taxTemplateNames() {
        assertEquals("GST 18% - CS", service.taxTemplate(18.0));
        assertEquals("GST 5% - CS", service.taxTemplate(5.0));
        assertEquals("GST 0.25% - CS", service.taxTemplate(0.25));
        assertEquals("Nil-Rated - CS", service.taxTemplate(0.0));
        assertNull(service.taxTemplate(null));
    }

    @Test
    void supplierStateFromGstinThenFromName() {
        com.cauverystore.entities.Supplier s = new com.cauverystore.entities.Supplier();
        s.setGstin("29ABCDE1234F1Z5");
        assertEquals("29", ErpNextStoreSyncService.supplierStateCode(s));
        s.setGstin(null);
        s.setState("Tamil Nadu");
        assertEquals("33", ErpNextStoreSyncService.supplierStateCode(s));
    }

    @Test
    void notConfiguredDoesNothing() {
        when(erp.isConfigured()).thenReturn(false);
        assertEquals("ERPNext is not configured", service.syncAll(false).get("skipped"));
        verify(setup, never()).ensure();
    }
}
