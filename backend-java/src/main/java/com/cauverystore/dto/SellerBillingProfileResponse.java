package com.cauverystore.dto;

import com.cauverystore.entities.SellerRegistration;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The seller identity the billing engine needs, in the agreed payload shape.
 *
 * Distinct from returning the SellerRegistration entity, which the status endpoint does. That
 * entity carries bank details, Aadhaar, document URLs and verification state - none of which
 * belongs in a billing payload, and some of which should not travel at all. This exposes the
 * six fields billing actually consumes and nothing else.
 *
 * Named in snake_case because that is the contract; the entity's own JSON stays camelCase for
 * the existing screens.
 */
public class SellerBillingProfileResponse {

    @JsonProperty("seller_id")
    private String sellerId;

    @JsonProperty("business_name")
    private String businessName;

    @JsonProperty("gstin")
    private String gstin;

    @JsonProperty("business_type")
    private String businessType;

    @JsonProperty("business_category")
    private String businessCategory;

    @JsonProperty("business_address")
    private String businessAddress;

    public static SellerBillingProfileResponse from(SellerRegistration reg) {
        SellerBillingProfileResponse dto = new SellerBillingProfileResponse();
        if (reg == null) return dto;
        dto.sellerId = reg.getUser() != null && reg.getUser().getId() != null
                ? String.valueOf(reg.getUser().getId()) : null;
        dto.businessName = reg.getBusinessName();
        dto.gstin = reg.getGstin();
        dto.businessType = reg.getBusinessType();
        dto.businessCategory = reg.getBusinessCategory();
        // The full postal address as one string, which is how it prints on an invoice.
        dto.businessAddress = joinAddress(reg);
        return dto;
    }

    private static String joinAddress(SellerRegistration reg) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, reg.getBusinessAddress());
        appendPart(sb, reg.getCity());
        appendPart(sb, reg.getState());
        appendPart(sb, reg.getPincode());
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) return;
        if (sb.length() > 0) sb.append(", ");
        sb.append(part.trim());
    }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public String getBusinessCategory() { return businessCategory; }
    public void setBusinessCategory(String businessCategory) { this.businessCategory = businessCategory; }
    public String getBusinessAddress() { return businessAddress; }
    public void setBusinessAddress(String businessAddress) { this.businessAddress = businessAddress; }
}
