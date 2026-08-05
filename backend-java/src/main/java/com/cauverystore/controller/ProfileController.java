package com.cauverystore.controller;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import com.cauverystore.service.AddressService;
import com.cauverystore.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class ProfileController {

    private final AuthService authService;
    private final AddressService addressService;
    private final SavedPaymentMethodRepository paymentMethodRepo;
    private final UserPreferencesRepository preferencesRepo;
    private final OrderRepository orderRepo;
    private final ProductReviewRepository reviewRepo;
    private final WishlistRepository wishlistRepo;
    private final UserRepository userRepo;

    private User getCurrentUser() {
        Long userId = authService.deriveUserId();
        return authService.getUserById(userId);
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<User> getProfile() {
        return ResponseEntity.ok(getCurrentUser());
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<User> updateProfile(@RequestBody User updated) {
        User user = getCurrentUser();
        if (updated.getFullName() != null) user.setFullName(updated.getFullName());
        if (updated.getPhone() != null) user.setPhone(updated.getPhone());
        if (updated.getEmail() != null) user.setEmail(updated.getEmail());
        if (updated.getAddress() != null) user.setAddress(updated.getAddress());
        if (updated.getProfilePicture() != null) user.setProfilePicture(updated.getProfilePicture());
        userRepo.save(user);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/profile/picture")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<User> updateProfilePicture(@RequestBody Map<String, String> body) {
        User user = getCurrentUser();
        user.setProfilePicture(body.get("profilePicture"));
        userRepo.save(user);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/addresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Address>> getAddresses() {
        return ResponseEntity.ok(addressService.getUserAddresses(getCurrentUser()));
    }

    @PostMapping("/addresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Address> addAddress(@RequestBody Address address) {
        return ResponseEntity.ok(addressService.addAddress(getCurrentUser(), address));
    }

    @PutMapping("/addresses/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Address> updateAddress(@PathVariable Long id, @RequestBody Address address) {
        return ResponseEntity.ok(addressService.updateAddress(getCurrentUser(), id, address));
    }

    @DeleteMapping("/addresses/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> deleteAddress(@PathVariable Long id) {
        addressService.deleteAddress(getCurrentUser(), id);
        return ResponseEntity.ok(Map.of("message", "Address deleted"));
    }

    @GetMapping("/addresses/default")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Address> getDefaultAddress() {
        Address addr = addressService.getDefaultAddress(getCurrentUser());
        return addr != null ? ResponseEntity.ok(addr) : ResponseEntity.noContent().build();
    }

    @GetMapping("/addresses/billing")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Address> getBillingAddress() {
        Address addr = addressService.getBillingAddress(getCurrentUser());
        return addr != null ? ResponseEntity.ok(addr) : ResponseEntity.noContent().build();
    }

    @GetMapping("/payment-methods")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SavedPaymentMethod>> getPaymentMethods() {
        return ResponseEntity.ok(paymentMethodRepo.findByUser(getCurrentUser()));
    }

    @PostMapping("/payment-methods")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SavedPaymentMethod> addPaymentMethod(@RequestBody SavedPaymentMethod method) {
        method.setUser(getCurrentUser());
        return ResponseEntity.ok(paymentMethodRepo.save(method));
    }

    @DeleteMapping("/payment-methods/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> deletePaymentMethod(@PathVariable Long id) {
        paymentMethodRepo.deleteByUserAndId(getCurrentUser(), id);
        return ResponseEntity.ok(Map.of("message", "Payment method removed"));
    }

    @GetMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserPreferences> getPreferences() {
        User user = getCurrentUser();
        return ResponseEntity.ok(preferencesRepo.findByUser(user)
                .orElseGet(() -> {
                    UserPreferences prefs = new UserPreferences();
                    prefs.setUser(user);
                    return preferencesRepo.save(prefs);
                }));
    }

    @PutMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserPreferences> updatePreferences(@RequestBody UserPreferences updated) {
        User user = getCurrentUser();
        UserPreferences prefs = preferencesRepo.findByUser(user).orElseGet(() -> {
            UserPreferences p = new UserPreferences();
            p.setUser(user);
            return p;
        });
        if (updated.getPreferredCategories() != null) prefs.setPreferredCategories(updated.getPreferredCategories());
        if (updated.getPreferredBrands() != null) prefs.setPreferredBrands(updated.getPreferredBrands());
        if (updated.getEmailNotifications() != null) prefs.setEmailNotifications(updated.getEmailNotifications());
        if (updated.getSmsNotifications() != null) prefs.setSmsNotifications(updated.getSmsNotifications());
        if (updated.getPushNotifications() != null) prefs.setPushNotifications(updated.getPushNotifications());
        if (updated.getTwoFactorEnabled() != null) prefs.setTwoFactorEnabled(updated.getTwoFactorEnabled());
        if (updated.getGdprConsent() != null) prefs.setGdprConsent(updated.getGdprConsent());
        if (updated.getCodPreference() != null) prefs.setCodPreference(updated.getCodPreference());
        if (updated.getSubscriptions() != null) prefs.setSubscriptions(updated.getSubscriptions());
        return ResponseEntity.ok(preferencesRepo.save(prefs));
    }

    @GetMapping("/orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Order>> getOrderHistory() {
        return ResponseEntity.ok(orderRepo.findByUserOrderByCreatedAtDesc(getCurrentUser()));
    }

    @GetMapping("/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductReview>> getMyReviews() {
        return ResponseEntity.ok(reviewRepo.findByUser(getCurrentUser()));
    }

    @GetMapping("/wishlist")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<WishlistItem>> getWishlist() {
        return ResponseEntity.ok(wishlistRepo.findByUser(getCurrentUser()));
    }

    @GetMapping("/notifications")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Notification>> getNotifications() {
        return ResponseEntity.ok(List.of());
    }
}
