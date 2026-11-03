package com.cauverystore.service;

import com.cauverystore.entities.GstConfiguration;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.entities.User;
import com.cauverystore.repository.GstConfigurationRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Whose GSTIN a sale goes out under. The store's own registration covers products the store
 * lists itself, and only those: a seller must never be invoiced under the store's number.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SellerGstinResolverTest {

    private static final String STORE_GSTIN = "33ABCDE1234F1Z5";

    @Mock private GstConfigurationRepository configRepo;
    @Mock private SellerRegistrationRepository sellerRegRepo;
    @Mock private UserRepository userRepo;

    private SellerGstinResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SellerGstinResolver(configRepo, sellerRegRepo, userRepo);
        when(configRepo.findBySellerId(anyLong())).thenReturn(Optional.empty());
        when(sellerRegRepo.findByUserId(anyLong())).thenReturn(Optional.empty());
        when(configRepo.findByIsActiveTrue()).thenReturn(List.of());
    }

    private void user(long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        when(userRepo.findById(id)).thenReturn(Optional.of(u));
    }

    private void storeRegistration(String gstin) {
        GstConfiguration store = new GstConfiguration();
        store.setGstin(gstin);
        store.setSellerId(null);
        when(configRepo.findByIsActiveTrue()).thenReturn(List.of(store));
    }

    @Test
    void sellersOwnConfigurationComesFirst() {
        GstConfiguration own = new GstConfiguration();
        own.setGstin("29PQRST5678K1Z2");
        when(configRepo.findBySellerId(5L)).thenReturn(Optional.of(own));
        storeRegistration(STORE_GSTIN);
        user(5L, Role.ADMIN);

        assertEquals(Optional.of("29PQRST5678K1Z2"), resolver.gstinOf(5L));
    }

    @Test
    void sellerRegistrationIsTheFallback() {
        SellerRegistration reg = new SellerRegistration();
        reg.setGstin(" 27LMNOP4321Q1Z9 ");
        when(sellerRegRepo.findByUserId(6L)).thenReturn(Optional.of(reg));
        user(6L, Role.SELLER);

        assertEquals(Optional.of("27LMNOP4321Q1Z9"), resolver.gstinOf(6L));
    }

    @Test
    void storeStaffProductsUseTheStoresOwnGstin() {
        storeRegistration(STORE_GSTIN);
        user(1L, Role.SUPER_ADMIN);
        user(2L, Role.ADMIN);

        assertEquals(Optional.of(STORE_GSTIN), resolver.gstinOf(1L));
        assertEquals(Optional.of(STORE_GSTIN), resolver.gstinOf(2L));
    }

    @Test
    void anUnregisteredSellerNeverGetsTheStoresGstin() {
        storeRegistration(STORE_GSTIN);
        user(9L, Role.SELLER);

        assertEquals(Optional.empty(), resolver.gstinOf(9L));
    }

    @Test
    void aSellersConfigurationIsNotMistakenForTheStores() {
        GstConfiguration sellersRow = new GstConfiguration();
        sellersRow.setGstin("29PQRST5678K1Z2");
        sellersRow.setSellerId(44L);
        when(configRepo.findByIsActiveTrue()).thenReturn(List.of(sellersRow));
        user(1L, Role.SUPER_ADMIN);

        assertEquals(Optional.empty(), resolver.gstinOf(1L));
    }

    @Test
    void nothingUntilTheStoreHasRegistered() {
        user(1L, Role.SUPER_ADMIN);

        assertEquals(Optional.empty(), resolver.gstinOf(1L));
        assertEquals(Optional.empty(), resolver.gstinOf(null));
    }
}
