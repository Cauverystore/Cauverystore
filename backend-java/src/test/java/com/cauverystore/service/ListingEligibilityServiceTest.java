package com.cauverystore.service;

import com.cauverystore.entities.Brand;
import com.cauverystore.entities.Category;
import com.cauverystore.entities.SellerLicence;
import com.cauverystore.repository.BrandRepository;
import com.cauverystore.repository.CategoryRepository;
import com.cauverystore.repository.SellerBrandApplicationRepository;
import com.cauverystore.repository.SellerLicenceRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Licence-locked categories and protected brands: who may list what. */
class ListingEligibilityServiceTest {

    private final CategoryRepository categoryRepo = mock(CategoryRepository.class);
    private final BrandRepository brandRepo = mock(BrandRepository.class);
    private final SellerLicenceRepository licenceRepo = mock(SellerLicenceRepository.class);
    private final SellerBrandApplicationRepository appRepo = mock(SellerBrandApplicationRepository.class);
    private final ListingEligibilityService service = new ListingEligibilityService(categoryRepo, brandRepo, licenceRepo, appRepo);

    private Category food() {
        Category c = new Category();
        c.setName("Food");
        c.setRequiredLicence("FSSAI");
        when(categoryRepo.findByName("Food")).thenReturn(Optional.of(c));
        return c;
    }

    private SellerLicence fssai(LocalDate until) {
        SellerLicence l = new SellerLicence();
        l.setLicenceType("FSSAI");
        l.setValidUntil(until);
        return l;
    }

    @Test
    void anOpenCategoryNeedsNothing() {
        Category c = new Category();
        c.setName("Fashion");
        when(categoryRepo.findByName("Fashion")).thenReturn(Optional.of(c));
        assertDoesNotThrow(() -> service.check(4L, c, null));
    }

    @Test
    void aLockedCategoryNeedsACheckedLicence() {
        Category c = food();
        when(licenceRepo.findBySellerIdAndStatusOrderByIdAsc(4L, "VERIFIED")).thenReturn(List.of());
        var e = assertThrows(ListingEligibilityService.NotAllowedException.class, () -> service.check(4L, c, null));
        assertTrue(e.getMessage().contains("FSSAI"));
    }

    @Test
    void aCheckedInDateLicenceOpensIt() {
        Category c = food();
        when(licenceRepo.findBySellerIdAndStatusOrderByIdAsc(4L, "VERIFIED")).thenReturn(List.of(fssai(LocalDate.now().plusYears(1))));
        assertDoesNotThrow(() -> service.check(4L, c, null));
    }

    @Test
    void anExpiredLicenceDoesNot() {
        Category c = food();
        when(licenceRepo.findBySellerIdAndStatusOrderByIdAsc(4L, "VERIFIED")).thenReturn(List.of(fssai(LocalDate.now().minusDays(1))));
        assertThrows(ListingEligibilityService.NotAllowedException.class, () -> service.check(4L, c, null));
    }

    @Test
    void aProtectedBrandNeedsAnApprovedApplication() {
        Brand b = new Brand();
        b.setId(9L);
        b.setName("Aavin");
        b.setGated(true);
        when(brandRepo.findFirstByNameIgnoreCase("aavin")).thenReturn(Optional.of(b));
        when(appRepo.existsBySellerIdAndBrandIdAndStatus(4L, 9L, "APPROVED")).thenReturn(false);
        assertThrows(ListingEligibilityService.NotAllowedException.class, () -> service.checkBrand(4L, "aavin"));

        when(appRepo.existsBySellerIdAndBrandIdAndStatus(4L, 9L, "APPROVED")).thenReturn(true);
        assertDoesNotThrow(() -> service.checkBrand(4L, "aavin"));
    }

    @Test
    void anUnprotectedOrUnknownBrandIsOpen() {
        Brand b = new Brand();
        b.setId(10L);
        b.setName("Plain");
        when(brandRepo.findFirstByNameIgnoreCase("Plain")).thenReturn(Optional.of(b));
        when(brandRepo.findFirstByNameIgnoreCase("Nobody")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.checkBrand(4L, "Plain"));
        assertDoesNotThrow(() -> service.checkBrand(4L, "Nobody"));
    }
}
