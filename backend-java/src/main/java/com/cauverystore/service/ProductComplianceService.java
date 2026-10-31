package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.GstConfigurationRepository;
import com.cauverystore.repository.ProductComplianceRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.validation.ComplianceFormats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The product compliance engine: decides whether a listing carries everything it must before
 * shoppers may see it, and scores how complete it is.
 *
 * Score is the share of applicable checks that pass, rounded down, so any gap at all keeps it
 * below 100. 100 may be published; 90-99 may be published with a warning; below 90 is blocked.
 * Some gaps block on their own whatever the score: GST and HSN, MRP and price, manufacturer,
 * country of origin, Legal Metrology quantities, the seller's GSTIN, and the fields the
 * product's category type requires.
 *
 * It reuses the existing rules rather than restating them: the GST rate is resolved by
 * HsnClassificationService exactly as at publish time, a licence-locked category is checked by
 * ListingEligibilityService, and the seller's GSTIN is found the way checkout finds it (GST
 * configuration first, then registration).
 *
 * Live products are scored, not hidden. The gate only acts when a product is about to become
 * visible to shoppers, unless an admin turns on {@link #SETTING_ENFORCE_LIVE}.
 */
@Service
public class ProductComplianceService {

    /** Platform setting: "off" lets non-compliant products be published (emergency switch). Default on. */
    public static final String SETTING_GATE = "compliance.publish_gate";
    /** Platform setting: "on" also holds a live product that fails the check the next time it is edited. Default off. */
    public static final String SETTING_ENFORCE_LIVE = "compliance.enforce_live";

    public static final int PUBLISH_THRESHOLD = 90;

    public record Issue(String field, String label, String section, boolean critical, String problem, String message) {}

    public record Result(Long productId, String categoryType, boolean compliant, int score, String band,
                         boolean publishable, List<String> missingFields, List<Issue> issues,
                         int totalChecks, LocalDateTime checkedAt) {}

    /** Thrown when something tries to make a product visible that the engine says may not be. */
    public static class ComplianceBlockedException extends RuntimeException {
        private final transient Result result;
        public ComplianceBlockedException(Result result) {
            super(describe(result));
            this.result = result;
        }
        public Result getResult() { return result; }

        private static String describe(Result r) {
            List<String> critical = r.issues().stream().filter(Issue::critical).map(Issue::label).distinct().toList();
            String gaps = critical.isEmpty() ? String.join(", ", r.issues().stream().map(Issue::label).distinct().limit(8).toList())
                    : String.join(", ", critical.stream().limit(8).toList());
            return "This product cannot be published yet: its compliance score is " + r.score()
                    + (critical.isEmpty() ? " (" + PUBLISH_THRESHOLD + " needed)" : "") + ". Missing or invalid: " + gaps + ".";
        }
    }

    private final ProductRepository productRepo;
    private final ProductComplianceRepository complianceRepo;
    private final HsnClassificationService hsnClassification;
    private final ListingEligibilityService listingEligibility;
    private final GstConfigurationRepository gstConfigRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final PlatformSettingsService settings;

    public ProductComplianceService(ProductRepository productRepo, ProductComplianceRepository complianceRepo,
                                    HsnClassificationService hsnClassification, ListingEligibilityService listingEligibility,
                                    GstConfigurationRepository gstConfigRepo, SellerRegistrationRepository sellerRegRepo,
                                    PlatformSettingsService settings) {
        this.productRepo = productRepo;
        this.complianceRepo = complianceRepo;
        this.hsnClassification = hsnClassification;
        this.listingEligibility = listingEligibility;
        this.gstConfigRepo = gstConfigRepo;
        this.sellerRegRepo = sellerRegRepo;
        this.settings = settings;
    }

    // ------------------------------------------------------------------ settings and visibility

    public boolean gateEnabled() {
        PlatformSetting s = settings.getSetting(SETTING_GATE);
        return s == null || s.getSettingValue() == null || !"off".equalsIgnoreCase(s.getSettingValue().trim());
    }

    public boolean enforceLive() {
        PlatformSetting s = settings.getSetting(SETTING_ENFORCE_LIVE);
        return s != null && s.getSettingValue() != null && "on".equalsIgnoreCase(s.getSettingValue().trim());
    }

    /** The storefront's own rule (see ProductRepository queries): active, and approved or never reviewed. */
    public static boolean visibleToShoppers(Product p) {
        return p != null && p.isActive() && ProductService.isApprovedForShoppers(p);
    }

    // ------------------------------------------------------------------ records

    public Optional<ProductCompliance> findRecord(Long productId) {
        return productId == null ? Optional.empty() : complianceRepo.findByProductIdAndDeletedFlagFalse(productId);
    }

    /**
     * The record for a product, created on first use. A product that is already live when its
     * record is first made starts at PUBLISHED, so existing listings keep their place.
     */
    @Transactional
    public ProductCompliance recordFor(Product product) {
        return findRecord(product.getId()).orElseGet(() -> {
            ProductCompliance c = new ProductCompliance();
            c.setProductId(product.getId());
            c.setStatus(visibleToShoppers(product) ? ProductWorkflowStatus.PUBLISHED.name() : ProductWorkflowStatus.DRAFT.name());
            return complianceRepo.save(c);
        });
    }

    // ------------------------------------------------------------------ the check

    /** Runs the check and stores its result on the product's record. */
    @Transactional
    public Result check(Long productId) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        ProductCompliance record = recordFor(product);
        Result result = evaluate(product, record);
        store(record, result);
        return result;
    }

    void store(ProductCompliance record, Result result) {
        record.setComplianceScore(result.score());
        record.setMissingFields(String.join(",", result.missingFields()));
        record.setPublishable(result.publishable());
        record.setLastCheckedAt(result.checkedAt());
        complianceRepo.save(record);
    }

    /** The check without saving anything. Either argument's fields may be blank; a null record counts as empty. */
    public Result evaluate(Product p, ProductCompliance c) {
        if (c == null) c = new ProductCompliance();
        ComplianceCategoryType type = typeOf(p);
        Checks k = new Checks();

        // Basic information
        k.require("name", "Product name", "basic", false, has(p.getName()));
        k.require("description", "Description", "basic", false, has(p.getDescription()));
        k.require("category", "Category", "basic", true, p.getCategory() != null);
        k.require("brand", "Brand", "basic", false, has(p.getBrand()));
        k.require("sku", "SKU", "basic", false, has(p.getSku()));

        // Pricing
        k.require("mrp", "MRP", "pricing", true, p.getMrp() != null && p.getMrp() > 0);
        k.require("price", "Selling price", "pricing", true, p.getPrice() != null && p.getPrice() > 0);
        if (p.getMrp() != null && p.getMrp() > 0) {
            double selling = p.getOfferPrice() != null && p.getOfferPrice() > 0 ? p.getOfferPrice()
                    : (p.getPrice() != null ? p.getPrice() : 0);
            k.valid("price", "Selling price", "pricing", true, selling <= p.getMrp() + 0.0001,
                    "The selling price is above the MRP.");
        }

        // GST and HSN
        boolean hsnPresent = has(p.getHsnCode());
        k.require("hsnCode", "HSN code", "gst", true, hsnPresent);
        if (hsnPresent) {
            boolean format = ComplianceFormats.isValidHsn(p.getHsnCode());
            k.valid("hsnCode", "HSN code", "gst", true, format, "An HSN code has 4, 6 or 8 digits.");
            if (format) {
                k.valid("hsnCode", "HSN code", "gst", true, hsnClassification.describeCode(p.getHsnCode()).isPresent(),
                        "This HSN code is not in the official GSTN master.");
            }
        }
        String gstProblem = hsnPresent ? gstRateProblem(p) : "No HSN code, so no GST rate.";
        k.require("gstRate", "GST rate", "gst", true, gstProblem == null);
        if (gstProblem != null && hsnPresent) k.note("gstRate", gstProblem);

        // Inventory
        k.require("stock", "Stock quantity", "inventory", false, p.getStock() != null && p.getStock() >= 0);
        k.require("minOrderQty", "Minimum order quantity", "inventory", false, c.getMinOrderQty() != null);
        k.require("maxOrderQty", "Maximum order quantity", "inventory", false, c.getMaxOrderQty() != null);
        if (c.getMinOrderQty() != null && c.getMaxOrderQty() != null) {
            k.valid("maxOrderQty", "Maximum order quantity", "inventory", false, c.getMaxOrderQty() >= c.getMinOrderQty(),
                    "The maximum order quantity is below the minimum.");
        }

        // Legal Metrology
        k.require("netQuantity", "Net quantity", "legalMetrology", true, c.getNetQuantity() != null && c.getNetQuantity().signum() > 0);
        k.require("grossQuantity", "Gross quantity", "legalMetrology", true, c.getGrossQuantity() != null && c.getGrossQuantity().signum() > 0);
        k.require("quantityUnit", "Unit of quantity", "legalMetrology", true, has(c.getQuantityUnit()));
        if (c.getNetQuantity() != null && c.getGrossQuantity() != null) {
            k.valid("grossQuantity", "Gross quantity", "legalMetrology", false,
                    c.getGrossQuantity().compareTo(c.getNetQuantity()) >= 0, "Gross quantity is less than net quantity.");
        }

        // Manufacturer, packer, importer
        String manufacturer = has(c.getManufacturerName()) ? c.getManufacturerName() : p.getManufacturer();
        k.require("manufacturerName", "Manufacturer name", "parties", true, has(manufacturer));
        k.require("manufacturerAddress", "Manufacturer address", "parties", true, has(c.getManufacturerAddress()));
        boolean packerIsManufacturer = Boolean.TRUE.equals(c.getPackerSameAsManufacturer());
        k.require("packerName", "Packer name", "parties", false, packerIsManufacturer || has(c.getPackerName()));
        k.require("packerAddress", "Packer address", "parties", false, packerIsManufacturer || has(c.getPackerAddress()));

        // Origin
        k.require("countryOfOrigin", "Country of origin", "origin", true, has(p.getCountryOfOrigin()));
        if (has(p.getCountryOfOrigin())) {
            if (isIndia(p.getCountryOfOrigin())) {
                k.require("stateOfOrigin", "State of origin", "origin", false, has(c.getStateOfOrigin()));
            } else {
                k.require("importerName", "Importer name", "parties", false, has(c.getImporterName()));
                k.require("importerAddress", "Importer address", "parties", false, has(c.getImporterAddress()));
            }
        }

        // Customer care
        k.require("customerCarePhone", "Customer care phone", "customerCare", false, has(c.getCustomerCarePhone()));
        k.require("customerCareEmail", "Customer care email", "customerCare", false, has(c.getCustomerCareEmail()));

        // Dates and traceability
        LocalDate today = LocalDate.now();
        k.require("mfgDate", "Manufacturing date", "dates", false, c.getMfgDate() != null);
        if (c.getMfgDate() != null) {
            k.valid("mfgDate", "Manufacturing date", "dates", false, !c.getMfgDate().isAfter(today), "The manufacturing date is in the future.");
        }
        boolean batchCritical = type == ComplianceCategoryType.COSMETICS;
        k.require("batchNumber", "Batch or lot number", "dates", batchCritical, has(c.getBatchNumber()) || has(c.getLotNumber()));
        if (c.getExpiryDate() != null) {
            k.valid("expiryDate", "Expiry date", "dates", true, !c.getExpiryDate().isBefore(today), "The expiry date has passed.");
        }

        // Seller
        k.require("sellerId", "Seller", "seller", true, p.getSellerId() != null);
        if (p.getSellerId() != null) {
            SellerIdentity s = sellerIdentity(p.getSellerId());
            k.require("sellerGstin", "Seller GSTIN", "seller", true, has(s.gstin()));
            if (has(s.gstin())) {
                k.valid("sellerGstin", "Seller GSTIN", "seller", true, ComplianceFormats.isValidGstin(s.gstin()),
                        "The seller's GSTIN fails its format or check-digit test.");
            }
            k.require("sellerBusinessName", "Seller business name", "seller", false, has(s.businessName()));
            k.require("sellerBusinessAddress", "Seller business address", "seller", false, has(s.businessAddress()));
            if (p.getCategory() != null) {
                String licenceProblem = null;
                try {
                    listingEligibility.checkCategory(p.getSellerId(), p.getCategory());
                } catch (ListingEligibilityService.NotAllowedException e) {
                    licenceProblem = e.getMessage();
                }
                k.require("sellerLicence", "Seller licence for this category", "seller", true, licenceProblem == null);
                if (licenceProblem != null) k.note("sellerLicence", licenceProblem);
            }
        }

        categoryChecks(k, type, p, c);

        return k.result(p.getId(), type);
    }

    private void categoryChecks(Checks k, ComplianceCategoryType type, Product p, ProductCompliance c) {
        String s = "category";
        switch (type) {
            case FOOD -> {
                k.require("fssaiLicenceNumber", "FSSAI licence number", s, true, has(c.getFssaiLicenceNumber()));
                if (has(c.getFssaiLicenceNumber())) {
                    k.valid("fssaiLicenceNumber", "FSSAI licence number", s, true, ComplianceFormats.isValidFssai(c.getFssaiLicenceNumber()),
                            "An FSSAI licence or registration number has 14 digits.");
                }
                k.require("ingredients", "Ingredients", s, true, has(c.getIngredients()));
                k.require("nutritionInfo", "Nutrition information", s, true, has(c.getNutritionInfo()));
                k.require("allergens", "Allergen information", s, true, has(c.getAllergens()));
                k.require("foodPreference", "Veg / non-veg mark", s, true, has(c.getFoodPreference()));
                k.require("storageInstructions", "Storage instructions", s, true, has(c.getStorageInstructions()));
                k.require("bestBeforeDate", "Best-before or expiry date", s, true, c.getBestBeforeDate() != null || c.getExpiryDate() != null);
            }
            case ELECTRONICS -> {
                k.require("warranty", "Warranty", s, true, has(p.getWarranty()));
                k.require("serviceCentreDetails", "Service centre details", s, true, has(c.getServiceCentreDetails()));
                bis(k, c, s);
                k.require("voltage", "Voltage", s, true, has(c.getVoltage()));
                k.require("powerRating", "Power", s, true, has(c.getPowerRating()));
            }
            case COSMETICS -> {
                k.require("ingredients", "Ingredients", s, true, has(c.getIngredients()));
                k.require("cosmeticLicenceNumber", "Manufacturing licence number", s, true, has(c.getCosmeticLicenceNumber()));
                k.require("expiryDate", "Expiry date", s, true, c.getExpiryDate() != null);
            }
            case MEDICAL -> {
                k.require("cdscoRegistrationNumber", "CDSCO registration number", s, true, has(c.getCdscoRegistrationNumber()));
                if (has(c.getCdscoRegistrationNumber())) {
                    k.valid("cdscoRegistrationNumber", "CDSCO registration number", s, true,
                            ComplianceFormats.isValidCdsco(c.getCdscoRegistrationNumber()), "This does not look like a CDSCO registration number.");
                }
                k.require("prescriptionRequired", "Prescription required (yes/no)", s, true, c.getPrescriptionRequired() != null);
                k.require("warnings", "Warnings", s, true, has(c.getWarnings()));
                k.require("disclaimer", "Disclaimer", s, true, has(c.getDisclaimer()));
            }
            case TOYS -> {
                bis(k, c, s);
                k.require("ageGroup", "Age group", s, true, has(c.getAgeGroup()));
                k.require("safetyWarning", "Safety warning", s, true, has(c.getSafetyWarning()));
            }
            case CLOTHING -> {
                k.require("fabric", "Fabric", s, true, has(c.getFabric()));
                k.require("washCare", "Wash care", s, true, has(c.getWashCare()));
                k.require("sizeChart", "Size chart", s, true, has(c.getSizeChart()));
            }
            case GENERAL -> { }
        }
    }

    private void bis(Checks k, ProductCompliance c, String section) {
        k.require("bisNumber", "BIS number", section, true, has(c.getBisNumber()));
        if (has(c.getBisNumber())) {
            k.valid("bisNumber", "BIS number", section, true, ComplianceFormats.isValidBis(c.getBisNumber()),
                    "A BIS number looks like R-12345678 (CRS registration) or CM/L-1234567 (ISI licence).");
        }
    }

    /** Null when a GST rate can be determined, otherwise why not. Uses the publish-time rule on a throwaway copy. */
    private String gstRateProblem(Product p) {
        Product probe = new Product();
        probe.setProductStatus("published");
        probe.setHsnCode(p.getHsnCode() == null ? null : p.getHsnCode().replaceAll("\\s", ""));
        probe.setGstRateSelectionId(p.getGstRateSelectionId());
        probe.setPrice(p.getPrice());
        probe.setOfferPrice(p.getOfferPrice());
        probe.setPrePackagedAndLabelled(p.getPrePackagedAndLabelled());
        try {
            hsnClassification.assertSellable(probe);
            return null;
        } catch (RuntimeException e) {
            return e.getMessage() != null ? e.getMessage() : "No published GST rate applies.";
        }
    }

    public static ComplianceCategoryType typeOf(Product p) {
        return p == null || p.getCategory() == null ? ComplianceCategoryType.GENERAL
                : ComplianceCategoryType.from(p.getCategory().getComplianceType());
    }

    // ------------------------------------------------------------------ seller identity

    public record SellerIdentity(String gstin, String businessName, String businessAddress) {}

    /** The seller's GSTIN, name and address, found the way checkout finds the GSTIN. */
    public SellerIdentity sellerIdentity(Long sellerId) {
        Optional<GstConfiguration> cfg = gstConfigRepo.findBySellerId(sellerId);
        Optional<SellerRegistration> reg = sellerRegRepo.findByUserId(sellerId);
        String gstin = cfg.map(GstConfiguration::getGstin).filter(ProductComplianceService::has).orElse(null);
        if (gstin == null) gstin = reg.map(SellerRegistration::getGstin).filter(ProductComplianceService::has).orElse(null);
        String name = reg.map(SellerRegistration::getBusinessName).filter(ProductComplianceService::has)
                .orElse(cfg.map(g -> has(g.getTradeName()) ? g.getTradeName() : g.getLegalName()).orElse(null));
        String address = reg.map(r -> joinAddress(r.getBusinessAddress(), r.getCity(), r.getState(), r.getPincode()))
                .filter(ProductComplianceService::has)
                .orElse(cfg.map(GstConfiguration::getAddress).orElse(null));
        return new SellerIdentity(gstin, name, address);
    }

    private static String joinAddress(String... parts) {
        return String.join(", ", Arrays.stream(parts).filter(ProductComplianceService::has).map(String::trim).toList());
    }

    // ------------------------------------------------------------------ the publish gate

    /** Refuses with the gaps listed when the product may not be published. Does nothing if the gate is off. */
    public void assertPublishable(Product product) {
        if (!gateEnabled()) return;
        Result r = evaluate(product, findRecord(product.getId()).orElse(null));
        if (!r.publishable()) throw new ComplianceBlockedException(r);
    }

    /**
     * Keeps a product off sale instead of refusing the save, when it is about to become visible
     * but may not be published: it goes back to waiting for approval, and the editor keeps their
     * work. Returns the result when it held the product, empty when it let it through.
     */
    public Optional<Result> holdIfNotPublishable(Product product) {
        if (!gateEnabled()) return Optional.empty();
        Result r = evaluate(product, findRecord(product.getId()).orElse(null));
        if (r.publishable()) return Optional.empty();
        product.setApprovalStatus("pending");
        product.setProductStatus("draft");
        return Optional.of(r);
    }

    // ------------------------------------------------------------------ helpers

    static boolean has(String s) { return s != null && !s.trim().isEmpty(); }

    private static boolean isIndia(String country) {
        String c = country.trim().toLowerCase(Locale.ROOT);
        return c.equals("india") || c.equals("in") || c.equals("ind") || c.equals("bharat");
    }

    /** Collects checks and their failures, one entry per field. */
    private static final class Checks {
        private final LinkedHashMap<String, Issue> issues = new LinkedHashMap<>();
        private final Set<String> fields = new LinkedHashSet<>();

        void require(String field, String label, String section, boolean critical, boolean ok) {
            fields.add(field);
            if (!ok && !issues.containsKey(field)) {
                issues.put(field, new Issue(field, label, section, critical, "MISSING", label + " is missing."));
            }
        }

        void valid(String field, String label, String section, boolean critical, boolean ok, String message) {
            fields.add(field);
            if (!ok && !issues.containsKey(field)) {
                issues.put(field, new Issue(field, label, section, critical, "INVALID", message));
            }
        }

        /** Replaces the generic message on a failed check with a specific one. */
        void note(String field, String message) {
            Issue i = issues.get(field);
            if (i != null) issues.put(field, new Issue(i.field(), i.label(), i.section(), i.critical(), i.problem(), message));
        }

        Result result(Long productId, ComplianceCategoryType type) {
            int total = fields.size();
            int failed = issues.size();
            int score = total == 0 ? 100 : (int) Math.floor(100.0 * (total - failed) / total);
            boolean anyCritical = issues.values().stream().anyMatch(Issue::critical);
            boolean publishable = !anyCritical && score >= PUBLISH_THRESHOLD;
            String band = failed == 0 ? "PUBLISHABLE" : publishable ? "WARNING" : "BLOCKED";
            return new Result(productId, type.name(), failed == 0, score, band, publishable,
                    new ArrayList<>(issues.keySet()), new ArrayList<>(issues.values()), total, LocalDateTime.now());
        }
    }
}
