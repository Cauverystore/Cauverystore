package com.cauverystore.service;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderItem;
import com.cauverystore.entities.OrderTimeline;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.User;
import com.cauverystore.repository.OrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Booking parcels with Shiprocket and applying its tracking webhook.
 *
 * The amounts matter most: for a cash-on-delivery parcel the courier collects whatever the
 * payload adds up to, so it has to come to exactly what the customer was charged.
 */
class ShiprocketServiceTest {

    private ShiprocketClient client;
    private OrderRepository orderRepo;
    private OrderService orderService;
    private ShiprocketService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = mock(ShiprocketClient.class);
        orderRepo = mock(OrderRepository.class);
        orderService = mock(OrderService.class);
        service = new ShiprocketService(client, orderRepo, orderService, "Primary", "secret-token");
    }

    private Order order() {
        User u = new User();
        u.setEmail("buyer@example.com");
        u.setFullName("Buyer Name");
        Address a = new Address();
        a.setFullName("Meena Ravi Kumar");
        a.setPhone("+91 98765-43210");
        a.setLine1("12 Gandhi Street");
        a.setLandmark("Near the temple");
        a.setCity("Coimbatore");
        a.setState("Tamil Nadu");
        a.setPincode("641001");

        Product shirt = new Product();
        shirt.setName("Cotton Shirt");
        shirt.setSku("SHIRT-1");
        shirt.setHsnCode("6205");
        shirt.setPackageWeight(0.3);
        shirt.setPackageDimensions("30 x 25 x 3");

        OrderItem item = new OrderItem();
        item.setProduct(shirt);
        item.setQuantity(2);
        item.setPrice(500);

        Order o = new Order();
        o.setId(42L);
        o.setUser(u);
        o.setAddress(a);
        o.setItems(new ArrayList<>(List.of(item)));
        o.setDeliveryCharge(40.0);
        o.setGiftWrapCharge(49.0);
        o.setTotalAmount(989.0); // 1000 + 40 + 49 - 100 coupon
        o.setPaid(true);
        o.setPaymentMethod("RAZORPAY");
        o.setStatus("PLACED");
        return o;
    }

    @Test
    void payloadAddsUpToWhatTheCustomerWasCharged() {
        Map<String, Object> p = service.buildOrderPayload(order());

        double collectable = (double) p.get("sub_total") + (double) p.get("shipping_charges")
                + (double) p.get("giftwrap_charges") - (double) p.get("total_discount");
        assertEquals(989.0, collectable, 0.001);
        assertEquals(100.0, (double) p.get("total_discount"), 0.001);
        assertEquals("Prepaid", p.get("payment_method"));
        assertEquals("42", p.get("order_id"));
        assertEquals("Primary", p.get("pickup_location"));
    }

    @Test
    void payloadCarriesAddressPhoneAndParcelSize() {
        Map<String, Object> p = service.buildOrderPayload(order());

        assertEquals("Meena", p.get("billing_customer_name"));
        assertEquals("Ravi Kumar", p.get("billing_last_name"));
        assertEquals("9876543210", p.get("billing_phone"));
        assertEquals("Near the temple", p.get("billing_address_2"));
        assertEquals(0.6, (double) p.get("weight"), 0.001);
        assertEquals(30L, p.get("length"));
        assertEquals(25L, p.get("breadth"));
        assertEquals(6L, p.get("height")); // two shirts stacked
    }

    @Test
    void missingSizesFallBackToASmallParcel() {
        assertArrayEquals(new double[]{20, 15, 5}, ShiprocketService.parseDimensions(null));
        assertArrayEquals(new double[]{20, 15, 5}, ShiprocketService.parseDimensions("big"));
        assertArrayEquals(new double[]{16, 8, 1}, ShiprocketService.parseDimensions("16x8x1"));
    }

    @Test
    void webhookTokenMustMatch() {
        assertTrue(service.webhookTokenMatches("secret-token"));
        assertFalse(service.webhookTokenMatches("wrong"));
        assertFalse(service.webhookTokenMatches(null));
        ShiprocketService unprotected = new ShiprocketService(client, orderRepo, orderService, "Primary", "");
        assertFalse(unprotected.webhookTokenMatches(""), "an empty token must never let updates in");
    }

    private JsonNode update(String awb, String status, String location) throws Exception {
        return mapper.readTree("{\"awb\":\"" + awb + "\",\"current_status\":\"" + status
                + "\",\"scans\":[{\"location\":\"" + location + "\"}]}");
    }

    @Test
    void pickupMarksTheOrderShipped() throws Exception {
        Order o = order();
        o.setStatus("PACKED");
        o.setTrackingNumber("AWB1");
        when(orderRepo.findFirstByTrackingNumber("AWB1")).thenReturn(Optional.of(o));
        when(orderRepo.findById(42L)).thenReturn(Optional.of(o));

        service.applyTrackingUpdate(update("AWB1", "PICKED UP", "Coimbatore Hub"));

        verify(orderService).markShipped(42L);
        verify(orderService, never()).markDelivered(any());
        OrderTimeline last = o.getTimeline().get(o.getTimeline().size() - 1);
        assertEquals("Picked up - Coimbatore Hub", last.getMessage());
    }

    @Test
    void deliveryMarksTheOrderDelivered() throws Exception {
        Order o = order();
        o.setStatus("SHIPPED");
        o.setTrackingNumber("AWB2");
        when(orderRepo.findFirstByTrackingNumber("AWB2")).thenReturn(Optional.of(o));

        service.applyTrackingUpdate(update("AWB2", "DELIVERED", "Coimbatore"));

        verify(orderService).markDelivered(42L);
        verify(orderService, never()).markShipped(any());
    }

    @Test
    void repeatedScansAreWrittenOnce() throws Exception {
        Order o = order();
        o.setStatus("SHIPPED");
        o.setTrackingNumber("AWB3");
        when(orderRepo.findFirstByTrackingNumber("AWB3")).thenReturn(Optional.of(o));

        service.applyTrackingUpdate(update("AWB3", "IN TRANSIT", "Salem"));
        service.applyTrackingUpdate(update("AWB3", "IN TRANSIT", "Salem"));

        assertEquals(1, o.getTimeline().size());
        verify(orderService, never()).markShipped(any());
    }

    @Test
    void cancelledOrdersAndUnknownParcelsAreLeftAlone() throws Exception {
        Order o = order();
        o.setStatus("CANCELLED");
        o.setTrackingNumber("AWB4");
        when(orderRepo.findFirstByTrackingNumber("AWB4")).thenReturn(Optional.of(o));
        when(orderRepo.findFirstByTrackingNumber("NOPE")).thenReturn(Optional.empty());

        assertTrue(service.applyTrackingUpdate(update("AWB4", "DELIVERED", "x")).startsWith("ignored"));
        assertTrue(service.applyTrackingUpdate(update("NOPE", "DELIVERED", "x")).startsWith("ignored"));
        verifyNoInteractions(orderService);
    }

    @Test
    void bookingRefusesUnpaidOrdersAndMissingSetup() {
        Order o = order();
        o.setPaid(false);
        when(orderRepo.findById(42L)).thenReturn(Optional.of(o));
        when(client.isConfigured()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.bookShipment(42L));

        when(client.isConfigured()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> service.bookShipment(42L));
        verify(client, never()).post(any(), any());
    }
}
