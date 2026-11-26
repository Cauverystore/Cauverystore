package com.cauverystore.billinglink;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One row per store invoice sent to Cauvery Billing. Makes the link send each invoice once and shows what failed. */
@Entity
@Table(name = "billing_link_records", uniqueConstraints = {
        @UniqueConstraint(name = "uq_billing_link_invoice", columnNames = {"invoice_id"})
})
public class BillingLinkRecord {
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "invoice_number", length = 60)
    private String invoiceNumber;

    /** The bill's id in Cauvery Billing. */
    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "status", length = 12, nullable = false)
    private String status = FAILED;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long v) { this.invoiceId = v; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String v) { this.invoiceNumber = v; }
    public Long getBillId() { return billId; }
    public void setBillId(Long v) { this.billId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer v) { this.attempts = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
