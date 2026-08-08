package com.cauverystore.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
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

    /**
     * TCS under section 52, as a percentage of net taxable supplies.
     *
     * 0.5% since 10-07-2024 - Notification 15/2024-Central Tax halved it, so it is now 0.25%
     * CGST plus 0.25% SGST intra-state, or 0.5% IGST inter-state. It was 1% before that, and
     * this field defaulted to the old figure, which would have withheld twice what is due from
     * every seller's settlement and over-declared the same amount in GSTR-8.
     *
     * Configurable because the rate is set by notification and has already changed once.
     * Invoices keep their own copy, so correcting this never rewrites what was collected before.
     */
    @Column(nullable = false)
    private Double tcsRate = 0.5;

    /**
     * The marketplace's TCS registration under s.52 - a SEPARATE GSTIN from the regular one.
     *
     * GSTR-8 is filed against this, not against {@link #gstin}. Filing under the regular
     * registration is rejected, and the rejection is discovered at the deadline.
     */
    private String tcsGstin;

    private LocalDate tcsRegistrationDate;

    /** 21-character MCA corporate identity number. Printed on the marketplace's own invoices. */
    private String cin;

    private String pan;

    /**
     * The nodal / escrow account. Money collected on behalf of sellers is held here, not owned -
     * keeping it distinct from the marketplace's own account is what makes settlement
     * reconcilable and is expected of a marketplace holding third-party funds.
     */
    private String nodalBankName;
    private String nodalAccountNumber;
    private String nodalIfsc;

    /** SAC for the marketplace's own commission service, and the GST it attracts. */
    private String commissionSacCode = "998599";
    private Double commissionGstRate = 18.0;
    private Double defaultCommissionRate = 0.0;

    @Column(nullable = false)
    private String invoicePrefix = "CS";

    private Long sellerId;

    private Double annualTurnover;

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
    public String getTcsGstin() { return tcsGstin; }
    public void setTcsGstin(String tcsGstin) { this.tcsGstin = tcsGstin; }
    public LocalDate getTcsRegistrationDate() { return tcsRegistrationDate; }
    public void setTcsRegistrationDate(LocalDate d) { this.tcsRegistrationDate = d; }
    public String getCin() { return cin; }
    public void setCin(String cin) { this.cin = cin; }
    public String getPan() { return pan; }
    public void setPan(String pan) { this.pan = pan; }
    public String getNodalBankName() { return nodalBankName; }
    public void setNodalBankName(String nodalBankName) { this.nodalBankName = nodalBankName; }
    public String getNodalAccountNumber() { return nodalAccountNumber; }
    public void setNodalAccountNumber(String n) { this.nodalAccountNumber = n; }
    public String getNodalIfsc() { return nodalIfsc; }
    public void setNodalIfsc(String nodalIfsc) { this.nodalIfsc = nodalIfsc; }
    public String getCommissionSacCode() { return commissionSacCode; }
    public void setCommissionSacCode(String c) { this.commissionSacCode = c; }
    public Double getCommissionGstRate() { return commissionGstRate; }
    public void setCommissionGstRate(Double r) { this.commissionGstRate = r; }
    public Double getDefaultCommissionRate() { return defaultCommissionRate; }
    public void setDefaultCommissionRate(Double r) { this.defaultCommissionRate = r; }

    /**
     * The GSTIN GSTR-8 must be filed against.
     *
     * Deliberately returns empty rather than falling back to the regular GSTIN when the TCS
     * registration is unset. A silent fallback would file the return under the wrong
     * registration, which is rejected - and rejected at the deadline, when there is no time to
     * fix it.
     */
    @jakarta.persistence.Transient
    public java.util.Optional<String> resolveTcsGstin() {
        if (tcsGstin == null || tcsGstin.isBlank()) return java.util.Optional.empty();
        if (tcsGstin.equalsIgnoreCase(gstin)) return java.util.Optional.empty();
        return java.util.Optional.of(tcsGstin);
    }

    public Double getTcsRate() { return tcsRate; }
    public void setTcsRate(Double tcsRate) { this.tcsRate = tcsRate; }
    public String getInvoicePrefix() { return invoicePrefix; }
    public void setInvoicePrefix(String invoicePrefix) { this.invoicePrefix = invoicePrefix; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public Double getAnnualTurnover() { return annualTurnover; }
    public void setAnnualTurnover(Double annualTurnover) { this.annualTurnover = annualTurnover; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
