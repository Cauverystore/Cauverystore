package com.cauverystore.controller;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.ProductVariant;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.ProductVariantRepository;
import com.cauverystore.repository.PurchaseOrderItemRepository;
import com.cauverystore.service.InventoryService;
import com.cauverystore.service.PurchaseOrderService;
import com.cauverystore.service.PurchaseOrderService.ItemRequest;
import com.cauverystore.service.PurchaseOrderService.OrderRequest;
import com.cauverystore.service.StockMovementService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The real queries, locks and transactions against a real database. */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class StockHistoryIntegrationTest {

    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired PurchaseOrderItemRepository poItems;
    @Autowired InventoryService inventory;
    @Autowired PurchaseOrderService purchaseOrders;
    @Autowired StockMovementService history;

    private Product product(String name, int stock) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(100.0);
        p.setStock(stock);
        p.setActive(true);
        return products.save(p);
    }

    private OrderRequest order(String status, ItemRequest... lines) {
        return new OrderRequest("PO-T", null, null, status, null, null, null, "test", List.of(lines));
    }

    @Test
    void receivingAddsStockOnceAndIsInTheHistory() {
        Product p = product("History bottle", 5);
        Long id = (Long) purchaseOrders.create(order("PENDING", new ItemRequest(p.getId(), null, 7, 20.0))).get("id");
        assertEquals(5, products.findStockById(p.getId()));

        Map<String, Object> received = purchaseOrders.changeStatus(id, "RECEIVED");

        assertEquals("RECEIVED", received.get("status"));
        assertEquals(12, products.findStockById(p.getId()));
        assertThrows(RuntimeException.class, () -> purchaseOrders.changeStatus(id, "RECEIVED"));
        assertThrows(RuntimeException.class, () -> purchaseOrders.changeStatus(id, "CANCELLED"));
        assertEquals(12, products.findStockById(p.getId()));

        List<Map<String, Object>> rows = history.getByProduct(p.getId());
        assertEquals(1, rows.size());
        assertEquals("IN", rows.get(0).get("type"));
        assertEquals(7, rows.get(0).get("quantity"));
        assertEquals(12, rows.get(0).get("balanceAfter"));
        assertEquals("PURCHASE_ORDER", rows.get(0).get("referenceType"));
        assertEquals(id, rows.get(0).get("referenceId"));
        assertEquals("History bottle", rows.get(0).get("productName"));
        assertTrue(purchaseOrders.getAll().stream().anyMatch(o -> id.equals(o.get("id"))));
    }

    @Test
    void editingAnOrderReplacesItsLines() {
        Product a = product("History edit A", 1);
        Product b = product("History edit B", 1);
        Long id = (Long) purchaseOrders.create(order("PENDING", new ItemRequest(a.getId(), null, 2, 10.0))).get("id");

        Map<String, Object> edited = purchaseOrders.update(id, order("CONFIRMED", new ItemRequest(b.getId(), null, 3, 10.0)));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) edited.get("items");
        assertEquals(1, lines.size());
        assertEquals(b.getId(), lines.get(0).get("productId"));
        assertEquals(1, poItems.findByPurchaseOrderId(id).size());
    }

    @Test
    void aSaleAndItsCancellationAreRecordedAgainstTheOrder() {
        Product p = product("History sale", 4);

        Long row = inventory.deductStock(p, null, 3);
        history.linkToOrder(List.of(row), 9001L);
        inventory.restoreForCancelledOrder(p, null, 3, 9001L);

        assertEquals(4, products.findStockById(p.getId()));
        List<Map<String, Object>> rows = history.getByProduct(p.getId());
        assertEquals(2, rows.size());
        Map<String, Object> sale = rows.stream().filter(r -> "OUT".equals(r.get("type"))).findFirst().orElseThrow();
        assertEquals(1, sale.get("balanceAfter"));
        assertEquals(9001L, sale.get("referenceId"));
        assertFalse(history.getByType("OUT").isEmpty());
        assertFalse(history.recent().isEmpty());
    }

    @Test
    void aSizeGetsItsStockAndTheProductTotalRises() {
        Product p = product("History shirt", 2);
        ProductVariant size = new ProductVariant();
        size.setProduct(p);
        size.setVariantType("Size");
        size.setVariantValue("M");
        size.setStock(2);
        size = variants.save(size);
        Long id = (Long) purchaseOrders.create(order("PENDING", new ItemRequest(p.getId(), size.getId(), 5, 10.0))).get("id");

        purchaseOrders.changeStatus(id, "RECEIVED");

        assertEquals(7, variants.findStockById(size.getId()));
        assertEquals(7, products.findStockById(p.getId()));
        assertEquals("M", history.getByProduct(p.getId()).get(0).get("variantLabel"));
    }

    @Test
    void aSizedProductWithoutASizeIsRefused() {
        Product p = product("History sized", 2);
        ProductVariant size = new ProductVariant();
        size.setProduct(p);
        size.setVariantType("Size");
        size.setVariantValue("L");
        size.setStock(2);
        variants.save(size);

        assertThrows(RuntimeException.class,
                () -> purchaseOrders.create(order("PENDING", new ItemRequest(p.getId(), null, 5, 10.0))));
        assertEquals(2, products.findStockById(p.getId()));
    }
}
