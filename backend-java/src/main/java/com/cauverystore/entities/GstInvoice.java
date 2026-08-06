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

    @Column(columnDefinition = "TEXT")
    private String irn;
    @Column(columnDefinition = "TEXT")
    private String qrCode;
    @Column(columnDefinition = "TEXT")
    private String ackNo;
    @Column(columnDefinition = "TEXT")
    private String ackDate;

    @Column(nullable = false)
    private Long orderId;

    private Long sellerId;

    private Long customerId;

    @Column(columnDefinition = "TEXT")
    private String customerEmail;

    @Column(nullable = false)
    private String sellerGstin;

    @Column(nullable = false)
    private String buyerGstin;

    @Column(columnDefinition = "TEXT")
    private String buyerName;
    @Column(columnDefinition = "TEXT")
    private String buyerAddress;
    private String buyerStateCode;

    @Column(columnDefinition = "TEXT")
    private String deliveryAddress;
    private String deliveryStateCode;

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

    @Column(columnDefinition = "TEXT")
    private String placeOfSupply;
    private Boolean isInterState = false;

    private String ewayBillNumber;
    private LocalDate ewayBillExpiry;

    private String transporterName;
    private String transporterGstin;
    private String transporterId;
    private String vehicleNumber;
    private String vehicleType = "R";
    private String transportMode = "ROAD";
    private Double distanceKm;
    private LocalDate journeyStartDate;
    private LocalDate journeyEndDate;

    private String status = "DRAFT";

    private String invoiceStatus = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String syncError;

    private Integer syncAttempts = 0;
    private LocalDateTime lastSyncAttempt;

    private Boolean reverseCharge = false;
    private String invoiceCopyType;
    private String supplyType = "GOODS";
    @Column(columnDefinition = "TEXT")
    private String sellerLegalName;
    @Column(columnDefinition = "TEXT")
    private String sellerAddress;
    private Integer hsnDigits = 4;
    private String invoiceType = "B2C";
    private Boolean itcEligible = false;

    /** True for B2C inter-state invoices whose taxable value exceeds ₹1,00,000 (GSTR-1 6A / B2CL). */
    private Boolean b2cLarge = false;

    private Double discountAmount;
    private Double deliveryCharge;
    private Boolean einvoicingRequired = false;
    @Column(columnDefinition = "TEXT")
    private String supplierSignature;
    @Column(columnDefinition = "TEXT")
    private String signedBy;
    private LocalDate signatureDate;

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
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
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
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public String getDeliveryStateCode() { return deliveryStateCode; }
    public void setDeliveryStateCode(String deliveryStateCode) { this.deliveryStateCode = deliveryStateCode; }
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
    public String getTransporterName() { return transporterName; }
    public void setTransporterName(String transporterName) { this.transporterName = transporterName; }
    public String getTransporterGstin() { return transporterGstin; }
    public void setTransporterGstin(String transporterGstin) { this.transporterGstin = transporterGstin; }
    public String getTransporterId() { return transporterId; }
    public void setTransporterId(String transporterId) { this.transporterId = transporterId; }
    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public String getTransportMode() { return transportMode; }
    public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    public LocalDate getJourneyStartDate() { return journeyStartDate; }
    public void setJourneyStartDate(LocalDate journeyStartDate) { this.journeyStartDate = journeyStartDate; }
    public LocalDate getJourneyEndDate() { return journeyEndDate; }
    public void setJourneyEndDate(LocalDate journeyEndDate) { this.journeyEndDate = journeyEndDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInvoiceStatus() { return invoiceStatus; }
    public void setInvoiceStatus(String invoiceStatus) { this.invoiceStatus = invoiceStatus; }
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
    public Boolean getItcEligible() { return itcEligible; }
    public void setItcEligible(Boolean itcEligible) { this.itcEligible = itcEligible; }
    public Boolean getB2cLarge() { return b2cLarge; }
    public void setB2cLarge(Boolean b2cLarge) { this.b2cLarge = b2cLarge; }
    public Double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(Double discountAmount) { this.discountAmount = discountAmount; }
    public Double getDeliveryCharge() { return deliveryCharge; }
    public void setDeliveryCharge(Double deliveryCharge) { this.deliveryCharge = deliveryCharge; }
    public Boolean getEinvoicingRequired() { return einvoicingRequired; }
    public void setEinvoicingRequired(Boolean einvoicingRequired) { this.einvoicingRequired = einvoicingRequired; }
    public String getSupplierSignature() { return supplierSignature; }
    public void setSupplierSignature(String supplierSignature) { this.supplierSignature = supplierSignature; }
    public String getSignedBy() { return signedBy; }
    public void setSignedBy(String signedBy) { this.signedBy = signedBy; }
    public LocalDate getSignatureDate() { return signatureDate; }
    public void setSignatureDate(LocalDate signatureDate) { this.signatureDate = signatureDate; }
}
