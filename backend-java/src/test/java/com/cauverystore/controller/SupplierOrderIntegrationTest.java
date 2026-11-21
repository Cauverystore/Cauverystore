package com.cauverystore.controller;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import com.cauverystore.service.SupplierOrderService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"agents.watch.alert-email=", "agents.orders.first-check-ms=3600000"})
@ActiveProfiles("test")
@Tag("integration")
class SupplierOrderIntegrationTest {

    @Autowired SupplierOrderService service;
    @Autowired SupplierOrderTaskRepository tasks;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired AgentJobRepository jobs;
    @Autowired AddressRepository addresses;

    private Product product(boolean fromSupplier) {
        Product p = new Product();
        p.setName("Supplier order box");
        p.setPrice(209.0);
        p.setStock(10);
        p.setSku("SO-1");
        p = products.save(p);
        if (fromSupplier) {
            AgentJob j = new AgentJob();
            j.setSourceUrl("https://deodap.in/products/so-" + p.getId());
            j.setStatus(AgentJob.USED);
            j.setProductId(p.getId());
            j.setSupplierCost(100.0);
            jobs.save(j);
        }
        return p;
    }

    private Order order(Product p, boolean paid, String method) {
        Address a = new Address();
        a.setFullName("A Customer");
        a.setLine1("1 Main Road");
        a.setCity("Coimbatore");
        a.setState("Tamil Nadu");
        a.setPincode("641001");
        a.setPhone("9000000000");
        a = addresses.save(a);
        Order o = new Order();
        o.setAddress(a);
        o.setPaid(paid);
        o.setPaymentMethod(method);
        o.setTotalAmount(418);
        OrderItem i = new OrderItem();
        i.setOrder(o);
        i.setProduct(p);
        i.setQuantity(2);
        i.setPrice(209);
        o.setItems(new java.util.ArrayList<>(List.of(i)));
        return orders.save(o);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rowFor(String view, Long orderId) {
        return ((List<Map<String, Object>>) service.list(view).get("rows")).stream()
                .filter(r -> orderId.equals(r.get("orderId"))).findFirst().orElse(null);
    }

    @Test
    void fullCycle() {
        Order paid = order(product(true), true, "RAZORPAY");
        Order unpaid = order(product(true), false, "RAZORPAY");
        Order cod = order(product(true), false, "COD");
        Order ownStock = order(product(false), true, "RAZORPAY");

        service.prepare();
        service.prepare();

        Map<String, Object> row = rowFor("todo", paid.getId());
        assertNotNull(row);
        assertEquals(2, row.get("quantity"));
        assertEquals(100.0, row.get("expectedCost"));
        assertTrue(String.valueOf(row.get("shipTo")).contains("641001"));
        assertNotNull(rowFor("todo", cod.getId()));
        assertNull(rowFor("todo", unpaid.getId()), "an unpaid online order must not be bought");
        assertNull(rowFor("todo", ownStock.getId()), "a product not from a supplier needs no supplier order");
        assertEquals(1, tasks.findAll().stream().filter(t -> t.getOrderId().equals(paid.getId())).count(), "no duplicates");

        Long id = ((Number) row.get("id")).longValue();
        assertThrows(SupplierOrderService.BadRequest.class, () -> service.markOrdered(id, " ", 200.0, "admin"));
        service.markOrdered(id, "DD-77", 200.0, "admin");
        assertThrows(SupplierOrderService.BadRequest.class, () -> service.markOrdered(id, "DD-78", 200.0, "admin"));

        Order fresh = orders.findById(paid.getId()).orElseThrow();
        fresh.setStatus("CANCELLED");
        orders.save(fresh);
        Order codFresh = orders.findById(cod.getId()).orElseThrow();
        codFresh.setStatus("CANCELLED");
        orders.save(codFresh);
        service.prepare();

        assertNotNull(rowFor("problem", paid.getId()), "bought, then cancelled: a person must sort it out");
        assertNull(rowFor("todo", cod.getId()), "not yet bought and cancelled: drops off the list");
        service.markHandled(id, "admin");
        assertNull(rowFor("problem", paid.getId()));
    }
}
