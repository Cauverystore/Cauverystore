package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * What the marketplace charges a seller for a sale.
 *
 * Effective-dated for the same reason GST rates are: raising the commission must not silently
 * restate what was charged on invoices already issued. A rate is never edited in place - it is
 * closed off and a new row starts the next day.
 *
 * Resolution is most-specific-first: a rate for this seller in this category beats one for the
 * category, which beats the platform default. A row with both keys null is that default.
 */
@Entity
@Table(name = "commission_rates", indexes = {
        @Index(name = "idx_commission_lookup", columnList = "seller_id,category_id,effective_from")
})
public class CommissionRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null means the rate applies to every category. */
    @Column(name = "category_id")
    private Long categoryId;

    /** Null means the rate applies to every seller. */
    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "rate_percent", nullable = false)
    private Double ratePercent;

    /** Flat fee per order on top of the percentage, where the platform charges one. */
    @Column(name = "fixed_fee", nullable = false)
    private Double fixedFee = 0.0;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Null means still in force. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** How closely this row targets a seller and category - higher wins. */
    @Transient
    public int specificity() {
        int score = 0;
        if (sellerId != null) score += 2;
        if (categoryId != null) score += 1;
        return score;
    }

    @Transient
    public boolean isInForce(LocalDate on) {
        if (on == null || effectiveFrom == null) return false;
        return !effectiveFrom.isAfter(on) && (effectiveTo == null || !effectiveTo.isBefore(on));
    }

    @Transient
    public boolean appliesTo(Long seller, Long category) {
        return (sellerId == null || sellerId.equals(seller))
                && (categoryId == null || categoryId.equals(category));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public Double getRatePercent() { return ratePercent; }
    public void setRatePercent(Double ratePercent) { this.ratePercent = ratePercent; }
    public Double getFixedFee() { return fixedFee; }
    public void setFixedFee(Double fixedFee) { this.fixedFee = fixedFee; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
