package com.cauverystore.service;

import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderItem;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.User;
import com.cauverystore.dto.InvoiceResponse;
import com.cauverystore.repository.*;
import com.cauverystore.config.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers the customer-facing order invoice.
 *
 * This is the document the buyer sees for what they paid, so the figures on it have to be the
 * figures that were charged. It used to recompute them - tax at a flat 12% of the undiscounted
 * subtotal, delivery re-derived from a threshold, and the total re-added from those - so it
 * could disagree with the payment and with the GST invoice at the same time.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock private OrderRepository orderRepo;
    @Mock private OrderItemRepository orderItemRepo;
    @Mock private CartRepository cartRepo;
    @Mock private ProductRepository productRepo;
    @Mock private UserRepository userRepo;
    @Mock private InventoryRepository inventoryRepo;
    @Mock private CartService cartService;
    @Mock private InventoryService inventoryService;
    @Mock private NotificationService notificationService;
    @Mock private RefundRepository refundRepo;
    @Mock private AddressRepository addressRepo;
    @Mock private JwtUtil jwtUtil;
    @Mock private InvoicePdfService invoicePdfService;
    @Mock private AuditService auditService;
    @Mock private GstInvoiceService gstInvoiceService;
    @Mock private GstConfigurationRepository gstConfigRepo;
    @Mock private SellerRegistrationRepository sellerRegRepo;
    @Mock private ProductService productService;
    @Mock private CouponService couponService;
    @Mock private ReturnRequestRepository returnRequestRepo;
    @Mock private PaymentService paymentService;
    @Mock private PaymentRepository paymentRepo;
    @Mock private CreditNoteService creditNoteService;
    @Mock private CourierTrackingService courierTrackingService;
    @Mock private GstRateResolver gstRateResolver;

    @InjectMocks private OrderService orderService;

    private User user;
    private Order order;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setEmail("buyer@cauverystore.in");
        user.setFullName("Buyer");

        Product product = new Product();
        product.setName("Basmati Rice 5kg");
        product.setHsnCode("1006");

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setPrice(400.0);
        item.setQuantity(2);

        order = new Order();
        order.setId(44L);
        order.setUser(user);
        order.setItems(List.of(item));
        order.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        order.setPaymentMethod("RAZORPAY");

        when(jwtUtil.getEmailFromToken(anyString())).thenReturn("buyer@cauverystore.in");
        when(userRepo.findByEmail("buyer@cauverystore.in")).thenReturn(user);
        when(orderRepo.findById(44L)).thenReturn(Optional.of(order));
    }

    @Test
    void getInvoice_shouldReportTheTaxAndTotalActuallyCharged() {
        // Subtotal is 800. A flat 12% would have printed 96.00 tax and a 936.00 total; the
        // customer actually paid 5% GST on rice, less a discount, and free delivery.
        order.setTax(35.0);
        order.setDeliveryCharge(0.0);
        order.setTotalAmount(735.0);

        InvoiceResponse invoice = orderService.getInvoice(44L, "Bearer token");

        assertEquals(800.0, invoice.getSubtotal());
        assertEquals(35.0, invoice.getTax(), "must be the GST charged, not 12% of the subtotal");
        assertEquals(0.0, invoice.getDeliveryCharge());
        assertEquals(735.0, invoice.getTotalAmount(), "must equal what was paid");
        verifyNoInteractions(gstRateResolver);
    }

    @Test
    void getInvoice_shouldNotReAddTheTotalFromItsParts() {
        // The stored total is authoritative even when it does not equal subtotal + tax +
        // delivery, because a discount or coupon lives in the difference.
        order.setTax(40.0);
        order.setDeliveryCharge(40.0);
        order.setTotalAmount(690.0);

        assertEquals(690.0, orderService.getInvoice(44L, "Bearer token").getTotalAmount());
    }

    @Test
    void getInvoice_shouldFallBackToResolvedRates_forOrdersPredatingStoredTax() {
        // Older orders have no tax on the row. Re-deriving it must go through the resolver at
        // the order's own date, not assume a flat rate.
        order.setTax(null);
        order.setDeliveryCharge(null);
        order.setTotalAmount(840.0);
        when(gstRateResolver.resolve(any(), anyBoolean(), any(), any()))
                .thenReturn(new GstRateResolver.Resolved(5.0, 0.0, false, "1006", true));

        InvoiceResponse invoice = orderService.getInvoice(44L, "Bearer token");

        assertEquals(40.0, invoice.getTax(), "800 at 5%, not 12%");
        verify(gstRateResolver).resolve(any(), eq(false), eq(LocalDate.of(2026, 8, 1)), eq(400.0));
    }

    @Test
    void getInvoice_shouldRejectSomeoneElsesOrder() {
        User other = new User();
        other.setId(99L);
        other.setEmail("someone@else.in");
        when(jwtUtil.getEmailFromToken(anyString())).thenReturn("someone@else.in");
        when(userRepo.findByEmail("someone@else.in")).thenReturn(other);

        assertThrows(RuntimeException.class, () -> orderService.getInvoice(44L, "Bearer token"));
    }
}
