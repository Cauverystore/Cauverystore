package com.cauverystore.billinglink;

import com.cauverystore.entities.*;
import com.cauverystore.repository.GstInvoiceRepository;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.PurchaseOrderRepository;
import com.cauverystore.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Store invoices going to Cauvery Billing. Cauvery Billing is a small local stand-in server. */
class BillingLinkServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long ADMIN = 1L, SELLER = 50L;

    private final BillingLinkRecordRepository records = mock(BillingLinkRecordRepository.class);
    private final GstInvoiceRepository invoices = mock(GstInvoiceRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final PurchaseOrderRepository purchaseOrders = mock(PurchaseOrderRepository.class);
    private final BillingLinkPurchaseRepository sentPurchases = mock(BillingLinkPurchaseRepository.class);
    private final Map<Long, BillingLinkRecord> saved = new HashMap<>();
    private final Map<Long, BillingLinkPurchase> savedPurchases = new HashMap<>();
    private final List<String> productsReceived = new CopyOnWriteArrayList<>();
    private final List<String> purchasesReceived = new CopyOnWriteArrayList<>();

    private HttpServer server;
    /** Bodies and keys the stand-in server received. */
    private final List<String> received = new CopyOnWriteArrayList<>();
    private final List<String> keysSeen = new CopyOnWriteArrayList<>();
    private volatile int reply = 201;

    private BillingLinkService service;
    private Order order;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/connect/orders", ex -> {
            received.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keysSeen.add(ex.getRequestHeaders().getFirst("X-Api-Key"));
            byte[] out = (reply < 300 ? "{\"created\":true,\"billId\":77,\"billNumber\":\"X\"}"
                    : "{\"error\":\"The website order could not be saved as a bill.\",\"problems\":[\"The lines add up to wrong.\"]}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(reply, out.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(out); }
        });
        server.createContext("/api/connect/products", ex -> {
            productsReceived.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = "{\"created\":2,\"updated\":0,\"stockChanged\":2,\"switchedOff\":0}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(out); }
        });
        server.createContext("/api/connect/purchases", ex -> {
            purchasesReceived.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = "{\"created\":true,\"purchaseId\":9}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(201, out.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(out); }
        });
        server.start();

        service = new BillingLinkService(records, invoices, orders, users, products, purchaseOrders, sentPurchases,
                mock(PlatformTransactionManager.class), JSON);
        when(sentPurchases.findByPurchaseOrderId(anyLong()))
                .thenAnswer(a -> Optional.ofNullable(savedPurchases.get((Long) a.getArgument(0))));
        when(sentPurchases.save(any())).thenAnswer(a -> {
            BillingLinkPurchase r = a.getArgument(0);
            savedPurchases.put(r.getPurchaseOrderId(), r);
            return r;
        });
        ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/");
        ReflectionTestUtils.setField(service, "key", "cbk_test");
        ReflectionTestUtils.setField(service, "ownSellerIdsSetting", "46");
        ReflectionTestUtils.setField(service, "firstOrderId", 10L);

        when(records.findByInvoiceId(anyLong())).thenAnswer(a -> Optional.ofNullable(saved.get((Long) a.getArgument(0))));
        when(records.save(any())).thenAnswer(a -> {
            BillingLinkRecord r = a.getArgument(0);
            saved.put(r.getInvoiceId(), r);
            return r;
        });
        User admin = new User();
        admin.setId(ADMIN);
        admin.setRole(Role.ADMIN);
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of(admin));
        when(users.findByRole(Role.SUPER_ADMIN)).thenReturn(List.of());

        Product mug = new Product();
        mug.setId(7L);
        mug.setName("Steel Mug");
        mug.setSku("MUG-01");
        mug.setBarcode("8901234567890");
        order = new Order();
        order.setId(100L);
        order.setPaid(true);
        OrderItem oi = new OrderItem();
        oi.setProduct(mug);
        oi.setQuantity(2);
        order.setItems(List.of(oi));
        when(orders.findById(100L)).thenReturn(Optional.of(order));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    /** Two mugs and delivery, within Tamil Nadu: ₹472 + ₹59 = ₹531. */
    private GstInvoice invoice(long id, Long sellerId) {
        GstInvoice inv = new GstInvoice();
        inv.setId(id);
        inv.setInvoiceNumber("CS/26-27/000" + id);
        inv.setOrderId(100L);
        inv.setSellerId(sellerId);
        inv.setStatus("ISSUED");
        inv.setInvoiceDate(LocalDate.of(2026, 11, 20));
        inv.setBuyerName("Asha");
        inv.setPlaceOfSupply("33-Tamil Nadu");
        inv.setIsInterState(false);
        inv.setTaxableAmount(450.0);
        inv.setCgstAmount(40.5);
        inv.setSgstAmount(40.5);
        inv.setIgstAmount(0.0);
        inv.setTotalTax(81.0);
        inv.setTotalAmount(531.0);
        inv.getItems().add(line(inv, "Steel Mug", "7323", null, 2, 236.0, 400.0, 36.0, 472.0));
        inv.getItems().add(line(inv, "Delivery charge", null, "996812", 1, 59.0, 50.0, 4.5, 59.0));
        when(invoices.findById(id)).thenReturn(Optional.of(inv));
        return inv;
    }

    private static GstInvoiceItem line(GstInvoice inv, String name, String hsn, String sac, int qty, double price,
                                       double taxable, double halfTax, double total) {
        GstInvoiceItem l = new GstInvoiceItem();
        l.setInvoice(inv);
        l.setProductName(name);
        l.setHsnCode(hsn);
        l.setSacCode(sac);
        l.setQuantity(qty);
        l.setUnitPrice(price);
        l.setTaxableValue(taxable);
        l.setCgstRate(9.0);
        l.setSgstRate(9.0);
        l.setIgstRate(0.0);
        l.setCgstAmount(halfTax);
        l.setSgstAmount(halfTax);
        l.setIgstAmount(0.0);
        l.setTotalAmount(total);
        return l;
    }

    @Test
    void paidStoreInvoiceIsSentOnceWithSkuAndTax() throws Exception {
        GstInvoice inv = invoice(1L, ADMIN);
        when(invoices.findAll()).thenReturn(List.of(inv));

        Map<String, Object> r = service.sendAll(false);
        assertEquals(1, r.get("sent"));
        assertEquals(1, received.size());
        assertEquals("cbk_test", keysSeen.get(0));

        JsonNode body = JSON.readTree(received.get(0));
        assertEquals("CS/26-27/0001", body.get("invoiceNumber").asText());
        assertEquals("100", body.get("orderRef").asText());
        assertEquals("2026-11-20", body.get("invoiceDate").asText());
        assertEquals("33", body.get("placeOfSupplyStateCode").asText());
        assertEquals(531.0, body.get("grandTotal").asDouble());
        JsonNode mug = body.get("lines").get(0);
        assertEquals("MUG-01", mug.get("sku").asText());
        assertEquals("P7", mug.get("productRef").asText());
        assertEquals("8901234567890", mug.get("barcode").asText());
        assertEquals(18.0, mug.get("gstRate").asDouble());
        assertEquals(2, mug.get("quantity").asInt());
        JsonNode delivery = body.get("lines").get(1);
        assertTrue(delivery.get("sku").isNull());
        assertEquals("996812", delivery.get("hsnCode").asText());

        assertEquals(BillingLinkRecord.SENT, saved.get(1L).getStatus());
        assertEquals(77L, saved.get(1L).getBillId());

        // Next run: already sent, nothing goes out again.
        service.sendAll(false);
        assertEquals(1, received.size());
    }

    @Test
    void sellersUnpaidCancelledAndTestOrdersStayOut() {
        GstInvoice seller = invoice(2L, SELLER);
        GstInvoice cancelled = invoice(3L, ADMIN);
        cancelled.setStatus("CANCELLED");
        GstInvoice draft = invoice(4L, 46L);
        draft.setStatus("DRAFT");
        GstInvoice test = invoice(5L, ADMIN);
        test.setOrderId(9L);
        when(invoices.findAll()).thenReturn(List.of(seller, cancelled, draft, test));
        service.sendAll(false);
        assertTrue(received.isEmpty());

        order.setPaid(false);
        GstInvoice unpaid = invoice(6L, 46L);
        when(invoices.findAll()).thenReturn(List.of(unpaid));
        service.sendAll(false);
        assertTrue(received.isEmpty());

        order.setPaid(true);
        service.sendAll(false);
        assertEquals(1, received.size());   // the store's own seller account (46) counts as the store
    }

    @Test
    void refusalIsRecordedWithTheReasonAndRetriedUpToTheLimit() {
        reply = 400;
        GstInvoice inv = invoice(1L, ADMIN);
        when(invoices.findAll()).thenReturn(List.of(inv));

        for (int i = 0; i < BillingLinkService.MAX_AUTO_ATTEMPTS + 2; i++) service.sendAll(false);
        assertEquals(BillingLinkService.MAX_AUTO_ATTEMPTS, received.size());
        BillingLinkRecord rec = saved.get(1L);
        assertEquals(BillingLinkRecord.FAILED, rec.getStatus());
        assertTrue(rec.getErrorMessage().contains("400"), rec.getErrorMessage());
        assertTrue(rec.getErrorMessage().contains("add up to"), rec.getErrorMessage());

        // "Send now" with force tries again; this time Cauvery Billing accepts it.
        reply = 201;
        service.sendAll(true);
        assertEquals(BillingLinkRecord.SENT, saved.get(1L).getStatus());
    }

    @Test
    void unreachableBillingIsAFailureNotACrash() {
        ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:1");
        GstInvoice inv = invoice(1L, ADMIN);
        when(invoices.findAll()).thenReturn(List.of(inv));
        Map<String, Object> r = service.sendAll(false);
        assertEquals(1, r.get("failed"));
        assertTrue(saved.get(1L).getErrorMessage().startsWith("Could not reach Cauvery Billing"));
    }

    @Test
    void nothingIsSentUntilConfigured() {
        ReflectionTestUtils.setField(service, "key", "");
        GstInvoice inv = invoice(1L, ADMIN);
        when(invoices.findAll()).thenReturn(List.of(inv));
        assertTrue(service.sendAll(false).containsKey("skipped"));
        assertTrue(received.isEmpty());
    }

    private Product product(long id, String name, String sku, Long seller, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setSku(sku);
        p.setSellerId(seller);
        p.setStock(stock);
        p.setPrice(299.0);
        p.setHsnCode("7323");
        p.setUom("NOS");
        return p;
    }

    @Test
    void storeProductsAndEachSizeAreSentWithStock() throws Exception {
        Product mug = product(7L, "Steel Mug", "MUG-01", 46L, 5);
        mug.setOfferPrice(249.0);
        Product shirt = product(8L, "Cotton T-shirt", "TS", ADMIN, 0);
        ProductVariant m = new ProductVariant();
        m.setId(31L);
        m.setVariantValue("M");
        m.setSku("TS-M");
        m.setStock(4);
        ProductVariant l = new ProductVariant();
        l.setId(32L);
        l.setVariantValue("L");
        l.setStock(-1);
        l.setPrice(329.0);
        shirt.setVariants(new ArrayList<>(List.of(m, l)));
        Product sellers = product(9L, "Seller's lamp", "LAMP", SELLER, 3);
        when(products.findAll()).thenReturn(List.of(mug, shirt, sellers));

        Map<String, Object> r = service.sendAll(false);
        assertEquals(1, productsReceived.size(), String.valueOf(r));
        JsonNode list = JSON.readTree(productsReceived.get(0)).get("products");
        assertEquals(3, list.size());                         // mug + two sizes; the seller's lamp stays out
        assertEquals("P7", list.get(0).get("ref").asText());
        assertEquals(249.0, list.get(0).get("price").asDouble(), 1e-9);   // the price the customer pays
        assertEquals(5, list.get(0).get("stock").asInt());
        assertEquals("PCS", list.get(0).get("unit").asText());
        assertEquals("V31", list.get(1).get("ref").asText());
        assertEquals("Cotton T-shirt - M", list.get(1).get("name").asText());
        assertEquals("TS-M", list.get(1).get("sku").asText());
        assertEquals(4, list.get(1).get("stock").asInt());
        assertTrue(list.get(2).get("sku").isNull());
        assertEquals(329.0, list.get(2).get("price").asDouble(), 1e-9);
        assertEquals(0, list.get(2).get("stock").asInt());   // never below zero
    }

    @Test
    void receivedPurchaseOrderIsSentOnce() throws Exception {
        Product mug = product(7L, "Steel Mug", "MUG-01", 46L, 25);
        mug.setGstPercentage(18.0);
        Supplier s = new Supplier();
        s.setName("Steel Works");
        s.setGstin("33AABCS1429B1ZB");
        s.setState("Tamil Nadu");
        PurchaseOrder po = new PurchaseOrder();
        po.setId(3L);
        po.setPoNumber("PO-2026-0003");
        po.setStatus("RECEIVED");
        po.setSupplier(s);
        po.setOrderDate(LocalDate.of(2026, 11, 20));
        po.setActualDelivery(LocalDate.of(2026, 11, 24));
        PurchaseOrderItem i = new PurchaseOrderItem();
        i.setProduct(mug);
        i.setQuantity(24);
        i.setReceivedQuantity(20);
        i.setUnitPrice(120.0);
        po.setItems(List.of(i));
        when(purchaseOrders.findByStatus("RECEIVED")).thenReturn(List.of(po));
        when(purchaseOrders.findById(3L)).thenReturn(Optional.of(po));

        service.sendAll(false);
        service.sendAll(false);
        assertEquals(1, purchasesReceived.size());
        JsonNode body = JSON.readTree(purchasesReceived.get(0));
        assertEquals("PO-2026-0003", body.get("ref").asText());
        assertEquals("2026-11-24", body.get("billDate").asText());
        assertEquals("33AABCS1429B1ZB", body.get("supplierGstin").asText());
        JsonNode line = body.get("lines").get(0);
        assertEquals("P7", line.get("productRef").asText());
        assertEquals(20, line.get("quantity").asInt());      // what actually arrived
        assertEquals(120.0, line.get("unitCost").asDouble(), 1e-9);
        assertEquals(18.0, line.get("gstRate").asDouble(), 1e-9);
        assertEquals(9L, savedPurchases.get(3L).getPurchaseId());
    }

    @Test
    void placeOfSupplyComesFromDeliveryStateFirst() {
        GstInvoice inv = new GstInvoice();
        inv.setPlaceOfSupply("33-Tamil Nadu");
        assertEquals("33", BillingLinkService.placeOfSupply(inv));
        inv.setBuyerStateCode("29");
        assertEquals("29", BillingLinkService.placeOfSupply(inv));
        inv.setDeliveryStateCode("32");
        assertEquals("32", BillingLinkService.placeOfSupply(inv));
    }
}
