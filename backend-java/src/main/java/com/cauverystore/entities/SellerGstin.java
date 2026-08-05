package com.cauverystore.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "seller_gstins")
public class SellerGstin extends BaseEntity {

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 15)
    private String gstin;

    private Boolean primaryFlag = true;

    @Column(nullable = false)
    private String status = "UNVERIFIED";

    private String legalName;

    private String tradeName;

    private String stateCode;

    private String verificationRef;

    private LocalDateTime verifiedAt;

    private Long verifiedBy;

    @Column(nullable = false)
    private String source = "SIMULATED";

    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public Boolean getPrimaryFlag() { return primaryFlag; }
    public void setPrimaryFlag(Boolean primaryFlag) { this.primaryFlag = primaryFlag; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getTradeName() { return tradeName; }
    public void setTradeName(String tradeName) { this.tradeName = tradeName; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public String getVerificationRef() { return verificationRef; }
    public void setVerificationRef(String verificationRef) { this.verificationRef = verificationRef; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public Long getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(Long verifiedBy) { this.verifiedBy = verifiedBy; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
