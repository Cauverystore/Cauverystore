package com.cauverystore.entities;

import java.util.Arrays;
import java.util.Optional;

/**
 * The trade the seller is in - Kirana, Pharmacy, Jewellery and so on.
 *
 * Answers a different question from BusinessType, which is how they sell. A pharmacy can be
 * retail or wholesale; a Kirana can be either. Both are needed to segment GST reporting in any
 * useful way, which is the point of collecting them.
 *
 * Stored as a string rather than a mapped enum so that a row written before this list existed
 * still loads. Writes are validated against it.
 */
public enum BusinessCategory {

    ACCOUNTING_CA("Accounting & CA"),
    INTERIOR_DESIGNER("Interior Designer"),
    AUTOMOBILES_AUTO_PARTS("Automobiles / Auto Parts"),
    SALON_SPA("Salon & Spa"),
    LIQUOR_STORE("Liquor Store"),
    BOOK_STATIONERY_STORE("Book / Stationery Store"),
    CONSTRUCTION_MATERIALS("Construction Materials & Equipment"),
    REPAIRING_PLUMBING_ELECTRICIAN("Repairing / Plumbing / Electrician"),
    CHEMICALS_FERTILIZERS("Chemicals & Fertilizers"),
    COMPUTER_EQUIPMENT_SOFTWARE("Computer Equipment & Software"),
    ELECTRICAL_ELECTRONICS("Electrical & Electronics Equipment"),
    FASHION_ACCESSORIES_COSMETICS("Fashion Accessories / Cosmetics"),
    TAILORING_BOUTIQUE("Tailoring / Boutique"),
    FRUIT_VEGETABLE("Fruit & Vegetable"),
    KIRANA_GENERAL_MERCHANT("Kirana / General Merchant"),
    FMCG_PRODUCTS("FMCG Products"),
    DAIRY_POULTRY("Dairy Farm Products / Poultry"),
    FURNITURE("Furniture"),
    GARMENT_FASHION_HOSIERY("Garment / Fashion & Hosiery"),
    JEWELLERY_GEMS("Jewellery & Gems"),
    PHARMACY_MEDICAL("Pharmacy / Medical"),
    HARDWARE_STORE("Hardware Store"),
    INDUSTRIAL_MACHINERY("Industrial Machinery & Equipment"),
    MOBILE_ACCESSORIES("Mobile & Accessories"),
    NURSERY_PLANTS("Nursery / Plants"),
    PETROLEUM_BULK_STATIONS("Petroleum Bulk Stations & Terminals / Petrol"),
    RESTAURANT_HOTEL("Restaurant / Hotel"),
    FOOTWEAR("Footwear"),
    PAPER_PRODUCTS("Paper & Paper Products"),
    SWEET_SHOP_BAKERY("Sweet Shop / Bakery"),
    GIFTS_TOYS("Gifts & Toys"),
    LAUNDRY_DRY_CLEAN("Laundry / Washing / Dry Clean"),
    COACHING_TRAINING("Coaching & Training"),
    RENTING_LEASING("Renting & Leasing"),
    FITNESS_CENTER("Fitness Center"),
    OIL_GAS("Oil & Gas"),
    REAL_ESTATE("Real Estate"),
    NGO_CHARITABLE_TRUST("NGO & Charitable Trust"),
    TOURS_TRAVELS("Tours & Travels"),
    OTHERS("Others");

    private final String label;

    BusinessCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Matches on the label or the enum name, ignoring case, spacing around separators and
     * surrounding space - so "Automobiles/Auto Parts" and "Automobiles / Auto Parts" both
     * resolve rather than one of them being silently rejected on save.
     */
    public static Optional<BusinessCategory> from(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String cleaned = normalise(raw);
        return Arrays.stream(values())
                .filter(c -> normalise(c.label).equals(cleaned) || c.name().equalsIgnoreCase(raw.trim()))
                .findFirst();
    }

    private static String normalise(String s) {
        return s.trim().toLowerCase().replaceAll("\\s*/\\s*", "/").replaceAll("\\s+", " ");
    }

    public static boolean isValid(String raw) {
        return from(raw).isPresent();
    }

    public static String[] labels() {
        return Arrays.stream(values()).map(BusinessCategory::getLabel).toArray(String[]::new);
    }
}
