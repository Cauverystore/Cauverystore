package com.cauverystore.billinglink;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One row per received purchase order sent to Cauvery Billing as a purchase bill. */
@Entity
@Table(name = "billing_link_purchases", uniqueConstraints = {
        @UniqueConstraint(name = "uq_billing_link_po", columnNames = {"purchase_order_id"})
})
public class BillingLinkPurchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "po_number", length = 60)
    private String poNumber;

    /** The purchase's id in Cauvery Billing. */
    @Column(name = "purchase_id")
    private Long purchaseId;

    @Column(name = "status", length = 12, nullable = false)
    private String status = BillingLinkRecord.FAILED;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long v) { purchaseOrderId = v; }
    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String v) { poNumber = v; }
    public Long getPurchaseId() { return purchaseId; }
    public void setPurchaseId(Long v) { purchaseId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { errorMessage = v == null || v.length() <= 1000 ? v : v.substring(0, 1000); }
    public int getAttempts() { return attempts; }
    public void setAttempts(int v) { attempts = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
