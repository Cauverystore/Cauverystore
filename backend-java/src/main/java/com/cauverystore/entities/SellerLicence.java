package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A licence a seller holds to sell certain goods: FSSAI for food, a drug licence, BIS and so on.
 *
 * Shown to shoppers on the seller's products once an admin has checked it, because the law asks
 * for it to be visible: FSSAI requires a food seller's licence number on every e-commerce
 * listing, and the Consumer Protection (E-Commerce) Rules ask a marketplace to show who the
 * seller is. Until it is checked it shows only to the seller and admins - a number nobody has
 * looked at would read as an endorsement the store has not given.
 *
 * Any change by the seller puts it back to PENDING for a fresh check.
 */
@Entity
@Table(name = "seller_licences", indexes = {
        @Index(name = "idx_seller_licences_seller", columnList = "seller_id")
})
public class SellerLicence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /** FSSAI, DRUG, BIS, LEGAL_METROLOGY, IEC, TRADE, OTHER. */
    @Column(name = "licence_type", length = 30, nullable = false)
    private String licenceType;

    /** The licence's own name, for OTHER; otherwise left empty and the type's label is used. */
    @Column(name = "licence_name", length = 100)
    private String licenceName;

    @Column(name = "licence_number", length = 60, nullable = false)
    private String licenceNumber;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    /** PENDING, VERIFIED or REJECTED. */
    @Column(name = "status", length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

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

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getLicenceType() { return licenceType; }
    public void setLicenceType(String licenceType) { this.licenceType = licenceType; }
    public String getLicenceName() { return licenceName; }
    public void setLicenceName(String licenceName) { this.licenceName = licenceName; }
    public String getLicenceNumber() { return licenceNumber; }
    public void setLicenceNumber(String licenceNumber) { this.licenceNumber = licenceNumber; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
