package com.cauverystore.service;

import com.cauverystore.entities.Order;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.repository.*;
import com.cauverystore.service.AccountDeletionService.AccountDeletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A shopper can delete their own account; personal data goes, order records stay. */
class AccountDeletionServiceTest {

    private UserRepository userRepo;
    private OrderRepository orderRepo;
    private WishlistRepository wishlistRepo;
    private SavedPaymentMethodRepository paymentMethodRepo;
    private UserPreferencesRepository preferencesRepo;
    private NotificationRepository notificationRepo;
    private NewsletterSubscriptionRepository newsletterRepo;
    private AuditService auditService;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private AccountDeletionService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepository.class);
        orderRepo = mock(OrderRepository.class);
        wishlistRepo = mock(WishlistRepository.class);
        paymentMethodRepo = mock(SavedPaymentMethodRepository.class);
        preferencesRepo = mock(UserPreferencesRepository.class);
        notificationRepo = mock(NotificationRepository.class);
        newsletterRepo = mock(NewsletterSubscriptionRepository.class);
        auditService = mock(AuditService.class);
        service = new AccountDeletionService(userRepo, orderRepo, wishlistRepo, paymentMethodRepo,
                preferencesRepo, notificationRepo, newsletterRepo, encoder, auditService);

        user = new User();
        user.setId(42L);
        user.setFullName("Asha Kumar");
        user.setEmail("asha@example.com");
        user.setPhone("9876543210");
        user.setPassword(encoder.encode("Secret#123"));
        user.setRole(Role.CUSTOMER);
        user.setStatus("ACTIVE");
        user.setActive(true);
        user.setTokenVersion(3);
        when(orderRepo.findByUser(user)).thenReturn(List.of());
        when(preferencesRepo.findByUser(user)).thenReturn(Optional.empty());
        when(newsletterRepo.findByEmail(any())).thenReturn(Optional.empty());
    }

    private static Order order(String status) {
        Order o = new Order();
        o.setStatus(status);
        return o;
    }

    private static AccountDeletionException refused(Runnable r) {
        return assertThrows(AccountDeletionException.class, r::run);
    }

    @Test
    void deletesAccountAndAnonymisesTheUser() {
        when(orderRepo.findByUser(user)).thenReturn(List.of(order("DELIVERED"), order("CANCELLED")));

        service.deleteAccount(user, "Secret#123", "delete");

        assertEquals("Deleted user", user.getFullName());
        assertEquals("deleted-42@deleted.invalid", user.getEmail());
        assertNull(user.getPhone());
        assertNull(user.getPassword());
        assertEquals("DELETED", user.getStatus());
        assertFalse(user.isActive());
        assertEquals(4, user.getTokenVersion());
        verify(userRepo).save(user);
        verify(wishlistRepo).deleteAll(any());
        verify(paymentMethodRepo).deleteAll(any());
        verify(notificationRepo).deleteAll(any());
        verify(auditService).logAccountAction("ACCOUNT_DELETED_BY_USER", "user:42", 42L);
    }

    @Test
    void wrongPasswordIsRefusedAndNothingChanges() {
        AccountDeletionException e = refused(() -> service.deleteAccount(user, "nope", "DELETE"));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
        assertEquals("ACTIVE", user.getStatus());
        verify(userRepo, never()).save(any());
    }

    @Test
    void missingConfirmationWordIsRefused() {
        AccountDeletionException e = refused(() -> service.deleteAccount(user, "Secret#123", "yes"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        verify(userRepo, never()).save(any());
    }

    @Test
    void anOrderInProgressBlocksDeletion() {
        when(orderRepo.findByUser(user)).thenReturn(List.of(order("DELIVERED"), order("SHIPPED")));
        AccountDeletionException e = refused(() -> service.deleteAccount(user, "Secret#123", "DELETE"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verify(userRepo, never()).save(any());
    }

    @Test
    void sellersAndStaffAreSentToSupport() {
        user.setRole(Role.SELLER);
        AccountDeletionException e = refused(() -> service.deleteAccount(user, "Secret#123", "DELETE"));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        verify(userRepo, never()).save(any());
    }

    @Test
    void googleAccountWithoutPasswordNeedsOnlyTheConfirmationWord() {
        user.setPassword(null);
        service.deleteAccount(user, null, "DELETE");
        assertEquals("DELETED", user.getStatus());
    }
}
