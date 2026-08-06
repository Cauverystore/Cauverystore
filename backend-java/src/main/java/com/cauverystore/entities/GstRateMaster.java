package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * GST rate applicable to an HSN code for a period.
 *
 * Rates are NOT published by GSTN alongside the HSN master - they come from CBIC rate
 * notifications (e.g. 09/2025-Central Tax (Rate), effective 22-09-2025, which superseded
 * 1/2017 and removed the 12% and 28% slabs). So rows here are entered/reviewed by a human
 * and carry provenance, rather than being scraped.
 *
 * Rows are versioned by effectiveFrom/effectiveTo so a historical invoice can be re-derived
 * at the rate that applied on its own date, while new orders pick up current rates.
 *
 * Only VERIFIED rows are used for charging tax. UNVERIFIED rows are proposals awaiting
 * sign-off and are deliberately ignored by the resolver.
 */
@Entity
@Table(name = "gst_rate_master", indexes = {
        @Index(name = "idx_gst_rate_hsn", columnList = "hsn_code"),
        @Index(name = "idx_gst_rate_lookup", columnList = "hsn_code,status,effective_from")
})
public class GstRateMaster {

    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_UNVERIFIED = "UNVERIFIED";

    /** Rate applies regardless of price. */
    public static final String CONDITION_NONE = "NONE";
    /** Applies when the unit price is at or below thresholdAmount. */
    public static final String CONDITION_VALUE_UPTO = "VALUE_UPTO";
    /** Applies when the unit price is above thresholdAmount. */
    public static final String CONDITION_VALUE_ABOVE = "VALUE_ABOVE";
    /** Applies when the goods are supplied pre-packaged and labelled. */
    public static final String CONDITION_PACKAGED = "PRE_PACKAGED";
    /** Applies when the goods are supplied loose, i.e. not pre-packaged and labelled. */
    public static final String CONDITION_UNPACKAGED = "NOT_PRE_PACKAGED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hsn_code", length = 8, nullable = false)
    private String hsnCode;

    /** Total GST percentage (e.g. 5.0, 18.0). CGST/SGST are each half of this on intra-state. */
    @Column(name = "gst_rate", nullable = false)
    private Double gstRate;

    /** Compensation cess, where applicable (demerit goods). */
    @Column(name = "cess_rate")
    private Double cessRate = 0.0;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Null means "still in force". Set when a later notification supersedes this row. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "status", length = 20, nullable = false)
    private String status = STATUS_UNVERIFIED;

    /**
     * Some rates depend on the item's SELLING PRICE, not just what it is - apparel under
     * chapters 61-63 is 5% at or below Rs 2500 per piece and 18% above it, footwear likewise
     * per pair. Such a heading has two rows distinguished only by this condition, so the code
     * alone cannot decide the rate.
     */
    @Column(name = "condition_type", length = 20)
    private String conditionType = CONDITION_NONE;

    @Column(name = "threshold_amount")
    private Double thresholdAmount;

    /** "piece", "pair", "unit" - what the threshold is measured per. */
    @Column(name = "threshold_unit", length = 20)
    private String thresholdUnit;

    /** The notification's own wording, kept verbatim so a reviewer can tell rows apart. */
    @Column(name = "condition_text", columnDefinition = "TEXT")
    private String conditionText;

    /** Provenance - e.g. "Notification 09/2025-Central Tax (Rate)" or "CA review 2026-08". */
    @Column(name = "source", columnDefinition = "TEXT")
    private String source;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "verified_by")
    private String verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** CGST half of the total rate (intra-state supply). */
    @Transient
    public double getCgstRate() {
        return gstRate != null ? gstRate / 2.0 : 0.0;
    }

    /** SGST half of the total rate (intra-state supply). */
    @Transient
    public double getSgstRate() {
        return gstRate != null ? gstRate / 2.0 : 0.0;
    }

    /** Full rate as IGST (inter-state supply). */
    @Transient
    public double getIgstRate() {
        return gstRate != null ? gstRate : 0.0;
    }

    @Transient
    public boolean isVerified() {
        return STATUS_VERIFIED.equals(status);
    }

    @Transient
    public boolean isConditional() {
        return conditionType != null && !CONDITION_NONE.equals(conditionType);
    }

    /** True when this row's condition is decided by the item's price rather than its packaging. */
    @Transient
    public boolean isValueBanded() {
        return CONDITION_VALUE_UPTO.equals(conditionType) || CONDITION_VALUE_ABOVE.equals(conditionType);
    }

    /** True when this row's condition is decided by whether the goods are pre-packaged. */
    @Transient
    public boolean isPackagingBanded() {
        return CONDITION_PACKAGED.equals(conditionType) || CONDITION_UNPACKAGED.equals(conditionType);
    }

    /** @deprecated use {@link #appliesTo(Double, Boolean)}; kept for callers that only know the price. */
    @Deprecated
    @Transient
    public boolean appliesToUnitPrice(Double unitPrice) {
        return appliesTo(unitPrice, null);
    }

    /**
     * Whether this row applies to an item with the given price and packaging.
     *
     * An unconditional row applies to everything; a conditional one only to its own side of
     * the split. A condition whose input is unknown does NOT apply - failing to resolve sends
     * the caller to the fallback and logs it, whereas guessing charges the wrong rate and
     * looks entirely correct until an audit.
     */
    @Transient
    public boolean appliesTo(Double unitPrice, Boolean prePackaged) {
        if (!isConditional()) return true;
        if (CONDITION_VALUE_UPTO.equals(conditionType)) {
            return unitPrice != null && thresholdAmount != null && unitPrice <= thresholdAmount;
        }
        if (CONDITION_VALUE_ABOVE.equals(conditionType)) {
            return unitPrice != null && thresholdAmount != null && unitPrice > thresholdAmount;
        }
        if (CONDITION_PACKAGED.equals(conditionType)) {
            return Boolean.TRUE.equals(prePackaged);
        }
        if (CONDITION_UNPACKAGED.equals(conditionType)) {
            return Boolean.FALSE.equals(prePackaged);
        }
        return false;
    }

    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }

    public Double getThresholdAmount() { return thresholdAmount; }
    public void setThresholdAmount(Double thresholdAmount) { this.thresholdAmount = thresholdAmount; }

    public String getThresholdUnit() { return thresholdUnit; }
    public void setThresholdUnit(String thresholdUnit) { this.thresholdUnit = thresholdUnit; }

    public String getConditionText() { return conditionText; }
    public void setConditionText(String conditionText) { this.conditionText = conditionText; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }

    public Double getGstRate() { return gstRate; }
    public void setGstRate(Double gstRate) { this.gstRate = gstRate; }

    public Double getCessRate() { return cessRate; }
    public void setCessRate(Double cessRate) { this.cessRate = cessRate; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
