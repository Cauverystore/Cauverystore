package com.cauverystore.entities;

import com.cauverystore.validation.ValidBis;
import com.cauverystore.validation.ValidCdsco;
import com.cauverystore.validation.ValidFssai;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The compliance details of one product: Legal Metrology declarations, who made, packed and
 * imported it, dates and batch, customer care, and the category-specific fields (food,
 * electronics, cosmetics, medical, toys, clothing).
 *
 * Kept beside Product rather than in it, one row per product, so the products table that every
 * storefront query reads does not grow by forty columns. Fields Product already had and the shop
 * already shows (MRP, country of origin, warranty, return policy, HSN, GST) stay on Product; the
 * compliance engine reads both.
 *
 * Also holds the product's place in the approval workflow and its last compliance score, so the
 * dashboard can filter without re-checking every product.
 */
@Getter
@Setter
@Entity
@Table(name = "product_compliance",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_compliance_product", columnNames = "product_id"),
        indexes = {
                @Index(name = "idx_product_compliance_status", columnList = "status"),
                @Index(name = "idx_product_compliance_score", columnList = "compliance_score")
        })
public class ProductCompliance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    // ---- Legal Metrology: quantity declarations
    @PositiveOrZero @Column(precision = 14, scale = 3)
    private BigDecimal netQuantity;
    @PositiveOrZero @Column(precision = 14, scale = 3)
    private BigDecimal grossQuantity;
    /** g, kg, ml, l, cm, m, N (number) ... as printed on the pack. */
    @Size(max = 20) @Column(length = 20)
    private String quantityUnit;

    // ---- Inventory: order limits
    @Min(1)
    private Integer minOrderQty;
    @Min(1)
    private Integer maxOrderQty;

    // ---- Manufacturer, packer, importer
    @Size(max = 255)
    private String manufacturerName;
    @Column(columnDefinition = "TEXT")
    private String manufacturerAddress;
    /** True when the manufacturer also packed it, so the packer fields need not be repeated. */
    private Boolean packerSameAsManufacturer;
    @Size(max = 255)
    private String packerName;
    @Column(columnDefinition = "TEXT")
    private String packerAddress;
    /** Only for goods made outside India. */
    @Size(max = 255)
    private String importerName;
    @Column(columnDefinition = "TEXT")
    private String importerAddress;

    // ---- Origin (country of origin is Product.countryOfOrigin)
    @Size(max = 100)
    private String stateOfOrigin;

    // ---- Customer care
    @Size(max = 255)
    private String customerCareName;
    @Pattern(regexp = "^$|^[+0-9 ()\\-]{6,20}$", message = "must be a phone number")
    @Column(length = 20)
    private String customerCarePhone;
    @Email @Size(max = 255)
    private String customerCareEmail;
    @Column(columnDefinition = "TEXT")
    private String customerCareAddress;

    // ---- Dates and traceability
    private LocalDate mfgDate;
    private LocalDate bestBeforeDate;
    private LocalDate expiryDate;
    @Size(max = 100) @Column(length = 100)
    private String batchNumber;
    @Size(max = 100) @Column(length = 100)
    private String lotNumber;
    @Size(max = 100) @Column(length = 100)
    private String serialNumber;

    // ---- Food
    @ValidFssai @Column(length = 20)
    private String fssaiLicenceNumber;
    /** Food and cosmetics. */
    @Column(columnDefinition = "TEXT")
    private String ingredients;
    @Column(columnDefinition = "TEXT")
    private String nutritionInfo;
    @Column(columnDefinition = "TEXT")
    private String allergens;
    /** VEG or NON_VEG. */
    @Pattern(regexp = "^$|^(VEG|NON_VEG)$", message = "must be VEG or NON_VEG")
    @Column(length = 10)
    private String foodPreference;
    @Column(columnDefinition = "TEXT")
    private String storageInstructions;

    // ---- Electronics (warranty is Product.warranty)
    @Column(columnDefinition = "TEXT")
    private String serviceCentreDetails;
    /** Electronics and toys. */
    @ValidBis @Column(length = 30)
    private String bisNumber;
    @Size(max = 50) @Column(length = 50)
    private String voltage;
    @Size(max = 50) @Column(length = 50)
    private String powerRating;

    // ---- Cosmetics
    @Size(max = 100) @Column(length = 100)
    private String cosmeticLicenceNumber;

    // ---- Medical
    @ValidCdsco @Column(length = 40)
    private String cdscoRegistrationNumber;
    private Boolean prescriptionRequired;
    @Column(columnDefinition = "TEXT")
    private String warnings;
    @Column(columnDefinition = "TEXT")
    private String disclaimer;

    // ---- Toys
    @Size(max = 50) @Column(length = 50)
    private String ageGroup;
    @Column(columnDefinition = "TEXT")
    private String safetyWarning;

    // ---- Clothing
    @Size(max = 255)
    private String fabric;
    @Column(columnDefinition = "TEXT")
    private String washCare;
    @Column(columnDefinition = "TEXT")
    private String sizeChart;

    // ---- Result of the last compliance check
    @Column(name = "compliance_score")
    private Integer complianceScore;
    /** Comma-separated field keys that were missing or invalid at the last check. */
    @Column(columnDefinition = "TEXT")
    private String missingFields;
    private Boolean publishable;
    private LocalDateTime lastCheckedAt;

    // ---- Workflow
    /** A ProductWorkflowStatus name. */
    @Column(length = 30, nullable = false)
    private String status = ProductWorkflowStatus.DRAFT.name();
    @Column(columnDefinition = "TEXT")
    private String statusNote;

    // ---- Audit
    @Column(updatable = false, length = 255)
    private String createdBy;
    @Column(updatable = false)
    private LocalDateTime createdDate;
    @Column(length = 255)
    private String updatedBy;
    private LocalDateTime updatedDate;
    @Version
    private Long version;
    @Column(nullable = false)
    private boolean deletedFlag = false;

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        updatedDate = createdDate;
        createdBy = currentActor();
        updatedBy = createdBy;
        if (status == null) status = ProductWorkflowStatus.DRAFT.name();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
        updatedBy = currentActor();
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth.getName() : "system";
    }

    /**
     * Copies what an editor may change. Identity, workflow state, score and audit columns are
     * never taken from a request.
     */
    public void copyEditableFrom(ProductCompliance o) {
        netQuantity = o.netQuantity; grossQuantity = o.grossQuantity; quantityUnit = o.quantityUnit;
        minOrderQty = o.minOrderQty; maxOrderQty = o.maxOrderQty;
        manufacturerName = o.manufacturerName; manufacturerAddress = o.manufacturerAddress;
        packerSameAsManufacturer = o.packerSameAsManufacturer; packerName = o.packerName; packerAddress = o.packerAddress;
        importerName = o.importerName; importerAddress = o.importerAddress;
        stateOfOrigin = o.stateOfOrigin;
        customerCareName = o.customerCareName; customerCarePhone = o.customerCarePhone;
        customerCareEmail = o.customerCareEmail; customerCareAddress = o.customerCareAddress;
        mfgDate = o.mfgDate; bestBeforeDate = o.bestBeforeDate; expiryDate = o.expiryDate;
        batchNumber = o.batchNumber; lotNumber = o.lotNumber; serialNumber = o.serialNumber;
        fssaiLicenceNumber = o.fssaiLicenceNumber; ingredients = o.ingredients; nutritionInfo = o.nutritionInfo;
        allergens = o.allergens; foodPreference = o.foodPreference; storageInstructions = o.storageInstructions;
        serviceCentreDetails = o.serviceCentreDetails; bisNumber = o.bisNumber; voltage = o.voltage; powerRating = o.powerRating;
        cosmeticLicenceNumber = o.cosmeticLicenceNumber;
        cdscoRegistrationNumber = o.cdscoRegistrationNumber; prescriptionRequired = o.prescriptionRequired;
        warnings = o.warnings; disclaimer = o.disclaimer;
        ageGroup = o.ageGroup; safetyWarning = o.safetyWarning;
        fabric = o.fabric; washCare = o.washCare; sizeChart = o.sizeChart;
    }
}
