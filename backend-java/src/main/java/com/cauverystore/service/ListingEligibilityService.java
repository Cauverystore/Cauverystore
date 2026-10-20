package com.cauverystore.service;

import com.cauverystore.entities.Brand;
import com.cauverystore.entities.Category;
import com.cauverystore.entities.SellerLicence;
import com.cauverystore.repository.BrandRepository;
import com.cauverystore.repository.CategoryRepository;
import com.cauverystore.repository.SellerBrandApplicationRepository;
import com.cauverystore.repository.SellerLicenceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Whether a seller may list a product in a category, under a brand.
 *
 * Two rules, both off until an admin turns them on, so nothing that sells today stops:
 * a category can require a licence (FSSAI for food, say), which the seller must hold checked
 * and in date; and a brand can be gated, which needs an approved brand application.
 *
 * Checked when a seller adds a product, or changes its category or brand. Existing listings
 * are left alone, and staff are never stopped.
 */
@Service
public class ListingEligibilityService {

    /** Why a seller may not list something; the message is written for the seller. */
    public static class NotAllowedException extends RuntimeException {
        public NotAllowedException(String message) { super(message); }
    }

    private final CategoryRepository categoryRepo;
    private final BrandRepository brandRepo;
    private final SellerLicenceRepository licenceRepo;
    private final SellerBrandApplicationRepository brandAppRepo;

    public ListingEligibilityService(CategoryRepository categoryRepo, BrandRepository brandRepo,
                                     SellerLicenceRepository licenceRepo, SellerBrandApplicationRepository brandAppRepo) {
        this.categoryRepo = categoryRepo;
        this.brandRepo = brandRepo;
        this.licenceRepo = licenceRepo;
        this.brandAppRepo = brandAppRepo;
    }

    public void check(Long sellerId, Category category, String brandName) {
        if (sellerId == null) return;
        checkCategory(sellerId, category);
        checkBrand(sellerId, brandName);
    }

    public void checkCategory(Long sellerId, Category category) {
        Category c = resolve(category);
        if (c == null || c.getRequiredLicence() == null || c.getRequiredLicence().isBlank()) return;
        String needed = c.getRequiredLicence();
        LocalDate today = LocalDate.now();
        boolean holds = licenceRepo.findBySellerIdAndStatusOrderByIdAsc(sellerId, "VERIFIED").stream()
                .filter(l -> needed.equals(l.getLicenceType()))
                .map(SellerLicence::getValidUntil)
                .anyMatch(until -> until == null || !until.isBefore(today));
        if (!holds) {
            String label = SellerLicenceService.TYPES.stream().filter(t -> t.code().equals(needed))
                    .map(SellerLicenceService.LicenceType::label).findFirst().orElse(needed);
            throw new NotAllowedException("Selling in " + c.getName() + " needs a checked " + label
                    + ". Add it under Store Profile > GST & Licences; once the team has checked it you can list here.");
        }
    }

    public void checkBrand(Long sellerId, String brandName) {
        if (brandName == null || brandName.isBlank()) return;
        Optional<Brand> brand = brandRepo.findFirstByNameIgnoreCase(brandName.trim());
        if (brand.isEmpty() || !Boolean.TRUE.equals(brand.get().getGated())) return;
        if (!brandAppRepo.existsBySellerIdAndBrandIdAndStatus(sellerId, brand.get().getId(), "APPROVED")) {
            throw new NotAllowedException(brand.get().getName() + " is a protected brand. Apply under Store Profile > Brands"
                    + " with proof that you own it or may sell it; you can list it once approved.");
        }
    }

    /** A category as stored: the request may carry only its id, or only its name. */
    private Category resolve(Category c) {
        if (c == null) return null;
        if (c.getId() != null) return categoryRepo.findById(c.getId()).orElse(c);
        if (c.getName() != null) return categoryRepo.findByName(c.getName()).orElse(c);
        return c;
    }
}
