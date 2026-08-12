package com.cauverystore.service;

import com.cauverystore.config.JwtUtil;
import com.cauverystore.entities.Address;
import com.cauverystore.entities.Cart;
import com.cauverystore.entities.User;
import com.cauverystore.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Proves the checkout path never mints a duplicate address: placing an order to an address the
 * user already has (matching line1-or-street + pincode) must reuse the existing row, not save a
 * new one. This is the regression that surfaces as the address book growing a lookalike row per
 * order.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceDedupeTest {

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

    @InjectMocks
    private OrderService orderService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(3L);
        user.setUsername("testuser");
        user.setEmail("test@test.com");
        user.setFullName("Test User");

        when(userRepo.findByUsername("testuser")).thenReturn(user);
        // An empty cart: the address resolution happens before the cart is touched, so the empty
        // cart terminates the flow after we have observed the dedupe behaviour we care about.
        Cart cart = new Cart();
        cart.setItems(List.of());
        when(cartService.getCart(user)).thenReturn(cart);
    }

    @Test
    void placeOrderReusesExistingAddressMatchingLine1AndPincode() {
        Address existing = new Address();
        existing.setId(55L);
        existing.setLine1("14 Gandhi Street");
        existing.setPincode("600001");
        existing.setActiveFlag(true);
        existing.setUser(user);
        when(addressRepo.findActiveByUser(user)).thenReturn(List.of(existing));

        Address incoming = new Address();
        incoming.setFullName("Test User");
        incoming.setLine1("14 Gandhi Street");
        incoming.setCity("Chennai");
        incoming.setState("Tamil Nadu");
        incoming.setPincode("600001");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> orderService.placeOrder("testuser", incoming, "COD", null));
        assertEquals("Cart has no active items", ex.getMessage());

        verify(addressRepo, never()).save(any(Address.class));
    }

    @Test
    void placeOrderReusesLegacyStreetOnlyRow() {
        // Rows created before the line1 field existed: street set, line1 NULL.
        Address legacy = new Address();
        legacy.setId(66L);
        legacy.setLine1(null);
        legacy.setStreet("14 Gandhi Street");
        legacy.setPincode("600001");
        legacy.setActiveFlag(true);
        legacy.setUser(user);
        when(addressRepo.findActiveByUser(user)).thenReturn(List.of(legacy));

        Address incoming = new Address();
        incoming.setFullName("Test User");
        incoming.setLine1("14 Gandhi Street");
        incoming.setPincode("600001");

        assertThrows(RuntimeException.class,
                () -> orderService.placeOrder("testuser", incoming, "COD", null));

        verify(addressRepo, never()).save(any(Address.class));
    }

    @Test
    void placeOrderSavesOnlyWhenNoExistingAddressMatches() {
        when(addressRepo.findActiveByUser(user)).thenReturn(List.of());
        when(addressRepo.save(any(Address.class))).thenAnswer(i -> i.getArgument(0));

        Address incoming = new Address();
        incoming.setFullName("Test User");
        incoming.setLine1("99 Brand New Road");
        incoming.setPincode("600050");

        assertThrows(RuntimeException.class,
                () -> orderService.placeOrder("testuser", incoming, "COD", null));

        verify(addressRepo).save(incoming);
        assertEquals("99 Brand New Road", incoming.getStreet());
        assertTrue(Boolean.TRUE.equals(incoming.getActiveFlag()));
    }
}