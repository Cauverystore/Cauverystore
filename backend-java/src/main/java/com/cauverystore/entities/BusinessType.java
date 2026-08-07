package com.cauverystore.entities;

import java.util.Arrays;
import java.util.Optional;

/**
 * What the seller's trade is - how they sell, rather than what they sell.
 *
 * Not to be confused with the constitution of the business (proprietorship, partnership,
 * company), which is a different question and is kept separately on SellerRegistration. That
 * distinction matters: this column used to hold the constitution, and the two were merged by
 * accident when the field was first added.
 *
 * Stored on SellerRegistration as a string rather than as a mapped enum, so a row written
 * before this list existed still loads instead of blowing up on read. Writes are validated
 * against it.
 */
public enum BusinessType {

    RETAIL("Retail"),
    WHOLESALE("Wholesale"),
    DISTRIBUTOR("Distributor"),
    SERVICE("Service"),
    MANUFACTURING("Manufacturing"),
    OTHERS("Others");

    private final String label;

    BusinessType(String label) {
        this.label = label;
    }

    /** The wording shown to a seller and stored on the record. */
    public String getLabel() {
        return label;
    }

    /** Matches on the label or the enum name, ignoring case and surrounding space. */
    public static Optional<BusinessType> from(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String cleaned = raw.trim();
        return Arrays.stream(values())
                .filter(t -> t.label.equalsIgnoreCase(cleaned) || t.name().equalsIgnoreCase(cleaned))
                .findFirst();
    }

    public static boolean isValid(String raw) {
        return from(raw).isPresent();
    }

    public static String[] labels() {
        return Arrays.stream(values()).map(BusinessType::getLabel).toArray(String[]::new);
    }
}
