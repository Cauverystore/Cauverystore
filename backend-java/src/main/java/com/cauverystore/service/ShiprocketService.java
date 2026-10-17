package com.cauverystore.service;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderItem;
import com.cauverystore.entities.OrderTimeline;
import com.cauverystore.entities.Product;
import com.cauverystore.exception.OrderNotFoundException;
import com.cauverystore.repository.OrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Books orders with Shiprocket and follows them through to delivery.
 *
 * Booking: the admin presses "Ship with Shiprocket". The order is created in Shiprocket, a
 * courier and AWB are assigned, a pickup is requested and the label is fetched. The order
 * moves to PACKED - it is SHIPPED only once the courier has actually picked it up.
 *
 * Following: Shiprocket posts every scan to our webhook. Pickup moves the order to SHIPPED,
 * delivery to DELIVERED (both through OrderService, so the customer emails, invoice status and
 * audit log happen exactly as when an admin clicks the buttons). Every other change of courier
 * status is written to the order timeline the customer sees.
 */
@Service
public class ShiprocketService {

    private static final DateTimeFormatter SR_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** Used when a product has no weight on record - a typical small garment parcel. */
    private static final double DEFAULT_WEIGHT_KG = 0.5;
    private static final double[] DEFAULT_BOX_CM = {20, 15, 5};

    private static final Set<String> BOOKABLE_STATUSES = Set.of("PLACED", "CONFIRMED", "PACKED");
    private static final Set<String> PICKED_UP = Set.of(
            "PICKED UP", "SHIPPED", "IN TRANSIT", "OUT FOR DELIVERY", "REACHED AT DESTINATION HUB",
            "REACHED DESTINATION HUB", "IN TRANSIT-AT DESTINATION HUB", "MISROUTED", "DELAYED");

    private final ShiprocketClient client;
    private final OrderRepository orderRepo;
    private final OrderService orderService;
    private final String pickupLocation;
    private final String webhookToken;

    public ShiprocketService(ShiprocketClient client, OrderRepository orderRepo, OrderService orderService,
                             @Value("${shiprocket.pickup-location:Primary}") String pickupLocation,
                             @Value("${shiprocket.webhook-token:}") String webhookToken) {
        this.client = client;
        this.orderRepo = orderRepo;
        this.orderService = orderService;
        this.pickupLocation = pickupLocation;
        this.webhookToken = webhookToken;
    }

