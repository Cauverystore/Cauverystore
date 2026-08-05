package com.cauverystore.service;

import com.cauverystore.entities.Order;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulated courier tracking adapter.
 *
 * Real courier integrations (Delhivery, BlueDart, etc.) require paid API keys, so until
 * credentials are provisioned this service produces deterministic, status-driven tracking
 * events based on the order lifecycle. Swap the implementation for a real provider
 * (or add a provider abstraction) without changing the controller contract.
 */
@Service
public class CourierTrackingService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public Map<String, Object> getTrackingInfo(Order order) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trackingNumber", order.getTrackingNumber());
        result.put("courier", order.getCourier() != null && !order.getCourier().isBlank()
                ? order.getCourier() : "Cauvery Express");
        result.put("status", order.getStatus());
        result.put("estimatedDelivery", estimateDelivery(order));
        result.put("events", buildEvents(order));
        return result;
    }

    private List<Map<String, Object>> buildEvents(Order order) {
        List<Map<String, Object>> events = new ArrayList<>();

        LocalDateTime created = order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now();

        events.add(event("PLACED", "Order placed", "Origin hub, Chennai", created));
        events.add(event("PICKED", "Package picked up by courier", "Origin hub, Chennai", created.plusHours(18)));

        if ("CANCELLED".equals(order.getStatus()) || "REFUNDED".equals(order.getStatus())) {
            events.add(event("CANCELLED", "Shipment cancelled", "Origin hub, Chennai", created.plusHours(20)));
            return events;
        }

        events.add(event("CONFIRMED", "Order confirmed by seller", "Origin hub, Chennai", created.plusHours(10)));
        events.add(event("PACKED", "Package packed and labelled", "Origin hub, Chennai", created.plusHours(26)));

        LocalDateTime shipped = order.getShippedAt() != null ? order.getShippedAt() : created.plusHours(36);
        if (isShippedOrLater(order.getStatus())) {
            events.add(event("SHIPPED", "Shipped from origin hub", "Origin hub, Chennai", shipped));
            events.add(event("IN_TRANSIT", "Arrived at regional sorting facility", "Regional hub, Coimbatore", shipped.plusHours(12)));
            events.add(event("OUT_FOR_DELIVERY", "Out for delivery", order.getAddress() != null && order.getAddress().getCity() != null
                    ? order.getAddress().getCity() : "Destination", shipped.plusHours(30)));
        }

        if ("DELIVERED".equals(order.getStatus())) {
            LocalDateTime delivered = order.getDeliveredAt() != null ? order.getDeliveredAt() : shipped.plusHours(34);
            events.add(event("DELIVERED", "Delivered. Thank you for shopping with Cauvery Store",
                    order.getAddress() != null && order.getAddress().getCity() != null
                            ? order.getAddress().getCity() : "Destination", delivered));
        }

        return events;
    }

    private boolean isShippedOrLater(String status) {
        List<String> progression = List.of("SHIPPED", "DELIVERED");
        return progression.contains(status);
    }

    private Map<String, Object> event(String status, String description, String location, LocalDateTime timestamp) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("status", status);
        e.put("description", description);
        e.put("location", location);
        e.put("timestamp", timestamp != null ? timestamp.format(FORMATTER) : "");
        return e;
    }

    private String estimateDelivery(Order order) {
        if ("DELIVERED".equals(order.getStatus())) {
            return "Delivered";
        }
        if ("CANCELLED".equals(order.getStatus()) || "REFUNDED".equals(order.getStatus())) {
            return "Cancelled";
        }
        LocalDateTime created = order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now();
        LocalDateTime estimate = order.getShippedAt() != null ? order.getShippedAt().plusHours(34) : created.plusHours(70);
        return "Estimated " + estimate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }
}
