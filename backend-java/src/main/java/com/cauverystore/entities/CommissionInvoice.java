package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The marketplace's own tax invoice to a seller for commission.
 *
 * A marketplace charging commission is making a taxable supply of services to its sellers, and
 * must invoice it with GST like anyone else. Without this the platform under-declares its own
 * output tax and the seller cannot claim the input credit on fees they have already paid.
 *
 * Deliberately a separate table from gst_invoices rather than a row in it, because the two
 * point in opposite directions and follow different rules:
 *
 *   product invoice     seller supplies the consumer; place of supply is where the goods go
 *   commission invoice  marketplace supplies the seller; place of supply is the SELLER's state
 *
 * So a Tamil Nadu marketplace billing a Tamil Nadu seller charges CGST+SGST even where the
 * order underneath shipped to Karnataka on IGST.
 */
@Entity
@Table(name = "commission_invoices", uniqueConstraints = {
        @UniqueConstraint(name = "uq_commission_seller_period", columnNames = {"seller_id", "period"})
}, indexes = {
        @Index(name = "idx_commission_inv_seller_period", columnList = "seller_id,period"),
        @Index(name = "idx_commission_inv_date", columnList = "invoice_date")
})
public class CommissionInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Rule 46 caps an invoice number at 16 characters. */
    @Column(name = "invoice_number", length = 16, nullable = false, unique = true)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    /** MMYYYY - commission is billed for a whole month, not per order. */
    @Column(name = "period", length = 6, nullable = false)
    private String period;

    // --- supplier: the marketplace ---
    @Column(name = "marketplace_gstin", length = 15, nullable = false)
    private String marketplaceGstin;

    @Column(name = "marketplace_legal_name")
    private String marketplaceLegalName;

    @Column(name = "marketplace_state_code", length = 2, nullable = false)
    private String marketplaceStateCode;

    // --- recipient: the seller ---
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "seller_gstin", length = 15)
    private String sellerGstin;

    @Column(name = "seller_legal_name")
    private String sellerLegalName;

    /** The place of supply for this invoice. The seller's state, not the consumer's. */
    @Column(name = "seller_state_code", length = 2, nullable = false)
    private String sellerStateCode;

    @Column(name = "sac_code", length = 6, nullable = false)
    private String sacCode = "998599";

    @Column(name = "taxable_amount", nullable = false)
    private Double taxableAmount;

    @Column(name = "gst_rate", nullable = false)
    private Double gstRate = 18.0;

    @Column(name = "cgst_amount", nullable = false)
    private Double cgstAmount = 0.0;

    @Column(name = "sgst_amount", nullable = false)
    private Double sgstAmount = 0.0;

    @Column(name = "igst_amount", nullable = false)
    private Double igstAmount = 0.0;

    @Column(name = "total_tax", nullable = false)
    private Double totalTax = 0.0;

    @Column(name = "total_amount", nullable = false)
    private Double totalAmount;

    @Column(name = "is_inter_state", nullable = false)
    private Boolean isInterState = false;

    /**
     * True when this intra-state commission invoice came from a marketplace registered in a
     * Union Territory without a legislature, so the state component is UTGST rather than SGST.
     * The amount is still stored in the state-tax fields - GSTN reports both under "State/UT Tax".
     */
    @Column(name = "utgst_applied", nullable = false)
    private Boolean utgstApplied = false;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ISSUED";

    @OneToMany(mappedBy = "commissionInvoice", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CommissionInvoiceLine> lines = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getMarketplaceGstin() { return marketplaceGstin; }
    public void setMarketplaceGstin(String g) { this.marketplaceGstin = g; }
    public String getMarketplaceLegalName() { return marketplaceLegalName; }
    public void setMarketplaceLegalName(String n) { this.marketplaceLegalName = n; }
    public String getMarketplaceStateCode() { return marketplaceStateCode; }
    public void setMarketplaceStateCode(String c) { this.marketplaceStateCode = c; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getSellerGstin() { return sellerGstin; }
    public void setSellerGstin(String sellerGstin) { this.sellerGstin = sellerGstin; }
    public String getSellerLegalName() { return sellerLegalName; }
    public void setSellerLegalName(String n) { this.sellerLegalName = n; }
    public String getSellerStateCode() { return sellerStateCode; }
    public void setSellerStateCode(String c) { this.sellerStateCode = c; }
    public String getSacCode() { return sacCode; }
    public void setSacCode(String sacCode) { this.sacCode = sacCode; }
    public Double getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(Double taxableAmount) { this.taxableAmount = taxableAmount; }
    public Double getGstRate() { return gstRate; }
    public void setGstRate(Double gstRate) { this.gstRate = gstRate; }
    public Double getCgstAmount() { return cgstAmount; }
    public void setCgstAmount(Double cgstAmount) { this.cgstAmount = cgstAmount; }
    public Double getSgstAmount() { return sgstAmount; }
    public void setSgstAmount(Double sgstAmount) { this.sgstAmount = sgstAmount; }
    public Double getIgstAmount() { return igstAmount; }
    public void setIgstAmount(Double igstAmount) { this.igstAmount = igstAmount; }
    public Double getTotalTax() { return totalTax; }
    public void setTotalTax(Double totalTax) { this.totalTax = totalTax; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public Boolean getIsInterState() { return isInterState; }
    public void setIsInterState(Boolean isInterState) { this.isInterState = isInterState; }
    public Boolean getUtgstApplied() { return utgstApplied; }
    public void setUtgstApplied(Boolean utgstApplied) { this.utgstApplied = utgstApplied; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<CommissionInvoiceLine> getLines() { return lines; }
    public void setLines(List<CommissionInvoiceLine> lines) { this.lines = lines; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
