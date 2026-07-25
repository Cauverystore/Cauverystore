package com.cauverystore.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "gst_configurations")
public class GstConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String gstin;

    @Column(nullable = false)
    private String legalName;

    private String tradeName;
    private String stateCode;
    private String stateName;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(columnDefinition = "TEXT")
    private String gstnUsername;

    @Column(columnDefinition = "TEXT")
    private String gstnPassword;

    @Column(columnDefinition = "TEXT")
    private String gstnApiKey;

    private String gstnEndpoint = "https://api.gstn.gov.in";
    private Boolean isActive = true;

    private String einvoiceApiKey;
    private String einvoiceEndpoint = "https://api.einvoice.gstn.gov.in";

    private String ewaybillApiKey;
    private String ewaybillEndpoint = "https://api.ewaybill.gstn.gov.in";

    @Column(nullable = false)
    private Double tcsRate = 1.0;

    @Column(nullable = false)
    private String invoicePrefix = "CS";

    private Long sellerId;

    private String createdBy;
    private LocalDateTime createdAt;
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
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getTradeName() { return tradeName; }
    public void setTradeName(String tradeName) { this.tradeName = tradeName; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public String getStateName() { return stateName; }
    public void setStateName(String stateName) { this.stateName = stateName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getGstnUsername() { return gstnUsername; }
    public void setGstnUsername(String gstnUsername) { this.gstnUsername = gstnUsername; }
    public String getGstnPassword() { return gstnPassword; }
    public void setGstnPassword(String gstnPassword) { this.gstnPassword = gstnPassword; }
    public String getGstnApiKey() { return gstnApiKey; }
    public void setGstnApiKey(String gstnApiKey) { this.gstnApiKey = gstnApiKey; }
    public String getGstnEndpoint() { return gstnEndpoint; }
    public void setGstnEndpoint(String gstnEndpoint) { this.gstnEndpoint = gstnEndpoint; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public String getEinvoiceApiKey() { return einvoiceApiKey; }
    public void setEinvoiceApiKey(String einvoiceApiKey) { this.einvoiceApiKey = einvoiceApiKey; }
    public String getEinvoiceEndpoint() { return einvoiceEndpoint; }
    public void setEinvoiceEndpoint(String einvoiceEndpoint) { this.einvoiceEndpoint = einvoiceEndpoint; }
    public String getEwaybillApiKey() { return ewaybillApiKey; }
    public void setEwaybillApiKey(String ewaybillApiKey) { this.ewaybillApiKey = ewaybillApiKey; }
    public String getEwaybillEndpoint() { return ewaybillEndpoint; }
    public void setEwaybillEndpoint(String ewaybillEndpoint) { this.ewaybillEndpoint = ewaybillEndpoint; }
    public Double getTcsRate() { return tcsRate; }
    public void setTcsRate(Double tcsRate) { this.tcsRate = tcsRate; }
    public String getInvoicePrefix() { return invoicePrefix; }
    public void setInvoicePrefix(String invoicePrefix) { this.invoicePrefix = invoicePrefix; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
