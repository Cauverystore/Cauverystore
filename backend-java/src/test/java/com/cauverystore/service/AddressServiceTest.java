package com.cauverystore.service;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.User;
import com.cauverystore.repository.AddressRepository;
import com.cauverystore.repository.OrderRepository;
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

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock private AddressRepository addressRepo;
    @Mock private OrderRepository orderRepo;

    @InjectMocks
    private AddressService addressService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setFullName("Test User");
        user.setEmail("test@test.com");
    }

    private Address savedRow(Long id, String line1, String street, String pincode, Boolean active) {
        Address a = new Address();
        a.setId(id);
        a.setLine1(line1);
        a.setStreet(street);
        a.setPincode(pincode);
        a.setActiveFlag(active);
        a.setUser(user);
        return a;
    }

    @Test
    void addAddressReusesExistingRowWithMatchingLine1AndPincode() {
        Address existing = savedRow(11L, "14 Gandhi Street", null, "600001", true);
        when(addressRepo.findActiveByUser(user)).thenReturn(List.of(existing));

        Address incoming = new Address();
        incoming.setFullName("Test User");
        incoming.setLine1("14 Gandhi Street");
        incoming.setCity("Chennai");
        incoming.setState("Tamil Nadu");
        incoming.setPincode("600001");

        Address result = addressService.addAddress(user, incoming);

        assertEquals(11L, result.getId());
        verify(addressRepo, never()).save(any());
    }

    @Test
    void addAddressReusesLegacyStreetOnlyRowWithNullActiveFlag() {
        // Pre-soft-delete rows: line1 NULL, street populated, active_flag NULL in the DB.
        Address legacy = savedRow(22L, null, "14 Gandhi Street", "600001", null);
        when(addressRepo.findActiveByUser(user)).thenReturn(List.of(legacy));

        Address incoming = new Address();
        incoming.setLine1("14 Gandhi Street");
        incoming.setPincode("600001");

        Address result = addressService.addAddress(user, incoming);

        assertEquals(22L, result.getId());
        verify(addressRepo, never()).save(any());
    }

    @Test
    void addAddressSavesWhenNoMatchExists() {
        when(addressRepo.findActiveByUser(user)).thenReturn(List.of());
        when(addressRepo.save(any(Address.class))).thenAnswer(i -> i.getArgument(0));

        Address incoming = new Address();
        incoming.setFullName("Test User");
        incoming.setLine1("99 New Road");
        incoming.setPincode("600050");

        Address result = addressService.addAddress(user, incoming);

        assertNull(result.getId());
        assertTrue(Boolean.TRUE.equals(result.getActiveFlag()));
        assertEquals("99 New Road", result.getStreet()); // normalized line1 -> street
        verify(addressRepo).save(incoming);
    }

    @Test
    void deleteAddressBlockedWhileLiveOrdersReferenceIt() {
        Address existing = savedRow(33L, "14 Gandhi Street", null, "600001", true);
        when(addressRepo.findById(33L)).thenReturn(java.util.Optional.of(existing));
        when(orderRepo.countByAddress_IdAndStatusNotIn(eq(33L), anyList())).thenReturn(2L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> addressService.deleteAddress(user, 33L));
        assertTrue(ex.getMessage().contains("active order"));
        verify(addressRepo, never()).save(existing);
    }

    @Test
    void deleteAddressSoftDeletesWhenOnlyTerminalOrdersReferenceIt() {
        Address existing = savedRow(44L, "14 Gandhi Street", null, "600001", true);
        when(addressRepo.findById(44L)).thenReturn(java.util.Optional.of(existing));
        when(orderRepo.countByAddress_IdAndStatusNotIn(eq(44L), anyList())).thenReturn(0L);

        addressService.deleteAddress(user, 44L);

        assertFalse(Boolean.TRUE.equals(existing.getActiveFlag()));
        verify(addressRepo).save(existing);
    }
}