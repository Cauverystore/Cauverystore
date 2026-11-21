package com.cauverystore.entities;

import jakarta.persistence.*;

/**
 * One piece of work for the separate agents service, and what came back.
 *
 * The admin queues a supplier product link (QUEUED). The listing agent picks it up, drafts the
 * listing with whichever AI provider it is set to, and sends the draft back (READY), or says why
 * it could not (FAILED). An admin then opens the draft in the Add Product form and creates the
 * product (USED) or turns it down (REJECTED). Agents never create products themselves.
 */
@Entity
@Table(name = "agent_jobs", indexes = @Index(name = "idx_agent_jobs_status", columnList = "status"))
public class AgentJob extends BaseEntity {

    public static final String QUEUED = "QUEUED";
    public static final String WORKING = "WORKING";
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";
    public static final String USED = "USED";
    public static final String REJECTED = "REJECTED";

    @Column(name = "agent_name", nullable = false, length = 40)
    private String agentName = "listing";

    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    @Column(nullable = false, length = 20)
    private String status = QUEUED;

    /** Optional cost typed by the admin; overrides the supplier's listed price. */
    @Column(name = "supplier_cost")
    private Double supplierCost;

    /** The checked draft as JSON, ready to fill the Add Product form. */
    @Column(name = "draft_json", columnDefinition = "TEXT")
    private String draftJson;

    @Column(length = 1000)
    private String note;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "queued_by", length = 120)
    private String queuedBy;

    @Column(nullable = false)
    private int attempts = 0;

    /** The supplier cost the stock and price watcher last accepted. */
    @Column(name = "watch_cost")
    private Double watchCost;

    /** Set when the watcher hid the product: OUT_OF_STOCK or NEEDS_REVIEW. */
    @Column(name = "watch_hold", length = 20)
    private String watchHold;

    @Column(name = "watch_checked_at")
    private java.time.LocalDateTime watchCheckedAt;

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getSupplierCost() { return supplierCost; }
    public void setSupplierCost(Double supplierCost) { this.supplierCost = supplierCost; }
    public String getDraftJson() { return draftJson; }
    public void setDraftJson(String draftJson) { this.draftJson = draftJson; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getQueuedBy() { return queuedBy; }
    public void setQueuedBy(String queuedBy) { this.queuedBy = queuedBy; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Double getWatchCost() { return watchCost; }
    public void setWatchCost(Double watchCost) { this.watchCost = watchCost; }
    public String getWatchHold() { return watchHold; }
    public void setWatchHold(String watchHold) { this.watchHold = watchHold; }
    public java.time.LocalDateTime getWatchCheckedAt() { return watchCheckedAt; }
    public void setWatchCheckedAt(java.time.LocalDateTime watchCheckedAt) { this.watchCheckedAt = watchCheckedAt; }
}
