package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDate;

/** One seller's line in a settlement run. HELD sellers are listed with the reason, but nothing is paid or locked for them. */
@Entity
@Table(name = "settlement_items", indexes = @Index(name = "idx_settle_item_run", columnList = "run_id"))
public class SettlementItem {

    public static final String PAYABLE = "PAYABLE";
    public static final String PAID = "PAID";
    public static final String HELD = "HELD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /** Sum of the ledger lines in this item, before any reserve. */
    @Column(name = "gross_paise", nullable = false)
    private long grossPaise;

    @Column(name = "reserve_paise", nullable = false)
    private long reservePaise;

    @Column(name = "payable_paise", nullable = false)
    private long payablePaise;

    @Column(name = "entry_count", nullable = false)
    private int entryCount;

    @Column(name = "status", length = 10, nullable = false)
    private String status;

    @Column(name = "held_reason", length = 160)
    private String heldReason;

    @Column(name = "payout_id")
    private Long payoutId;

    @Column(name = "utr", length = 64)
    private String utr;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public long getGrossPaise() { return grossPaise; }
    public void setGrossPaise(long v) { this.grossPaise = v; }
    public long getReservePaise() { return reservePaise; }
    public void setReservePaise(long v) { this.reservePaise = v; }
    public long getPayablePaise() { return payablePaise; }
    public void setPayablePaise(long v) { this.payablePaise = v; }
    public int getEntryCount() { return entryCount; }
    public void setEntryCount(int v) { this.entryCount = v; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHeldReason() { return heldReason; }
    public void setHeldReason(String heldReason) { this.heldReason = heldReason; }
    public Long getPayoutId() { return payoutId; }
    public void setPayoutId(Long payoutId) { this.payoutId = payoutId; }
    public String getUtr() { return utr; }
    public void setUtr(String utr) { this.utr = utr; }
    public LocalDate getPaidOn() { return paidOn; }
    public void setPaidOn(LocalDate paidOn) { this.paidOn = paidOn; }
}
