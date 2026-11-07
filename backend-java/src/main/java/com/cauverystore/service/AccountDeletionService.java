package com.cauverystore.service;

import com.cauverystore.entities.Notification;
import com.cauverystore.entities.Order;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.repository.NewsletterSubscriptionRepository;
import com.cauverystore.repository.NotificationRepository;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.SavedPaymentMethodRepository;
import com.cauverystore.repository.UserPreferencesRepository;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.repository.WishlistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Lets a shopper delete their own account (Google Play and the DPDP Act both expect this).
 *
 * <h2>Delete vs. keep</h2>
 *
 * Personal details and anything only the shopper needs are removed or blanked: name, e-mail,
 * phone, profile picture, saved payment methods, wishlist, preferences, notifications and the
 * newsletter sign-up. The row in {@code users} stays, anonymised and locked, because orders,
 * GST invoices, returns and refunds point at it and tax law requires those records to be kept.
 * The delivery addresses attached to past orders are kept for the same reason.
 *
 * <h2>Who and when</h2>
 *
 * Shopper accounts only: sellers and staff have compliance records and open commitments, so they
 * go through support. An account with an order still in progress cannot be deleted until the
 * order is finished or cancelled, so nobody loses a parcel or a refund.
 */
@Service
public class AccountDeletionService {

    /** Orders in one of these states are finished; anything else is still in progress. */
    static final Set<String> FINISHED_ORDER_STATUSES = Set.of("DELIVERED", "CANCELLED", "REFUNDED");

    /** What the shopper must type to confirm (the app and the website both show it). */
    public static final String CONFIRM_WORD = "DELETE";

    /** A refusal the controller turns into a clear message for the shopper. */
    public static class AccountDeletionException extends RuntimeException {
        private final HttpStatus status;

        public AccountDeletionException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }

    private final UserRepository userRepo;
    private final OrderRepository orderRepo;
    private final WishlistRepository wishlistRepo;
    private final SavedPaymentMethodRepository paymentMethodRepo;
    private final UserPreferencesRepository preferencesRepo;
    private final NotificationRepository notificationRepo;
    private final NewsletterSubscriptionRepository newsletterRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AccountDeletionService(UserRepository userRepo,
                                  OrderRepository orderRepo,
                                  WishlistRepository wishlistRepo,
                                  SavedPaymentMethodRepository paymentMethodRepo,
                                  UserPreferencesRepository preferencesRepo,
                                  NotificationRepository notificationRepo,
                                  NewsletterSubscriptionRepository newsletterRepo,
                                  PasswordEncoder passwordEncoder,
                                  AuditService auditService) {
        this.userRepo = userRepo;
        this.orderRepo = orderRepo;
        this.wishlistRepo = wishlistRepo;
        this.paymentMethodRepo = paymentMethodRepo;
        this.preferencesRepo = preferencesRepo;
        this.notificationRepo = notificationRepo;
        this.newsletterRepo = newsletterRepo;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public void deleteAccount(User user, String password, String confirm) {
        if (user.getRole() != Role.CUSTOMER) {
            throw new AccountDeletionException(HttpStatus.FORBIDDEN,
                    "Seller and staff accounts can't be deleted here. Please contact support.");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AccountDeletionException(HttpStatus.FORBIDDEN, "This account can't be deleted right now.");
        }
        if (confirm == null || !CONFIRM_WORD.equalsIgnoreCase(confirm.trim())) {
            throw new AccountDeletionException(HttpStatus.BAD_REQUEST,
                    "Type " + CONFIRM_WORD + " to confirm that you want to delete your account.");
        }
        // Accounts made with Google have no password to check; the typed confirmation is enough.
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
                throw new AccountDeletionException(HttpStatus.UNAUTHORIZED, "Your password is not correct.");
            }
        }
        List<Order> orders = orderRepo.findByUser(user);
        boolean hasOpenOrder = orders.stream().anyMatch(o -> !FINISHED_ORDER_STATUSES.contains(o.getStatus()));
        if (hasOpenOrder) {
            throw new AccountDeletionException(HttpStatus.CONFLICT,
                    "You still have an order in progress. You can delete your account once it is delivered or cancelled.");
        }

        String oldEmail = user.getEmail();
        Long id = user.getId();

        wishlistRepo.deleteAll(wishlistRepo.findByUser(user));
        paymentMethodRepo.deleteAll(paymentMethodRepo.findByUser(user));
        preferencesRepo.findByUser(user).ifPresent(preferencesRepo::delete);
        List<Notification> notifications = notificationRepo.findByUserIdOrderByCreatedAtDesc(id);
        notificationRepo.deleteAll(notifications);
        if (oldEmail != null) {
            newsletterRepo.findByEmail(oldEmail).ifPresent(newsletterRepo::delete);
        }

        user.setFullName("Deleted user");
        user.setUsername("deleted-" + id);
        user.setEmail("deleted-" + id + "@deleted.invalid");
        user.setPhone(null);
        user.setAddress(null);
        user.setProfilePicture(null);
        user.setPassword(null);
        user.setRefreshToken(null);
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setStatus("DELETED");
        user.setActive(false);
        // Signs out every phone and browser that still holds a token for this account.
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        userRepo.save(user);

        auditService.logAccountAction("ACCOUNT_DELETED_BY_USER", "user:" + id, id);
    }
}
