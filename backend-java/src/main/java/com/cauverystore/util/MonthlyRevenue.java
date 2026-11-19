package com.cauverystore.util;

import com.cauverystore.entities.Order;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Revenue per calendar month for the Super Admin dashboard chart. Counted the same way as the
 * dashboard's "Total Revenue" card: the order total of every order that is not cancelled or
 * refunded, by the month the order was placed.
 */
public final class MonthlyRevenue {

    private MonthlyRevenue() {
    }

    /** The {@code months} months ending with {@code current}, oldest first, empty months as 0. */
    public static List<Map<String, Object>> lastMonths(Collection<Order> orders, YearMonth current, int months) {
        Map<YearMonth, Double> totals = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            totals.put(current.minusMonths(i), 0.0);
        }
        for (Order o : orders) {
            if (o == null || o.getCreatedAt() == null || !counts(o.getStatus())) continue;
            YearMonth month = YearMonth.from(o.getCreatedAt());
            if (totals.containsKey(month)) {
                totals.put(month, totals.get(month) + o.getTotalAmount());
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        totals.forEach((month, revenue) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", month.toString());
            m.put("label", month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            m.put("year", month.getYear());
            m.put("revenue", Math.round(revenue * 100.0) / 100.0);
            result.add(m);
        });
        return result;
    }

    private static boolean counts(String status) {
        return !"CANCELLED".equals(status) && !"REFUNDED".equals(status);
    }
}
