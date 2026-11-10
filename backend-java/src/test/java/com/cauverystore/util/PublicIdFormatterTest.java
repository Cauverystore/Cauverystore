package com.cauverystore.util;

import com.cauverystore.entities.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicIdFormatterTest {

    @Test
    void firstIdForEachRoleUsesTheAgreedPrefix() {
        assertEquals("Sup001", PublicIdFormatter.format(Role.SUPER_ADMIN, 1));
        assertEquals("Admin001", PublicIdFormatter.format(Role.ADMIN, 1));
        assertEquals("Seller001", PublicIdFormatter.format(Role.SELLER, 1));
        assertEquals("Cust001", PublicIdFormatter.format(Role.CUSTOMER, 1));
    }

    @Test
    void numbersAreZeroPaddedToThreeDigits() {
        assertEquals("Cust042", PublicIdFormatter.format(Role.CUSTOMER, 42));
        assertEquals("Cust999", PublicIdFormatter.format(Role.CUSTOMER, 999));
    }

    @Test
    void thereIsNoUpperLimit() {
        assertEquals("Cust1000", PublicIdFormatter.format(Role.CUSTOMER, 1000));
        assertEquals("Cust1234567", PublicIdFormatter.format(Role.CUSTOMER, 1234567));
        assertEquals("Seller9999999999", PublicIdFormatter.format(Role.SELLER, 9999999999L));
    }

    @Test
    void anUnknownRoleFallsBackToCustomer() {
        assertEquals("Cust005", PublicIdFormatter.format(null, 5));
    }

    @Test
    void executiveHasItsOwnPrefix() {
        assertEquals("Exec001", PublicIdFormatter.format(Role.EXECUTIVE, 1));
    }

    @Test
    void zeroOrNegativeSequenceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PublicIdFormatter.format(Role.CUSTOMER, 0));
    }
}