    /** What the admin screen shows - whether it is set up, never the secrets themselves. */
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", client.isConfigured());
        m.put("pickupLocation", pickupLocation);
        m.put("webhookProtected", webhookToken != null && !webhookToken.isBlank());
        return m;
    }

    // ------------------------------------------------------------------ booking

    // Deliberately not one transaction: if the courier step fails, the Shiprocket ids saved
    // before it must survive, or the retry would book the parcel in Shiprocket a second time.
    public Order bookShipment(Long orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        if (!client.isConfigured()) {
            throw new IllegalStateException("Shiprocket is not set up yet. Add SHIPROCKET_EMAIL and SHIPROCKET_PASSWORD in Railway.");
        }
        if (!BOOKABLE_STATUSES.contains(order.getStatus())) {
            throw new IllegalStateException("Only orders that are placed, confirmed or packed can be booked - this one is " + order.getStatus());
        }
        if (!order.isPaid() && !isCod(order)) {
            throw new IllegalStateException("This order is not paid yet, so it cannot be shipped");
        }
        if (order.getAddress() == null) {
            throw new IllegalStateException("This order has no delivery address");
        }

        // A first attempt can create the Shiprocket order and then fail at the courier step
        // (no courier for the pincode, empty wallet). The retry carries on from there instead of
        // creating a second Shiprocket order for the same parcel.
        if (order.getShiprocketShipmentId() == null) {
            JsonNode created = client.post("/orders/create/adhoc", buildOrderPayload(order));
            if (!created.hasNonNull("shipment_id")) {
                throw new IllegalStateException("Shiprocket did not accept the order: " + created);
            }
            order.setShiprocketOrderId(created.path("order_id").asLong());
            order.setShiprocketShipmentId(created.path("shipment_id").asLong());
            orderRepo.save(order);
        }
        long shipmentId = order.getShiprocketShipmentId();

        if (order.getTrackingNumber() == null || order.getTrackingNumber().isBlank()) {
            JsonNode awb = client.post("/courier/assign/awb", Map.of("shipment_id", shipmentId));
            JsonNode data = awb.path("response").path("data");
            String awbCode = data.path("awb_code").asText("");
            if (awbCode.isBlank()) {
                throw new IllegalStateException("No courier could be assigned: "
                        + firstNonBlank(data.path("awb_assign_error").asText(""), awb.path("message").asText(""), awb.toString())
                        + ". Check the Shiprocket wallet balance and that the pincode is serviceable.");
            }
            orderService.assignCourier(orderId, data.path("courier_name").asText("Courier"), awbCode);
            order = orderRepo.findById(orderId).orElseThrow();
        }

        // Pickup and label are worth having but not worth failing the booking over: both can be
        // done again from the Shiprocket panel, and the AWB above is what matters.
        try {
            client.post("/courier/generate/pickup", Map.of("shipment_id", List.of(shipmentId)));
        } catch (RuntimeException e) {
            addTimeline(order, "COURIER_NOTE", "Pickup not requested automatically - request it in Shiprocket (" + e.getMessage() + ")");
        }
        try {
            JsonNode label = client.post("/courier/generate/label", Map.of("shipment_id", List.of(shipmentId)));
            String url = label.path("label_url").asText("");
            if (!url.isBlank()) {
                order.setShippingLabelUrl(url);
                order.setLabelGeneratedAt(LocalDateTime.now());
            }
        } catch (RuntimeException e) {
            // The label stays downloadable from Shiprocket itself.
        }
        orderRepo.save(order);

        if (!"PACKED".equals(order.getStatus())) {
            order = orderService.updateOrderStatus(orderId, "PACKED");
        }
        return order;
    }

    Map<String, Object> buildOrderPayload(Order order) {
        Address a = order.getAddress();
        String[] name = splitName(firstNonBlank(a.getFullName(),
                order.getUser() != null ? order.getUser().getFullName() : null, "Customer"));

        List<Map<String, Object>> items = new ArrayList<>();
        double itemsTotal = 0;
        double weight = 0;
        double length = 0, breadth = 0, height = 0;
        for (OrderItem item : order.getItems()) {
            Product p = item.getProduct();
            Map<String, Object> line = new LinkedHashMap<>();
            String itemName = p != null ? p.getName() : "Item";
            if (item.getVariant() != null && item.getVariant().getVariantValue() != null) {
                itemName += " - " + item.getVariant().getVariantValue();
            }
            line.put("name", itemName);
            line.put("sku", firstNonBlank(
                    item.getVariant() != null ? item.getVariant().getSku() : null,
                    p != null ? p.getSku() : null,
                    "ITEM-" + (p != null ? p.getId() : item.getId())));
            line.put("units", item.getQuantity());
            line.put("selling_price", round2(item.getPrice()));
            if (p != null && p.getHsnCode() != null) line.put("hsn", p.getHsnCode());
            items.add(line);
            itemsTotal += item.getPrice() * item.getQuantity();

            double w = p != null ? firstPositive(p.getPackageWeight(), p.getWeight(), DEFAULT_WEIGHT_KG) : DEFAULT_WEIGHT_KG;
            weight += w * item.getQuantity();
            double[] box = parseDimensions(p != null ? firstNonBlank(p.getPackageDimensions(), p.getDimensions()) : null);
            length = Math.max(length, box[0]);
            breadth = Math.max(breadth, box[1]);
            height += box[2] * item.getQuantity();
        }

        double shipping = order.getDeliveryCharge() != null ? order.getDeliveryCharge() : 0;
        double giftWrap = order.getGiftWrapCharge() != null ? order.getGiftWrapCharge() : 0;
        // Coupons and loyalty points are not stored per line, so whatever separates the lines from
        // what the customer was charged goes in as one discount. For a COD parcel that keeps the
        // amount the courier collects equal to the order total.
        double discount = Math.max(0, itemsTotal + shipping + giftWrap - order.getTotalAmount());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order_id", String.valueOf(order.getId()));
        m.put("order_date", (order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now()).format(SR_DATE));
        m.put("pickup_location", pickupLocation);
        m.put("billing_customer_name", name[0]);
        m.put("billing_last_name", name[1]);
        m.put("billing_address", firstNonBlank(a.getLine1(), a.getStreet(), ""));
        m.put("billing_address_2", joinNonBlank(a.getLine2(), a.getLandmark()));
        m.put("billing_city", a.getCity());
        m.put("billing_pincode", a.getPincode());
        m.put("billing_state", a.getState());
        m.put("billing_country", firstNonBlank(a.getCountry(), "India"));
        m.put("billing_email", order.getUser() != null ? order.getUser().getEmail() : null);
        m.put("billing_phone", tenDigitPhone(firstNonBlank(a.getPhone(),
                order.getUser() != null ? order.getUser().getPhone() : null)));
        m.put("shipping_is_billing", true);
        m.put("order_items", items);
        m.put("payment_method", isCod(order) ? "COD" : "Prepaid");
        m.put("shipping_charges", round2(shipping));
        m.put("giftwrap_charges", round2(giftWrap));
        m.put("total_discount", round2(discount));
        m.put("sub_total", round2(itemsTotal));
        m.put("length", Math.max(1, Math.round(length)));
        m.put("breadth", Math.max(1, Math.round(breadth)));
        m.put("height", Math.max(1, Math.round(height)));
        m.put("weight", Math.max(0.1, Math.round(weight * 100) / 100.0));
        return m;
    }

    // ------------------------------------------------------------------ webhook

    public boolean webhookTokenMatches(String supplied) {
        if (webhookToken == null || webhookToken.isBlank() || supplied == null) return false;
        return java.security.MessageDigest.isEqual(
                webhookToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Applies one tracking update. Returns what happened, for the log. */
    @Transactional
    public String applyTrackingUpdate(JsonNode update) {
        String awb = update.path("awb").asText("");
        if (awb.isBlank()) return "ignored: no AWB";
        if (update.path("is_return").asInt(0) == 1) return "ignored: return shipment";

        Order order = orderRepo.findFirstByTrackingNumber(awb).orElse(null);
        if (order == null) return "ignored: no order with AWB " + awb;

        String status = firstNonBlank(update.path("current_status").asText(""),
                update.path("shipment_status").asText("")).trim().toUpperCase(Locale.ROOT);
        if (status.isBlank()) return "ignored: no status";

        String current = order.getStatus();
        if ("CANCELLED".equals(current) || "REFUNDED".equals(current)) {
            return "ignored: order is " + current;
        }

        String where = latestScanLocation(update);
        String note = toSentence(status) + (where.isBlank() ? "" : " - " + where);

        if ("DELIVERED".equals(status)) {
            if (!"DELIVERED".equals(current)) {
                if (!"SHIPPED".equals(current)) orderService.markShipped(order.getId());
                orderService.markDelivered(order.getId());
                return "order " + order.getId() + " delivered";
            }
            return "already delivered";
        }

        if (PICKED_UP.contains(status) && !"SHIPPED".equals(current) && !"DELIVERED".equals(current)) {
            orderService.markShipped(order.getId());
            order = orderRepo.findById(order.getId()).orElseThrow();
        }

        // Each courier status is written once, however many scans repeat it.
        if (!lastTimelineMessage(order).equals(note)) {
            addTimeline(order, status.startsWith("RTO") ? "RETURNING" : "IN_TRANSIT", note);
            orderRepo.save(order);
        }
        return "order " + order.getId() + ": " + status;
    }

    // ------------------------------------------------------------------ live tracking

    public static String publicTrackingUrl(String awb) {
        return awb == null || awb.isBlank() ? null
                : "https://shiprocket.co/tracking/" + java.net.URLEncoder.encode(awb, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isCod(Order order) {
        return order.getPaymentMethod() != null && order.getPaymentMethod().toUpperCase(Locale.ROOT).contains("COD");
    }

    private void addTimeline(Order order, String status, String message) {
        OrderTimeline t = new OrderTimeline();
        t.setOrder(order);
        t.setStatus(status);
        t.setMessage(message);
        t.setTimestamp(LocalDateTime.now());
        order.getTimeline().add(t);
    }

    private static String lastTimelineMessage(Order order) {
        List<OrderTimeline> tl = order.getTimeline();
        if (tl == null || tl.isEmpty()) return "";
        String m = tl.get(tl.size() - 1).getMessage();
        return m == null ? "" : m;
    }

    private static String latestScanLocation(JsonNode update) {
        JsonNode scans = update.path("scans");
        if (scans.isArray() && scans.size() > 0) {
            return scans.get(scans.size() - 1).path("location").asText("").trim();
        }
        return "";
    }

    /** "OUT FOR DELIVERY" -> "Out for delivery". */
    static String toSentence(String status) {
        String s = status.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (s.startsWith("rto")) s = "Returning to sender" + s.substring(3);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static double[] parseDimensions(String dims) {
        if (dims == null || dims.isBlank()) return DEFAULT_BOX_CM.clone();
        String[] parts = dims.toLowerCase(Locale.ROOT).replaceAll("[^0-9.x*×]", "").split("[x*×]");
        if (parts.length != 3) return DEFAULT_BOX_CM.clone();
        try {
            double[] d = new double[3];
            for (int i = 0; i < 3; i++) {
                d[i] = Double.parseDouble(parts[i]);
                if (d[i] <= 0) return DEFAULT_BOX_CM.clone();
            }
            return d;
        } catch (NumberFormatException e) {
            return DEFAULT_BOX_CM.clone();
        }
    }

    static String tenDigitPhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    private static String[] splitName(String full) {
        String f = full.trim();
        int sp = f.indexOf(' ');
        return sp < 0 ? new String[]{f, ""} : new String[]{f.substring(0, sp), f.substring(sp + 1).trim()};
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static double firstPositive(Double... values) {
        for (Double v : values) if (v != null && v > 0) return v;
        return DEFAULT_WEIGHT_KG;
    }

    private static String joinNonBlank(String... values) {
        List<String> parts = new ArrayList<>();
        for (String v : values) if (v != null && !v.isBlank()) parts.add(v.trim());
        return String.join(", ", parts);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
