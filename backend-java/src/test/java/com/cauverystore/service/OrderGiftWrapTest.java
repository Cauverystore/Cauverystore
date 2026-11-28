package com.cauverystore.service;

import com.cauverystore.config.JwtUtil;
import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Checkout offers gift wrap at 49. It used to be added to the total on screen only - the order
 * never charged it, so the payment and the order disagreed. The server now charges it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderGiftWrapTest {

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
    @Mock private AccountRestrictionService accountRestrictionService;
    @Mock private ReturnEligibilityService returnEligibilityService;

    @InjectMocks private OrderService orderService;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(3L);
        user.setUsername("testuser");
        when(userRepo.findByUsername("testuser")).thenReturn(user);
        when(addressRepo.findActiveByUser(user)).thenReturn(List.of());
        when(addressRepo.save(any(Address.class))).thenAnswer(i -> i.getArgument(0));

        Product kurta = new Product();
        kurta.setId(1L);
        kurta.setName("Cotton Kurta");
        kurta.setStock(10);
        kurta.setPackageWeight(1.2);
        CartItem ci = new CartItem();
        ci.setProduct(kurta);
        ci.setQuantity(1);
        Cart cart = new Cart();
        cart.setItems(new ArrayList<>(List.of(ci)));
        when(cartService.getCart(user)).thenReturn(cart);
        when(productService.getDiscountedPriceDouble(1L)).thenReturn(1050.0);

        when(gstRateResolver.resolve(any(), anyBoolean(), any(), any()))
                .thenAnswer(i -> new GstRateResolver.Resolved(5.0, 0.0, false, "6109", true));
        when(orderRepo.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Address address() {
        Address a = new Address();
        a.setLine1("10 Main Road");
        a.setPincode("600001");
        return a;
    }

    @Test
    void giftWrap_shouldBeChargedOnTheOrder_withItsGstInside() {
        Order order = orderService.placeOrder("testuser", address(), "RAZORPAY", null, true);

        assertEquals(49.0, order.getGiftWrapCharge(), 0.001);
        assertEquals(135.0, order.getDeliveryCharge(), 0.001, "1.2 kg parcel");
        assertEquals(1234.0, order.getTotalAmount(), 0.001, "1,050 goods + 49 wrap + 135 delivery on top");
        // 50.00 inside the 1,050 plus 2.34 inside the 49, both at the goods' 5%.
        assertEquals(58.76, order.getTax(), 0.001, "52.34 on goods and wrap + 6.42 GST inside the 135 delivery");
    }

    @Test
    void switchedOffProductInTheCartCannotBeOrdered() {
        User user = userRepo.findByUsername("testuser");
        cartService.getCart(user).getItems().get(0).getProduct().setActive(false);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> orderService.placeOrder("testuser", address(), "COD", null, false));

        assertTrue(e.getMessage().contains("no longer available"));
        verifyNoInteractions(inventoryService);
        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    void noGiftWrap_shouldChargeNothingForIt() {
        Order order = orderService.placeOrder("testuser", address(), "RAZORPAY", null, false);

        assertNull(order.getGiftWrapCharge());
        assertEquals(1185.0, order.getTotalAmount(), 0.001, "1,050 goods + 135 delivery, even above Rs 500");
        assertEquals(56.42, order.getTax(), 0.001, "50 on goods + 6.42 GST inside the 135 delivery");
    }
}
