package com.cauverystore.util;

import com.cauverystore.entities.Role;

/**
 * Builds the human-readable account ID shown to people: Sup001, Admin001, Seller001, Cust001.
 *
 * The number is padded to three digits and then simply grows (Cust999, Cust1000, Cust1001...),
 * so there is no upper limit. Pure on purpose: the counter that supplies the number lives
 * elsewhere, which keeps this testable without a database.
 */
public final class PublicIdFormatter {

    private PublicIdFormatter() {}

    public static String prefixFor(Role role) {
        if (role == null) return "Cust";
        return switch (role) {
            case SUPER_ADMIN -> "Sup";
            case ADMIN -> "Admin";
            case SELLER -> "Seller";
            case EXECUTIVE -> "Exec";
            case CUSTOMER -> "Cust";
        };
    }

    public static String format(Role role, long sequence) {
        if (sequence < 1) throw new IllegalArgumentException("sequence starts at 1");
        return prefixFor(role) + String.format("%03d", sequence);
    }
}
