package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Money the marketplace has actually paid a seller.
 *
 * A record of a payment made outside the system (bank transfer, UPI), entered by an admin with
 * the bank's reference so the seller can match it to their statement. It does not work out
 * what is owed: that needs commission, TCS and refunds settled first, and a figure here that
 * disagreed with them would be worse than none.
 *
 * Never deleted. A payout entered by mistake is voided with a reason, so the seller's history
 * cannot silently change under them.
 */
@Entity
@Table(name = "seller_payouts", indexes = {
        @Index(name = "idx_seller_payouts_seller", columnList = "seller_id,paid_on")
})
public class SellerPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    /** The bank or UPI reference (UTR) the seller sees on their own statement. */
    @Column(name = "reference", length = 64, nullable = false)
    private String reference;

    /** BANK_TRANSFER, UPI or OTHER. */
    @Column(name = "method", length = 20, nullable = false)
    private String method = "BANK_TRANSFER";

    /** The sales period the payment covers, where the admin knows it. */
    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    /** Shown to the seller. */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "voided", nullable = false)
    private Boolean voided = false;

    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;

    @Column(name = "voided_by")
    private String voidedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public LocalDate getPaidOn() { return paidOn; }
    public void setPaidOn(LocalDate paidOn) { this.paidOn = paidOn; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public LocalDate getPeriodFrom() { return periodFrom; }
    public void setPeriodFrom(LocalDate periodFrom) { this.periodFrom = periodFrom; }
    public LocalDate getPeriodTo() { return periodTo; }
    public void setPeriodTo(LocalDate periodTo) { this.periodTo = periodTo; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public Boolean getVoided() { return voided; }
    public void setVoided(Boolean voided) { this.voided = voided; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public String getVoidedBy() { return voidedBy; }
    public void setVoidedBy(String voidedBy) { this.voidedBy = voidedBy; }
    public LocalDateTime getVoidedAt() { return voidedAt; }
    public void setVoidedAt(LocalDateTime voidedAt) { this.voidedAt = voidedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
