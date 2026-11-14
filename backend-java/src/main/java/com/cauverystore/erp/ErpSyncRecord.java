package com.cauverystore.erp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One row per local record pushed to ERPNext. Makes the sync idempotent and shows what failed. */
@Entity
@Table(name = "erp_sync_records", uniqueConstraints = {
        @UniqueConstraint(name = "uq_erp_sync_entity", columnNames = {"entity_type", "entity_id"})
})
public class ErpSyncRecord {
    public static final String COMMISSION_INVOICE = "COMMISSION_INVOICE";
    public static final String SELLER_PAYOUT = "SELLER_PAYOUT";
    /** A product the store sells itself, as an ERPNext Item. */
    public static final String ITEM = "ITEM";
    /** A customer tax invoice for the store's own goods. */
    public static final String SALES_INVOICE = "SALES_INVOICE";
    /** A credit note against one of those invoices (return or price adjustment). */
    public static final String SALES_RETURN = "SALES_RETURN";
    /** Money received from a customer for an order. */
    public static final String CUSTOMER_PAYMENT = "CUSTOMER_PAYMENT";
    /** Money paid back to a customer. */
    public static final String CUSTOMER_REFUND = "CUSTOMER_REFUND";
    /** A received purchase order from a supplier, as a draft Purchase Invoice. */
    public static final String PURCHASE_BILL = "PURCHASE_BILL";
    public static final String SYNCED = "SYNCED";
    public static final String FAILED = "FAILED";
    /** Not an error: needs another record first (e.g. a payment waits for its invoice). */
    public static final String WAITING = "WAITING";
    /** Cancelled on the website after it reached the ERP, and cancelled there too. */
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", length = 40, nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "erp_doc_name")
    private String erpDocName;

    @Column(name = "status", length = 12, nullable = false)
    private String status = FAILED;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    /** Hash of what was last sent, so a changed product is sent again. */
    @Column(name = "fingerprint", length = 64)
    private String fingerprint;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String t) { this.entityType = t; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long id) { this.entityId = id; }
    public String getErpDocName() { return erpDocName; }
    public void setErpDocName(String n) { this.erpDocName = n; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String m) { this.errorMessage = m; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer a) { this.attempts = a; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String f) { this.fingerprint = f; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
