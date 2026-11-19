package com.cauverystore.service;

import java.util.List;

/**
 * The store's recommended category list. An admin adds the missing ones with one click on the
 * Categories page; nothing is created on startup, so a category the admin deletes stays deleted.
 *
 * Goods that need a licence or a compulsory quality certificate (BIS / ISI, hallmarking) get a
 * category of their own, so that requirement can be enforced on sellers without blocking
 * everyone else. The licence codes match SellerLicenceService.TYPES.
 */
public final class StandardCategories {

    public record Entry(String name, String description, String requiredLicence) {}

    private StandardCategories() {}

    public static final List<Entry> ALL = List.of(
            // Open to every seller
            new Entry("Electronics", "Smartphones, laptops, and gadgets", null),
            new Entry("Fashion", "Clothing, shoes, and accessories", null),
            new Entry("Home & Kitchen", "Furniture, appliances, and decor", null),
            new Entry("Books", "Fiction, non-fiction, and educational", null),
            new Entry("Sports", "Fitness equipment and sportswear", null),
            new Entry("Fashion Jewellery & Accessories",
                    "Imitation and fashion jewellery, hair accessories, belts, sunglasses and other accessories. Gold and silver go under Gold & Silver Jewellery (Hallmarked).", null),
            new Entry("Beauty & Personal Care",
                    "Skincare, haircare, makeup, fragrances, men's grooming and bath products. Medicinal products go under Medicines & Pharmacy.", null),
            new Entry("Baby Care",
                    "Diapers, baby clothing, feeding bottles, baby bath and skincare, strollers and carriers. Baby food goes under Baby Food & Infant Nutrition.", null),
            new Entry("Stationery & Office Supplies",
                    "Notebooks, pens, files, art and craft supplies, school and office essentials.", null),
            new Entry("Luggage & Bags",
                    "Suitcases, backpacks, travel bags, handbags, wallets and laptop bags.", null),
            new Entry("Automotive Accessories",
                    "Car and bike accessories, covers, car care and mobile holders. Helmets go under Helmets & Vehicle Safety.", null),
            new Entry("Tools & Hardware",
                    "Hand tools, power tools, fasteners, plumbing and paint supplies. Wires, switches and bulbs go under Electrical & Lighting.", null),
            new Entry("Garden & Outdoors",
                    "Plants, pots, planters, gardening tools and outdoor decor. Seeds and fertilisers go under Seeds, Fertilisers & Pesticides.", null),
            new Entry("Pet Supplies",
                    "Pet food, treats, collars, leashes, beds, toys and grooming for pets.", null),
            new Entry("Handloom & Handicrafts",
                    "Handloom sarees and fabrics, Kanchipuram silk, Coimbatore cotton, brass and bronze ware, wooden crafts, pottery and GI-tagged products.", null),
            new Entry("Pooja & Spiritual",
                    "Pooja items, idols, agarbatti, camphor, brass lamps, rudraksha and religious books.", null),
            new Entry("Musical Instruments",
                    "Guitars, keyboards, veena, mridangam, flutes, drums and music accessories.", null),
            new Entry("Gifts & Party Supplies",
                    "Gift sets, hampers, return gifts, decorations and party supplies.", null),

            // Food - FSSAI licence or registration
            new Entry("Grocery & Staples",
                    "Rice, dal, atta, spices and masalas, oils, ghee, dry fruits and organic staples. Sellers need an FSSAI licence.", "FSSAI"),
            new Entry("Snacks, Sweets & Beverages",
                    "Packaged snacks, sweets, chocolates, tea, coffee, juices and health drinks. Sellers need an FSSAI licence.", "FSSAI"),
            new Entry("Health Supplements & Nutraceuticals",
                    "Vitamins, protein powders, herbal supplements and other nutraceuticals regulated as food. Sellers need an FSSAI licence.", "FSSAI"),
            new Entry("Baby Food & Infant Nutrition",
                    "Infant formula, cereals and baby food. Sellers need an FSSAI licence.", "FSSAI"),

            // Medicines and medical devices - drug licence
            new Entry("Medicines & Pharmacy",
                    "Allopathic medicines and OTC healthcare products. Sellers need a drug licence.", "DRUG"),
            new Entry("Ayurveda, Siddha & Unani Medicines",
                    "Classical and proprietary AYUSH medicines. Sellers need a drug licence for AYUSH medicines.", "DRUG"),
            new Entry("Medical Devices & Equipment",
                    "BP monitors, glucometers, thermometers, nebulisers and mobility aids. Sellers need a drug licence or medical device registration.", "DRUG"),

            // Compulsory quality certification - BIS (ISI mark, CRS registration, hallmarking)
            new Entry("Toys & Games",
                    "Toys, board games, puzzles and ride-ons. Toys must carry the BIS (ISI) mark.", "BIS"),
            new Entry("Home Appliances",
                    "Mixer grinders, wet grinders, kettles, irons, fans, water heaters, induction stoves and large appliances. Many need BIS certification.", "BIS"),
            new Entry("Electrical & Lighting",
                    "Wires, cables, switches, plugs, LED bulbs and lights. These need BIS certification.", "BIS"),
            new Entry("Chargers, Power Banks & Batteries",
                    "Mobile chargers, adapters, power banks and batteries. These need BIS registration.", "BIS"),
            new Entry("Helmets & Vehicle Safety",
                    "Two-wheeler helmets and vehicle safety items. Helmets must carry the ISI mark.", "BIS"),
            new Entry("Pressure Cookers & Gas Stoves",
                    "Pressure cookers, gas stoves and LPG accessories. These must carry the ISI mark.", "BIS"),
            new Entry("Gold & Silver Jewellery (Hallmarked)",
                    "Gold and silver jewellery and coins. Gold jewellery must be BIS hallmarked.", "BIS"),
            new Entry("Footwear",
                    "Shoes, sandals, slippers and chappals. Many types now need BIS certification.", "BIS"),

            // Weights and measures - Legal Metrology
            new Entry("Weighing Scales & Measuring Instruments",
                    "Kitchen scales, body weighing scales, commercial scales and measuring tapes. Sellers need Legal Metrology approval.", "LEGAL_METROLOGY"),

            // Agricultural inputs - dealer licences
            new Entry("Seeds, Fertilisers & Pesticides",
                    "Seeds, fertilisers, pesticides and plant protection. Sellers need the matching seed, fertiliser or insecticide dealer licence.", "OTHER"),

            // Catch-all
            new Entry("Others",
                    "Products not yet placed in a category. Bulk uploads with an empty or unknown category land here.", null)
    );
}
