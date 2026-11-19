package com.cauverystore.util;

import com.cauverystore.entities.Order;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonthlyRevenueTest {

    private static Order order(String status, double amount, LocalDateTime at) {
        Order o = new Order();
        o.setStatus(status);
        o.setTotalAmount(amount);
        o.setCreatedAt(at);
        return o;
    }

    @Test
    void groupsRevenueByMonthLeavingOutCancelledAndRefunded() {
        List<Order> orders = List.of(
                order("DELIVERED", 100.0, LocalDateTime.of(2026, 11, 3, 10, 0)),
                order("PLACED", 50.5, LocalDateTime.of(2026, 11, 18, 22, 0)),
                order("CANCELLED", 999.0, LocalDateTime.of(2026, 11, 5, 9, 0)),
                order("REFUNDED", 999.0, LocalDateTime.of(2026, 10, 5, 9, 0)),
                order("DELIVERED", 141.5, LocalDateTime.of(2026, 10, 25, 9, 0)),
                order("DELIVERED", 70.0, LocalDateTime.of(2025, 11, 30, 9, 0)),
                order("DELIVERED", 5.0, null));

        List<Map<String, Object>> months = MonthlyRevenue.lastMonths(orders, YearMonth.of(2026, 11), 12);

        assertEquals(12, months.size());
        assertEquals("2025-12", months.get(0).get("month"));
        assertEquals("Dec", months.get(0).get("label"));
        assertEquals("2026-11", months.get(11).get("month"));
        assertEquals(150.5, months.get(11).get("revenue"));
        assertEquals(141.5, months.get(10).get("revenue"));
        assertEquals(0.0, months.get(9).get("revenue"));
    }
}
