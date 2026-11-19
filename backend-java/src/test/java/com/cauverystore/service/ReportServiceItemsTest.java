package com.cauverystore.service;

import com.cauverystore.entities.Category;
import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderItem;
import com.cauverystore.entities.Product;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportServiceItemsTest {

    private static Product product(long id, String name, String category) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        if (category != null) {
            Category c = new Category();
            c.setName(category);
            p.setCategory(c);
        }
        return p;
    }

    private static Order order(long id, String status, LocalDateTime at, Object[]... lines) {
        Order o = new Order();
        o.setId(id);
        o.setStatus(status);
        o.setCreatedAt(at);
        for (Object[] l : lines) {
            OrderItem i = new OrderItem();
            i.setProduct((Product) l[0]);
            i.setQuantity((Integer) l[1]);
            i.setPrice((Double) l[2]);
            o.getItems().add(i);
        }
        return o;
    }

    @Test
    void listsEveryItemSortedByCategoryThenDateWithUncategorisedLast() {
        Product bottle = product(60, "Steel Bottle", "Kitchen");
        Product sheet = product(61, "Bedsheet", "Home");
        Product loose = product(62, "Loose item", null);
        List<Order> orders = List.of(
                order(7, "DELIVERED", LocalDateTime.of(2026, 11, 10, 9, 0),
                        new Object[]{bottle, 2, 349.5}, new Object[]{loose, 1, 10.0}),
                order(5, "CANCELLED", LocalDateTime.of(2026, 11, 2, 9, 0),
                        new Object[]{sheet, 1, 899.0}),
                order(6, "PLACED", LocalDateTime.of(2026, 11, 2, 9, 0),
                        new Object[]{bottle, 1, 349.5}));

        List<Map<String, Object>> rows = ReportService.itemRows(orders);

        assertEquals(4, rows.size());
        assertEquals("Home", rows.get(0).get("category"));
        assertEquals("CANCELLED", rows.get(0).get("orderStatus"));
        assertEquals("Kitchen", rows.get(1).get("category"));
        assertEquals(6L, rows.get(1).get("orderId"));
        assertEquals(7L, rows.get(2).get("orderId"));
        assertEquals(699.0, rows.get(2).get("lineTotal"));
        assertEquals("2026-11-10", rows.get(2).get("orderDate"));
        assertEquals("Uncategorised", rows.get(3).get("category"));
    }
}
