package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A seller asking to sell a brand: as its owner, or as someone the owner has authorised.
 *
 * Moves SUBMITTED -> UNDER_REVIEW -> APPROVED or REJECTED. Approval links it to the store's
 * brand list (creating the brand if it is new). For a gated brand an approved application is
 * what lets the seller list it.
 */
@Entity
@Table(name = "seller_brand_applications", indexes = {
        @Index(name = "idx_seller_brand_apps_seller", columnList = "seller_id"),
        @Index(name = "idx_seller_brand_apps_status", columnList = "status")
})
public class SellerBrandApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "brand_name", length = 120, nullable = false)
    private String brandName;

    /** The brand in the store's list, once approved (or when applying for an existing one). */
    @Column(name = "brand_id")
    private Long brandId;

    /** BRAND_OWNER, AUTHORISED_SELLER, MANUFACTURER or DISTRIBUTOR. */
    @Column(name = "relationship", length = 30, nullable = false)
    private String relationship;

    @Column(name = "trademark_number", length = 40)
    private String trademarkNumber;

    /** A private KYC document (trademark certificate or authorisation letter) backing the claim. */
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "SUBMITTED";

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

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
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public Long getBrandId() { return brandId; }
    public void setBrandId(Long brandId) { this.brandId = brandId; }
    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }
    public String getTrademarkNumber() { return trademarkNumber; }
    public void setTrademarkNumber(String trademarkNumber) { this.trademarkNumber = trademarkNumber; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
