package com.cauverystore.service;

import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderTimeline;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracking for an order, from real sources only.
 *
 * With an AWB and Shiprocket set up, the courier's own scans are fetched live. Otherwise - or
 * if Shiprocket cannot be reached - the order's timeline is shown, which holds the status
 * changes the store itself recorded. Nothing here is estimated or made up: a customer is never
 * told a parcel is somewhere it is not.
 */
@Service
public class CourierTrackingService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final ShiprocketClient shiprocket;

    public CourierTrackingService(ShiprocketClient shiprocket) {
        this.shiprocket = shiprocket;
    }

    public Map<String, Object> getTrackingInfo(Order order) {
        Map<String, Object> result = new LinkedHashMap<>();
        String awb = order.getTrackingNumber();
        result.put("trackingNumber", awb);
        result.put("courier", order.getCourier() != null && !order.getCourier().isBlank() ? order.getCourier() : null);
        result.put("status", order.getStatus());
        result.put("trackingUrl", ShiprocketService.publicTrackingUrl(awb));

        String estimate = null;
        List<Map<String, Object>> events = new ArrayList<>();
        if (shiprocket.isConfigured() && awb != null && !awb.isBlank()) {
            try {
                JsonNode data = shiprocket.get("/courier/track/awb/" + URLEncoder.encode(awb, StandardCharsets.UTF_8))
                        .path("tracking_data");
                String etd = data.path("etd").asText("");
                if (!etd.isBlank()) estimate = etd;
                for (JsonNode s : data.path("shipment_track_activities")) {
                    events.add(event(s.path("sr-status-label").asText(s.path("status").asText("")),
                            s.path("activity").asText(""), s.path("location").asText(""), s.path("date").asText("")));
                }
            } catch (RuntimeException e) {
                // Fall through to the timeline below; a courier hiccup must not break the page.
            }
        }
        if (events.isEmpty() && order.getTimeline() != null) {
            for (OrderTimeline t : order.getTimeline()) {
                events.add(event(t.getStatus(), t.getMessage(), "",
                        t.getTimestamp() != null ? t.getTimestamp().format(FORMATTER) : ""));
            }
        }

        if ("DELIVERED".equals(order.getStatus())) estimate = "Delivered";
        else if ("CANCELLED".equals(order.getStatus()) || "REFUNDED".equals(order.getStatus())) estimate = "Cancelled";
        result.put("estimatedDelivery", estimate);
        result.put("events", events);
        return result;
    }

    private Map<String, Object> event(String status, String description, String location, String timestamp) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("status", status);
        e.put("description", description);
        e.put("location", location);
        e.put("timestamp", timestamp);
        return e;
    }
}
