package com.cauverystore.controller;

import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderItem;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.OrderItemRepository;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.service.AuthorizationService;
import com.cauverystore.service.HsnClassificationService;
import com.cauverystore.service.ProductComplianceService;
import com.cauverystore.service.ProductService;
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

/** Every route that can switch a product on, with incomplete legal details. */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class ProductSwitchOnIntegrationTest {

    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @MockBean HsnClassificationService hsn;
    @MockBean AuthorizationService authorization;
    @SpyBean ProductComplianceService compliance;

    private Product product(String name, boolean active, String approval) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(100.0);
        p.setMrp(200.0);
        p.setStock(5);
        p.setActive(active);
        p.setApprovalStatus(approval);
        return products.save(p);
    }

    private void incomplete(Product product) {
        doThrow(new IllegalArgumentException("Legal details are missing")).when(compliance)
                .assertPublishable(argThat(p -> p != null && product.getId().equals(p.getId())));
    }

    private void complete(Product product) {
        doNothing().when(compliance).assertPublishable(argThat(p -> p != null && product.getId().equals(p.getId())));
    }

    private boolean isOn(Product p) {
        return products.findById(p.getId()).orElseThrow().isActive();
    }

    @Test
    void theOnOffSwitchRefusesAnIncompleteProduct() {
        Product p = product("Switch incomplete", false, "approved");
        incomplete(p);

        assertThrows(RuntimeException.class, () -> productService.updateActiveStatus(p.getId(), true));

        assertFalse(isOn(p));
    }

    @Test
    void theOnOffSwitchStillWorksForACompleteProduct() {
        Product p = product("Switch complete", false, "approved");
        complete(p);

        productService.updateActiveStatus(p.getId(), true);
        assertTrue(isOn(p));
        productService.updateActiveStatus(p.getId(), false);
        assertFalse(isOn(p));
    }

    @Test
    void anIncompleteProductCanAlwaysBeSwitchedOff() {
        Product p = product("Switch off", true, "approved");
        incomplete(p);

        productService.updateActiveStatus(p.getId(), false);

        assertFalse(isOn(p));
    }

    @Test
    void aProductWaitingForApprovalCanBeOnButStaysHidden() {
        Product p = product("Switch pending", false, "pending");
        incomplete(p);

        productService.updateActiveStatus(p.getId(), true);

        Product row = products.findById(p.getId()).orElseThrow();
        assertTrue(row.isActive());
        assertFalse(ProductService.isAvailableToShoppers(row));
    }

    @Test
    void bulkEditRefusesAnIncompleteProductAndChangesNothingOnIt() {
        Product bad = product("Bulk edit bad", false, "approved");
        Product good = product("Bulk edit good", false, "approved");
        incomplete(bad);
        complete(good);

        Map<String, Object> result = productService.bulkEdit(Map.of(
                "productIds", List.of(bad.getId().intValue(), good.getId().intValue()),
                "active", true, "price", 150, "stock", 9), null);

        assertEquals(1, result.get("updated"));
        Product badRow = products.findById(bad.getId()).orElseThrow();
        assertFalse(badRow.isActive());
        assertEquals(100.0, badRow.getPrice());
        assertEquals(5, badRow.getStock());
        Product goodRow = products.findById(good.getId()).orElseThrow();
        assertTrue(goodRow.isActive());
        assertEquals(150.0, goodRow.getPrice());
        assertEquals(9, goodRow.getStock());
    }

    @Test
    void bulkEditCannotPriceAboveMrp() {
        Product p = product("Bulk edit MRP", true, "approved");

        Map<String, Object> result = productService.bulkEdit(Map.of(
                "productIds", List.of(p.getId().intValue()), "price", 250), null);

        assertEquals(0, result.get("updated"));
        assertEquals(100.0, products.findById(p.getId()).orElseThrow().getPrice());
    }

    @Test
    void aCopyWaitsForApproval() {
        Product original = product("Switch original", true, "approved");

        Product copy = productService.cloneProduct(original.getId());

        assertFalse(ProductService.isAvailableToShoppers(copy));
    }

    @Test
    void aRefusedDeleteDoesNotPutAnIncompleteProductOnSale() {
        Product p = product("Switch cancelled order", false, "approved");
        incomplete(p);
        Order order = new Order();
        order.setStatus("CANCELLED");
        order = orders.save(order);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(p);
        item.setQuantity(1);
        item.setPrice(100.0);
        orderItems.save(item);

        assertFalse(productService.deleteProductCascade(p.getId()));

        assertFalse(isOn(p));
    }
}
