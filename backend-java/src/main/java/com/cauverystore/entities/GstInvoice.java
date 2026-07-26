package com.cauverystore.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "gst_invoices")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class GstInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    private String irn;
    private String qrCode;
    private String ackNo;
    private String ackDate;

    @Column(nullable = false)
    private Long orderId;

    private Long sellerId;

    @Column(nullable = false)
    private String sellerGstin;

    @Column(nullable = false)
    private String buyerGstin;

    private String buyerName;
    private String buyerAddress;
    private String buyerStateCode;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    @Column(nullable = false)
    private Double taxableAmount;

    @Column(nullable = false)
    private Double cgstRate;
    private Double cgstAmount;

    @Column(nullable = false)
    private Double sgstRate;
    private Double sgstAmount;

    private Double igstRate;
    private Double igstAmount;

    @Column(nullable = false)
    private Double totalTax;

    @Column(nullable = false)
    private Double totalAmount;

    private Double tcsAmount;
    private Double tcsRate = 1.0;

    private String placeOfSupply;
    private Boolean isInterState = false;

    private String ewayBillNumber;
    private LocalDate ewayBillExpiry;

    private String status = "DRAFT";

    @Column(columnDefinition = "TEXT")
    private String syncError;

    private Integer syncAttempts = 0;
    private LocalDateTime lastSyncAttempt;

    private Boolean reverseCharge = false;
    private String invoiceCopyType;
    private String supplyType = "GOODS";
    private String sellerLegalName;
    @Column(columnDefinition = "TEXT")
    private String sellerAddress;
    private Integer hsnDigits = 4;
    private String invoiceType = "B2C";

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("invoice")
    private List<GstInvoiceItem> items = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public String getIrn() { return irn; }
    public void setIrn(String irn) { this.irn = irn; }
    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
    public String getAckNo() { return ackNo; }
    public void setAckNo(String ackNo) { this.ackNo = ackNo; }
    public String getAckDate() { return ackDate; }
    public void setAckDate(String ackDate) { this.ackDate = ackDate; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getSellerGstin() { return sellerGstin; }
    public void setSellerGstin(String sellerGstin) { this.sellerGstin = sellerGstin; }
    public String getBuyerGstin() { return buyerGstin; }
    public void setBuyerGstin(String buyerGstin) { this.buyerGstin = buyerGstin; }
    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
    public String getBuyerAddress() { return buyerAddress; }
    public void setBuyerAddress(String buyerAddress) { this.buyerAddress = buyerAddress; }
    public String getBuyerStateCode() { return buyerStateCode; }
    public void setBuyerStateCode(String buyerStateCode) { this.buyerStateCode = buyerStateCode; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public Double getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(Double taxableAmount) { this.taxableAmount = taxableAmount; }
    public Double getCgstRate() { return cgstRate; }
    public void setCgstRate(Double cgstRate) { this.cgstRate = cgstRate; }
    public Double getCgstAmount() { return cgstAmount; }
    public void setCgstAmount(Double cgstAmount) { this.cgstAmount = cgstAmount; }
    public Double getSgstRate() { return sgstRate; }
    public void setSgstRate(Double sgstRate) { this.sgstRate = sgstRate; }
    public Double getSgstAmount() { return sgstAmount; }
    public void setSgstAmount(Double sgstAmount) { this.sgstAmount = sgstAmount; }
    public Double getIgstRate() { return igstRate; }
    public void setIgstRate(Double igstRate) { this.igstRate = igstRate; }
    public Double getIgstAmount() { return igstAmount; }
    public void setIgstAmount(Double igstAmount) { this.igstAmount = igstAmount; }
    public Double getTotalTax() { return totalTax; }
    public void setTotalTax(Double totalTax) { this.totalTax = totalTax; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public Double getTcsAmount() { return tcsAmount; }
    public void setTcsAmount(Double tcsAmount) { this.tcsAmount = tcsAmount; }
    public Double getTcsRate() { return tcsRate; }
    public void setTcsRate(Double tcsRate) { this.tcsRate = tcsRate; }
    public String getPlaceOfSupply() { return placeOfSupply; }
    public void setPlaceOfSupply(String placeOfSupply) { this.placeOfSupply = placeOfSupply; }
    public Boolean getIsInterState() { return isInterState; }
    public void setIsInterState(Boolean isInterState) { this.isInterState = isInterState; }
    public String getEwayBillNumber() { return ewayBillNumber; }
    public void setEwayBillNumber(String ewayBillNumber) { this.ewayBillNumber = ewayBillNumber; }
    public LocalDate getEwayBillExpiry() { return ewayBillExpiry; }
    public void setEwayBillExpiry(LocalDate ewayBillExpiry) { this.ewayBillExpiry = ewayBillExpiry; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSyncError() { return syncError; }
    public void setSyncError(String syncError) { this.syncError = syncError; }
    public Integer getSyncAttempts() { return syncAttempts; }
    public void setSyncAttempts(Integer syncAttempts) { this.syncAttempts = syncAttempts; }
    public LocalDateTime getLastSyncAttempt() { return lastSyncAttempt; }
    public void setLastSyncAttempt(LocalDateTime lastSyncAttempt) { this.lastSyncAttempt = lastSyncAttempt; }
    public List<GstInvoiceItem> getItems() { return items; }
    public void setItems(List<GstInvoiceItem> items) { this.items = items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getReverseCharge() { return reverseCharge; }
    public void setReverseCharge(Boolean reverseCharge) { this.reverseCharge = reverseCharge; }
    public String getInvoiceCopyType() { return invoiceCopyType; }
    public void setInvoiceCopyType(String invoiceCopyType) { this.invoiceCopyType = invoiceCopyType; }
    public String getSupplyType() { return supplyType; }
    public void setSupplyType(String supplyType) { this.supplyType = supplyType; }
    public String getSellerLegalName() { return sellerLegalName; }
    public void setSellerLegalName(String sellerLegalName) { this.sellerLegalName = sellerLegalName; }
    public String getSellerAddress() { return sellerAddress; }
    public void setSellerAddress(String sellerAddress) { this.sellerAddress = sellerAddress; }
    public Integer getHsnDigits() { return hsnDigits; }
    public void setHsnDigits(Integer hsnDigits) { this.hsnDigits = hsnDigits; }
    public String getInvoiceType() { return invoiceType; }
    public void setInvoiceType(String invoiceType) { this.invoiceType = invoiceType; }
}
