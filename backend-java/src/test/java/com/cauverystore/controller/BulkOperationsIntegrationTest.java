package com.cauverystore.controller;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.ProductVariant;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.ProductVariantRepository;
import com.cauverystore.service.AuthorizationService;
import com.cauverystore.service.BulkOperationService;
import com.cauverystore.service.HsnClassificationService;
import com.cauverystore.service.ProductComplianceService;
import com.cauverystore.service.PurchaseOrderService;
import com.cauverystore.service.PurchaseOrderService.ItemRequest;
import com.cauverystore.service.PurchaseOrderService.OrderRequest;
import com.cauverystore.service.StockMovementService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

/** The bulk page against a real database, with its safety rules. */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class BulkOperationsIntegrationTest {

    @Autowired BulkOperationService bulk;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired PurchaseOrderService purchaseOrders;
    @Autowired StockMovementService history;
    @MockBean HsnClassificationService hsn;
    @MockBean AuthorizationService authorization;
    @SpyBean ProductComplianceService compliance;

    private Product product(String name, int stock, double price, Double mrp, boolean active) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(price);
        p.setMrp(mrp);
        p.setStock(stock);
        p.setActive(active);
        return products.save(p);
    }

    private ProductVariant size(Product p) {
        ProductVariant v = new ProductVariant();
        v.setProduct(p);
        v.setVariantType("Size");
        v.setVariantValue("M");
        v.setStock(1);
        return variants.save(v);
    }

    @Test
    void textIdsFromThePageAreAccepted() {
        assertEquals(List.of(12L, 45L), BulkOperationController.ids(Map.of("ids", List.of("12", " 45 "))));
        assertEquals(List.of(7L), BulkOperationController.ids(Map.of("ids", List.of(7))));
        assertThrows(IllegalArgumentException.class, () -> BulkOperationController.ids(Map.of("ids", List.of("abc"))));
    }

    @Test
    void stockIsSetAndRecordedButSizedProductsAreLeftAlone() {
        Product plain = product("Bulk plain", 3, 100, 200.0, true);
        Product sized = product("Bulk sized", 3, 100, 200.0, true);
        size(sized);

        Map<String, Object> result = bulk.bulkStockUpdate(List.of(plain.getId(), sized.getId()), 9);

        assertEquals(1, result.get("count"));
        assertEquals(1, result.get("skipped"));
        assertEquals(9, products.findStockById(plain.getId()));
        assertEquals(3, products.findStockById(sized.getId()));
        assertEquals("ADJUSTMENT", history.getByProduct(plain.getId()).get(0).get("type"));
    }

    @Test
    void aPriceAboveMrpIsRefusedAndTheOthersStillChange() {
        Product ok = product("Bulk price ok", 1, 100, 500.0, true);
        Product low = product("Bulk price low mrp", 1, 100, 120.0, true);

        Map<String, Object> result = bulk.bulkPriceUpdate(List.of(ok.getId(), low.getId()), 150.0, null);

        assertEquals(1, result.get("count"));
        assertEquals(150.0, products.findById(ok.getId()).orElseThrow().getPrice());
        assertEquals(100.0, products.findById(low.getId()).orElseThrow().getPrice());
    }

    @Test
    void anIncompleteHiddenProductCannotBeSwitchedOn() {
        Product hidden = product("Bulk hidden", 1, 100, 200.0, false);
        doThrow(new IllegalArgumentException("blocked")).when(compliance)
                .assertPublishable(argThat(p -> p != null && hidden.getId().equals(p.getId())));

        Map<String, Object> result = bulk.bulkStatusUpdate(List.of(hidden.getId()), true);

        assertEquals(0, result.get("count"));
        assertFalse(products.findById(hidden.getId()).orElseThrow().isActive());
    }

    @Test
    void aCompleteHiddenProductCanBeSwitchedOnAndOff() {
        Product hidden = product("Bulk complete", 1, 100, 200.0, false);
        doNothing().when(compliance).assertPublishable(argThat(p -> p != null && hidden.getId().equals(p.getId())));

        assertEquals(1, bulk.bulkStatusUpdate(List.of(hidden.getId()), true).get("count"));
        assertTrue(products.findById(hidden.getId()).orElseThrow().isActive());
        assertEquals(1, bulk.bulkStatusUpdate(List.of(hidden.getId()), false).get("count"));
        assertFalse(products.findById(hidden.getId()).orElseThrow().isActive());
    }

    @Test
    void deletingAProductKeepsItsStockHistoryWithItsName() {
        Product p = product("Bulk delete me", 2, 100, 200.0, true);
        bulk.bulkStockUpdate(List.of(p.getId()), 5);
        long rowsBefore = history.recent().stream().filter(r -> "Bulk delete me".equals(r.get("productName"))).count();

        Map<String, Object> deleted = bulk.bulkDelete(List.of(p.getId()));
        assertEquals(1, deleted.get("count"), String.valueOf(deleted.get("problems")));

        assertTrue(products.findById(p.getId()).isEmpty());
        assertEquals(rowsBefore, history.recent().stream()
                .filter(r -> "Bulk delete me".equals(r.get("productName")) && Boolean.TRUE.equals(r.get("productDeleted"))).count());
    }

    @Test
    void aProductOnAPurchaseOrderIsKept() {
        Product p = product("Bulk on PO", 2, 100, 200.0, false);
        purchaseOrders.create(new OrderRequest("PO-B", null, null, "PENDING", null, null, null, null,
                List.of(new ItemRequest(p.getId(), null, 1, 1.0))));

        Map<String, Object> result = bulk.bulkDelete(List.of(p.getId()));

        assertEquals(0, result.get("count"));
        assertTrue(products.findById(p.getId()).isPresent());
    }
}
