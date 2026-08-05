package com.cauverystore.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "credit_notes")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CreditNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String creditNoteNumber;

    private Long orderId;
    private Long invoiceId;
    private String originalInvoiceNumber;

    private Long sellerId;
    private Long customerId;
    private String customerEmail;

    private String sellerGstin;
    private String sellerLegalName;
    @Column(columnDefinition = "TEXT")
    private String sellerAddress;

    private String buyerGstin;
    private String buyerName;
    @Column(columnDefinition = "TEXT")
    private String buyerAddress;
    private String buyerStateCode;

    @Column(nullable = false)
    private LocalDate creditNoteDate;

    @Column(nullable = false)
    private Double taxableAmount;
    private Double cgstRate;
    private Double cgstAmount;
    private Double sgstRate;
    private Double sgstAmount;
    private Double igstRate;
    private Double igstAmount;
    private Double totalTax;
    private Double totalAmount;
    private Double tcsAmount;

    private String placeOfSupply;
    private Boolean isInterState = false;
    private String invoiceType = "B2C";

    private String noteType = "CREDIT";
    @Column(columnDefinition = "TEXT")
    private String reason;
    private String referenceType;
    private Long referenceId;
    private String status = "GENERATED";

    @OneToMany(mappedBy = "creditNote", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("creditNote")
    private List<CreditNoteItem> items = new ArrayList<>();

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
    public String getCreditNoteNumber() { return creditNoteNumber; }
    public void setCreditNoteNumber(String creditNoteNumber) { this.creditNoteNumber = creditNoteNumber; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    public String getOriginalInvoiceNumber() { return originalInvoiceNumber; }
    public void setOriginalInvoiceNumber(String originalInvoiceNumber) { this.originalInvoiceNumber = originalInvoiceNumber; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getSellerGstin() { return sellerGstin; }
    public void setSellerGstin(String sellerGstin) { this.sellerGstin = sellerGstin; }
    public String getSellerLegalName() { return sellerLegalName; }
    public void setSellerLegalName(String sellerLegalName) { this.sellerLegalName = sellerLegalName; }
    public String getSellerAddress() { return sellerAddress; }
    public void setSellerAddress(String sellerAddress) { this.sellerAddress = sellerAddress; }
    public String getBuyerGstin() { return buyerGstin; }
    public void setBuyerGstin(String buyerGstin) { this.buyerGstin = buyerGstin; }
    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
    public String getBuyerAddress() { return buyerAddress; }
    public void setBuyerAddress(String buyerAddress) { this.buyerAddress = buyerAddress; }
    public String getBuyerStateCode() { return buyerStateCode; }
    public void setBuyerStateCode(String buyerStateCode) { this.buyerStateCode = buyerStateCode; }
    public LocalDate getCreditNoteDate() { return creditNoteDate; }
    public void setCreditNoteDate(LocalDate creditNoteDate) { this.creditNoteDate = creditNoteDate; }
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
    public String getPlaceOfSupply() { return placeOfSupply; }
    public void setPlaceOfSupply(String placeOfSupply) { this.placeOfSupply = placeOfSupply; }
    public Boolean getIsInterState() { return isInterState; }
    public void setIsInterState(Boolean isInterState) { this.isInterState = isInterState; }
    public String getInvoiceType() { return invoiceType; }
    public void setInvoiceType(String invoiceType) { this.invoiceType = invoiceType; }
    public String getNoteType() { return noteType; }
    public void setNoteType(String noteType) { this.noteType = noteType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<CreditNoteItem> getItems() { return items; }
    public void setItems(List<CreditNoteItem> items) { this.items = items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
