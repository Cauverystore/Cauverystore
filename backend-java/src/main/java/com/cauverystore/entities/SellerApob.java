package com.cauverystore.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "seller_apobs")
public class SellerApob extends BaseEntity {

    @Column(nullable = false)
    private Long sellerId;

    private String gstin;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String address;

    private String city;
    private String state;
    private String pincode;
    private String stateCode;

    private Boolean isWarehouse = false;

    private Long warehouseId;

    @Column(nullable = false)
    private String status = "PENDING";

    private LocalDateTime verifiedAt;

    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public Boolean getIsWarehouse() { return isWarehouse; }
    public void setIsWarehouse(Boolean isWarehouse) { this.isWarehouse = isWarehouse; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
}
