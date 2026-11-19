package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One payout cycle. DRAFT (prepared, nothing committed) -> APPROVED (a second person signed it
 * off) -> PAID (every payable seller has been paid). A draft can be CANCELLED; an approved run
 * cannot, because money may already be on its way.
 */
@Entity
@Table(name = "settlement_runs")
public class SettlementRun {

    public static final String DRAFT = "DRAFT";
    public static final String APPROVED = "APPROVED";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ledger lines that became payable on or before this date are in the run. */
    @Column(name = "cutoff_date", nullable = false)
    private LocalDate cutoffDate;

    @Column(name = "status", length = 12, nullable = false)
    private String status = DRAFT;

    @Column(name = "total_payable_paise", nullable = false)
    private long totalPayablePaise;

    @Column(name = "payable_sellers", nullable = false)
    private int payableSellers;

    @Column(name = "held_sellers", nullable = false)
    private int heldSellers;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public LocalDate getCutoffDate() { return cutoffDate; }
    public void setCutoffDate(LocalDate cutoffDate) { this.cutoffDate = cutoffDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getTotalPayablePaise() { return totalPayablePaise; }
    public void setTotalPayablePaise(long v) { this.totalPayablePaise = v; }
    public int getPayableSellers() { return payableSellers; }
    public void setPayableSellers(int v) { this.payableSellers = v; }
    public int getHeldSellers() { return heldSellers; }
    public void setHeldSellers(int v) { this.heldSellers = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
