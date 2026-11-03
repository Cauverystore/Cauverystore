package com.cauverystore.service;

import com.cauverystore.entities.GstConfiguration;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.repository.GstConfigurationRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.repository.UserRepository;

import java.util.Optional;

/**
 * Whose GSTIN a sale goes out under.
 *
 * In order: the seller's own GST configuration, then the GSTIN on their seller registration.
 * Products listed by store staff (ADMIN / SUPER_ADMIN) belong to the store itself, which has
 * no seller registration; for those the store's own registration (the GST configuration saved
 * in Admin → GST Compliance, the one with no seller attached) is used. Without that last step
 * every product the store lists itself is blocked at checkout and gets no tax invoice.
 *
 * A seller is never given the store's GSTIN: a sale by an unregistered seller must stay without
 * one rather than be invoiced under someone else's registration.
 */
public class SellerGstinResolver {

    private final GstConfigurationRepository configRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final UserRepository userRepo;

    public SellerGstinResolver(GstConfigurationRepository configRepo,
                               SellerRegistrationRepository sellerRegRepo,
                               UserRepository userRepo) {
        this.configRepo = configRepo;
        this.sellerRegRepo = sellerRegRepo;
        this.userRepo = userRepo;
    }

    public Optional<String> gstinOf(Long sellerId) {
        if (sellerId == null) return Optional.empty();
        Optional<String> own = configRepo.findBySellerId(sellerId)
                .map(GstConfiguration::getGstin)
                .filter(SellerGstinResolver::usable);
        if (own.isPresent()) return own.map(String::trim);
        Optional<String> registered = sellerRegRepo.findByUserId(sellerId)
                .map(SellerRegistration::getGstin)
                .filter(SellerGstinResolver::usable);
        if (registered.isPresent()) return registered.map(String::trim);
        return isStoreStaff(sellerId) ? storeGstin() : Optional.empty();
    }

    /** The store's own registration: the active GST configuration with no seller attached. */
    public Optional<String> storeGstin() {
        return configRepo.findByIsActiveTrue().stream()
                .filter(c -> c.getSellerId() == null)
                .map(GstConfiguration::getGstin)
                .filter(SellerGstinResolver::usable)
                .map(String::trim)
                .findFirst();
    }

    private boolean isStoreStaff(Long userId) {
        if (userRepo == null) return false;
        return userRepo.findById(userId)
                .map(u -> u.getRole() == Role.ADMIN || u.getRole() == Role.SUPER_ADMIN)
                .orElse(false);
    }

    private static boolean usable(String gstin) {
        return gstin != null && gstin.trim().length() >= 2;
    }
}
