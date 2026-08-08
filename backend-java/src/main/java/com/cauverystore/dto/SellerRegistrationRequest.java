package com.cauverystore.dto;

/**
 * Accepts both the camelCase the screens send and the snake_case of the published payload
 * contract, via @JsonAlias. One name would have meant either breaking the existing frontend or
 * refusing the documented shape.
 */
public class SellerRegistrationRequest {
    @com.fasterxml.jackson.annotation.JsonAlias("business_name")
    private String businessName;
    private String contactPerson;
    @com.fasterxml.jackson.annotation.JsonAlias("business_email")
    private String businessEmail;
    @com.fasterxml.jackson.annotation.JsonAlias("business_phone")
    private String businessPhone;
    @com.fasterxml.jackson.annotation.JsonAlias("business_address")
    private String businessAddress;
    private String city;
    private String state;
    private String pincode;
    @com.fasterxml.jackson.annotation.JsonAlias("business_type")
    private String businessType;
    @com.fasterxml.jackson.annotation.JsonAlias("business_category")
    private String businessCategory;
    @com.fasterxml.jackson.annotation.JsonAlias("constitution_of_business")
    private String constitutionOfBusiness;
    @com.fasterxml.jackson.annotation.JsonAlias("books_beginning_date")
    private String booksBeginningDate;
    @com.fasterxml.jackson.annotation.JsonAlias("signature_image_url")
    private String signatureImageUrl;
    @com.fasterxml.jackson.annotation.JsonAlias("authorised_signatory")
    private String authorisedSignatory;
    private String gstin;

    /** Section 10(2)(d): a composition taxpayer cannot sell through a TCS-collecting operator. */
    private Boolean compositionScheme;
    @com.fasterxml.jackson.annotation.JsonAlias("pan_number")
    private String panNumber;
    private String aadhaarNumber;
    private String bankAccountName;
    private String bankAccountNumber;
    private String bankIfsc;
    private String bankName;
    private String bankBranch;
    private String website;
    private String socialMediaLinks;
    private String productCategories;
    private Integer onboardingStep;
    private String agreedToTerms;

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getBusinessEmail() { return businessEmail; }
    public void setBusinessEmail(String businessEmail) { this.businessEmail = businessEmail; }
    public String getBusinessPhone() { return businessPhone; }
    public void setBusinessPhone(String businessPhone) { this.businessPhone = businessPhone; }
    public String getBusinessAddress() { return businessAddress; }
    public void setBusinessAddress(String businessAddress) { this.businessAddress = businessAddress; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public String getConstitutionOfBusiness() { return constitutionOfBusiness; }
    public void setConstitutionOfBusiness(String constitutionOfBusiness) { this.constitutionOfBusiness = constitutionOfBusiness; }
    public String getBusinessCategory() { return businessCategory; }
    public void setBusinessCategory(String businessCategory) { this.businessCategory = businessCategory; }
    public String getBooksBeginningDate() { return booksBeginningDate; }
    public void setBooksBeginningDate(String booksBeginningDate) { this.booksBeginningDate = booksBeginningDate; }
    public String getSignatureImageUrl() { return signatureImageUrl; }
    public void setSignatureImageUrl(String signatureImageUrl) { this.signatureImageUrl = signatureImageUrl; }
    public String getAuthorisedSignatory() { return authorisedSignatory; }
    public void setAuthorisedSignatory(String authorisedSignatory) { this.authorisedSignatory = authorisedSignatory; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public Boolean getCompositionScheme() { return compositionScheme; }
    public void setCompositionScheme(Boolean compositionScheme) { this.compositionScheme = compositionScheme; }
    public String getPanNumber() { return panNumber; }
    public void setPanNumber(String panNumber) { this.panNumber = panNumber; }
    public String getAadhaarNumber() { return aadhaarNumber; }
    public void setAadhaarNumber(String aadhaarNumber) { this.aadhaarNumber = aadhaarNumber; }
    public String getBankAccountName() { return bankAccountName; }
    public void setBankAccountName(String bankAccountName) { this.bankAccountName = bankAccountName; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }
    public String getBankIfsc() { return bankIfsc; }
    public void setBankIfsc(String bankIfsc) { this.bankIfsc = bankIfsc; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getBankBranch() { return bankBranch; }
    public void setBankBranch(String bankBranch) { this.bankBranch = bankBranch; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getSocialMediaLinks() { return socialMediaLinks; }
    public void setSocialMediaLinks(String socialMediaLinks) { this.socialMediaLinks = socialMediaLinks; }
    public String getProductCategories() { return productCategories; }
    public void setProductCategories(String productCategories) { this.productCategories = productCategories; }
    public Integer getOnboardingStep() { return onboardingStep; }
    public void setOnboardingStep(Integer onboardingStep) { this.onboardingStep = onboardingStep; }
    public String getAgreedToTerms() { return agreedToTerms; }
    public void setAgreedToTerms(String agreedToTerms) { this.agreedToTerms = agreedToTerms; }
}
