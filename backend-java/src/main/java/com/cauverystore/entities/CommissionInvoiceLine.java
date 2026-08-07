package com.cauverystore.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

/**
 * One order's worth of commission on a monthly commission invoice.
 *
 * The rate is stored on the line, not looked up when the invoice is read. A rate card change
 * must not restate what was already billed.
 */
@Entity
@Table(name = "commission_invoice_lines", indexes = {
        @Index(name = "idx_commission_lines_order", columnList = "order_id")
})
public class CommissionInvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_invoice_id", nullable = false)
    @JsonIgnoreProperties("lines")
    private CommissionInvoice commissionInvoice;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** The product tax invoice this commission was earned against, where one exists. */
    @Column(name = "gst_invoice_id")
    private Long gstInvoiceId;

    @Column(name = "order_value", nullable = false)
    private Double orderValue;

    @Column(name = "rate_percent", nullable = false)
    private Double ratePercent;

    @Column(name = "fixed_fee", nullable = false)
    private Double fixedFee = 0.0;

    @Column(name = "commission_amount", nullable = false)
    private Double commissionAmount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CommissionInvoice getCommissionInvoice() { return commissionInvoice; }
    public void setCommissionInvoice(CommissionInvoice ci) { this.commissionInvoice = ci; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getGstInvoiceId() { return gstInvoiceId; }
    public void setGstInvoiceId(Long gstInvoiceId) { this.gstInvoiceId = gstInvoiceId; }
    public Double getOrderValue() { return orderValue; }
    public void setOrderValue(Double orderValue) { this.orderValue = orderValue; }
    public Double getRatePercent() { return ratePercent; }
    public void setRatePercent(Double ratePercent) { this.ratePercent = ratePercent; }
    public Double getFixedFee() { return fixedFee; }
    public void setFixedFee(Double fixedFee) { this.fixedFee = fixedFee; }
    public Double getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(Double commissionAmount) { this.commissionAmount = commissionAmount; }
}
