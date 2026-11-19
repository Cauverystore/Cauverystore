package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One line in a seller's money ledger. Append-only.
 *
 * Positive paise are owed to the seller, negative are owed back to the marketplace. A mistake is
 * corrected by a new line that reverses it, never by editing or deleting, so the history a
 * seller or an auditor reads cannot change. The only field that is ever updated is
 * {@code settlementRunId}, which records the run that paid the line out.
 *
 * {@code refKey} is unique, so posting the same order or refund twice (a retry, a double click, a
 * second server) is a no-op instead of a double credit.
 */
@Entity
@Table(name = "finance_ledger", indexes = {
        @Index(name = "idx_ledger_seller", columnList = "seller_id,available_on"),
        @Index(name = "idx_ledger_order", columnList = "order_id"),
        @Index(name = "idx_ledger_run", columnList = "settlement_run_id")
}, uniqueConstraints = @UniqueConstraint(name = "uq_ledger_ref", columnNames = "ref_key"))
public class LedgerEntry {

    public static final String SALE = "SALE";
    public static final String COMMISSION = "COMMISSION";
    public static final String HANDLING_FEE = "HANDLING_FEE";
    public static final String PAYMENT_FEE = "PAYMENT_FEE";
    public static final String GST_ON_FEES = "GST_ON_FEES";
    public static final String TCS = "TCS";
    public static final String ADJUSTMENT = "ADJUSTMENT";
    public static final String PENALTY = "PENALTY";
    public static final String RESERVE_RELEASE = "RESERVE_RELEASE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "refund_id")
    private Long refundId;

    @Column(name = "entry_type", length = 24, nullable = false)
    private String entryType;

    /** ORDER, REFUND or MANUAL. */
    @Column(name = "source", length = 12, nullable = false)
    private String source;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    /** The first day this line can be paid out (end of the return window for a sale). */
    @Column(name = "available_on", nullable = false)
    private LocalDate availableOn;

    @Column(name = "ref_key", length = 96, nullable = false)
    private String refKey;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "settlement_run_id")
    private Long settlementRunId;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getRefundId() { return refundId; }
    public void setRefundId(Long refundId) { this.refundId = refundId; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public long getAmountPaise() { return amountPaise; }
    public void setAmountPaise(long amountPaise) { this.amountPaise = amountPaise; }
    public LocalDate getAvailableOn() { return availableOn; }
    public void setAvailableOn(LocalDate availableOn) { this.availableOn = availableOn; }
    public String getRefKey() { return refKey; }
    public void setRefKey(String refKey) { this.refKey = refKey; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getSettlementRunId() { return settlementRunId; }
    public void setSettlementRunId(Long settlementRunId) { this.settlementRunId = settlementRunId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
