package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.entities.SellerStore;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.repository.SellerStoreRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the product page may say about who sells an item.
 *
 * The endpoint is open to anyone, and the registration behind it holds a phone number, an
 * address and tax details - so the tests are as much about what never comes back as what does.
 */
class PublicSellerServiceTest {

    private final ProductRepository productRepo = mock(ProductRepository.class);
    private final SellerRegistrationRepository regRepo = mock(SellerRegistrationRepository.class);
    private final SellerStoreRepository storeRepo = mock(SellerStoreRepository.class);
    private final PublicSellerService service = new PublicSellerService(productRepo, regRepo, storeRepo);

    private void productSoldBy(Long sellerId) {
        Product p = new Product();
        p.setSellerId(sellerId);
        when(productRepo.findById(1L)).thenReturn(Optional.of(p));
    }

    private SellerRegistration registration(String status) {
        SellerRegistration reg = new SellerRegistration();
        reg.setBusinessName("Murugan Traders");
        reg.setCity("Erode");
        reg.setState("Tamil Nadu");
        reg.setBusinessPhone("9000000000");
        reg.setBusinessEmail("owner@example.com");
        reg.setBusinessAddress("12 Market Street");
        reg.setStatus(status);
        when(regRepo.findByUserId(7L)).thenReturn(Optional.of(reg));
        return reg;
    }

    @Test
    void namesAnApprovedSellerByBusinessNameAndTown() {
        productSoldBy(7L);
        registration("APPROVED");
        when(storeRepo.findBySellerId(7L)).thenReturn(Optional.empty());

        Map<String, Object> seller = service.sellerOfProduct(1L);

        assertEquals("Murugan Traders", seller.get("name"));
        assertEquals("Erode", seller.get("city"));
        assertEquals("Tamil Nadu", seller.get("state"));
    }

    @Test
    void neverReturnsContactDetails() {
        productSoldBy(7L);
        registration("APPROVED");
        when(storeRepo.findBySellerId(7L)).thenReturn(Optional.empty());

        assertEquals(Set.of("name", "city", "state"), service.sellerOfProduct(1L).keySet());
    }

    @Test
    void prefersTheShopNameTheSellerChose() {
        productSoldBy(7L);
        registration("APPROVED");
        SellerStore store = new SellerStore();
        store.setStoreName("Murugan Handlooms");
        when(storeRepo.findBySellerId(7L)).thenReturn(Optional.of(store));

        assertEquals("Murugan Handlooms", service.sellerOfProduct(1L).get("name"));
    }

    @Test
    void saysNothingAboutASellerStillBeingVetted() {
        productSoldBy(7L);
        registration("SUBMITTED");

        assertTrue(service.sellerOfProduct(1L).isEmpty());
    }

    @Test
    void saysNothingWhenTheProductHasNoRegisteredSeller() {
        // Listed by the marketplace's own staff: the seller id is an admin with no registration.
        productSoldBy(7L);
        when(regRepo.findByUserId(7L)).thenReturn(Optional.empty());

        assertTrue(service.sellerOfProduct(1L).isEmpty());
    }

    @Test
    void saysNothingForAProductThatDoesNotExist() {
        when(productRepo.findById(1L)).thenReturn(Optional.empty());

        assertTrue(service.sellerOfProduct(1L).isEmpty());
    }
}
