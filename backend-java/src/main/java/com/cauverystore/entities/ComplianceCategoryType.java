package com.cauverystore.entities;

/**
 * Which set of category-specific mandatory fields applies to a category's products.
 * Stored on Category.complianceType by name; null or GENERAL means only the common fields.
 */
public enum ComplianceCategoryType {
    GENERAL("General"),
    FOOD("Food"),
    ELECTRONICS("Electronics"),
    COSMETICS("Cosmetics"),
    MEDICAL("Medical"),
    TOYS("Toys"),
    CLOTHING("Clothing");

    private final String label;

    ComplianceCategoryType(String label) { this.label = label; }

    public String getLabel() { return label; }

    /** Lenient lookup: unknown or blank values fall back to GENERAL. */
    public static ComplianceCategoryType from(String value) {
        if (value == null || value.isBlank()) return GENERAL;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return GENERAL;
        }
    }
}
