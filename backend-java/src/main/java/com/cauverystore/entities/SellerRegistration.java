package com.cauverystore.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "seller_registrations")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SellerRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String businessName;

    @Column(nullable = false)
    private String contactPerson;

    @Column(nullable = false)
    private String businessEmail;

    @Column(nullable = false)
    private String businessPhone;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String businessAddress;

    private String city;
    private String state;
    private String pincode;

    @Column(nullable = false)
    /**
     * How the seller trades - Retail, Wholesale, Distributor, Service, Manufacturing, Others.
     * See BusinessType.
     *
     * This column previously held the constitution of the business (Sole Proprietorship,
     * Partnership, LLP and so on), which is a different question entirely. Those values are
     * moved to constitutionOfBusiness on startup rather than being overwritten - see
     * SellerBusinessFieldMigrator.
     */
    private String businessType;

    /**
     * The legal constitution: Sole Proprietorship, Partnership, LLP, Private Limited and so on.
     *
     * Kept because it is on the GST registration and is not recoverable once lost, even though
     * the current onboarding form asks for trade type instead.
     */
    private String constitutionOfBusiness;

    /**
     * What the business does - Retailer, Wholesaler, Distributor, Manufacturer, Services.
     * Distinct from productCategories, which is what they sell: a manufacturer and a retailer
     * can list the same goods and are not the same kind of registrant.
     */
    private String businessCategory;

    /**
     * The day this seller's books open.
     *
     * An invoice cannot be dated before it - doing so would put a supply in a period whose
     * return has been filed, or in no period at all. It also anchors the financial year that
     * the invoice numbering series restarts on.
     */
    private LocalDate booksBeginningDate;

    /**
     * Signature of the supplier or their authorised representative, which Rule 46 requires on
     * a tax invoice. Stored as an uploaded image URL.
     *
     * Not the same thing as GstInvoice.supplierSignature, which is a digest binding the
     * amounts so an invoice cannot be altered unnoticed. That is tamper-evidence; this is the
     * signature the rule actually asks for. An e-invoice with an IRN needs neither, because
     * the portal signs it.
     */
    private String signatureImageUrl;

    /** Who the signature belongs to, printed beneath it. */
    private String authorisedSignatory;

    private String gstin;
    private String panNumber;
    private String aadhaarNumber;

    /**
     * Whether this seller is registered under the composition scheme.
     *
     * Section 10(2)(d) bars a composition taxpayer from supplying goods through an e-commerce
     * operator required to collect TCS - which this marketplace is. So the answer decides
     * whether they can trade here at all, and it has to be asked rather than assumed: a
     * composition dealer's GSTIN looks like any other, and nothing in the number reveals it.
     *
     * Null means unanswered, which is not the same as "no". An unanswered seller is one nobody
     * has checked, and the readiness screen says so rather than treating silence as clearance.
     */
    private Boolean compositionScheme;

    private String gstinStatus = "UNVERIFIED";
    private LocalDateTime gstinVerifiedAt;
    private String gstinLegalName;
    private String gstinStateCode;

    private String bankStatus = "UNVERIFIED";
    private LocalDateTime bankVerifiedAt;
    private String bankVerificationRef;

    @Column(columnDefinition = "TEXT")
    private String bankAccountName;

    @Column(columnDefinition = "TEXT")
    private String bankAccountNumber;

    private String bankIfsc;
    private String bankName;
    private String bankBranch;

    private String website;
    private String socialMediaLinks;
    private String productCategories;

    private String status = "DRAFT";
    private Integer onboardingStep = 1;

    private String registrationCertificateUrl;
    private Boolean registrationDocumentVerified = false;

    @Column(columnDefinition = "TEXT")
    private String licenses;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "seller_registration_apobs", joinColumns = @JoinColumn(name = "seller_registration_id"))
    @Column(name = "apob", columnDefinition = "TEXT")
    private List<String> apobList = new ArrayList<>();

    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private String rejectionReason;

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
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
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
    public LocalDate getBooksBeginningDate() { return booksBeginningDate; }
    public void setBooksBeginningDate(LocalDate booksBeginningDate) { this.booksBeginningDate = booksBeginningDate; }
    public String getSignatureImageUrl() { return signatureImageUrl; }
    public void setSignatureImageUrl(String signatureImageUrl) { this.signatureImageUrl = signatureImageUrl; }
    public String getAuthorisedSignatory() { return authorisedSignatory; }
    public void setAuthorisedSignatory(String authorisedSignatory) { this.authorisedSignatory = authorisedSignatory; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public Boolean getCompositionScheme() { return compositionScheme; }
    public void setCompositionScheme(Boolean compositionScheme) { this.compositionScheme = compositionScheme; }

    /** True only when the seller has said yes. Unanswered is not a no. */
    @jakarta.persistence.Transient
    public boolean isOnCompositionScheme() { return Boolean.TRUE.equals(compositionScheme); }
    public String getGstinStatus() { return gstinStatus; }
    public void setGstinStatus(String gstinStatus) { this.gstinStatus = gstinStatus; }
    public LocalDateTime getGstinVerifiedAt() { return gstinVerifiedAt; }
    public void setGstinVerifiedAt(LocalDateTime gstinVerifiedAt) { this.gstinVerifiedAt = gstinVerifiedAt; }
    public String getGstinLegalName() { return gstinLegalName; }
    public void setGstinLegalName(String gstinLegalName) { this.gstinLegalName = gstinLegalName; }
    public String getGstinStateCode() { return gstinStateCode; }
    public void setGstinStateCode(String gstinStateCode) { this.gstinStateCode = gstinStateCode; }
    public String getBankStatus() { return bankStatus; }
    public void setBankStatus(String bankStatus) { this.bankStatus = bankStatus; }
    public LocalDateTime getBankVerifiedAt() { return bankVerifiedAt; }
    public void setBankVerifiedAt(LocalDateTime bankVerifiedAt) { this.bankVerifiedAt = bankVerifiedAt; }
    public String getBankVerificationRef() { return bankVerificationRef; }
    public void setBankVerificationRef(String bankVerificationRef) { this.bankVerificationRef = bankVerificationRef; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getOnboardingStep() { return onboardingStep; }
    public void setOnboardingStep(Integer onboardingStep) { this.onboardingStep = onboardingStep; }
    public String getRegistrationCertificateUrl() { return registrationCertificateUrl; }
    public void setRegistrationCertificateUrl(String registrationCertificateUrl) { this.registrationCertificateUrl = registrationCertificateUrl; }
    public Boolean getRegistrationDocumentVerified() { return registrationDocumentVerified; }
    public void setRegistrationDocumentVerified(Boolean registrationDocumentVerified) { this.registrationDocumentVerified = registrationDocumentVerified; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getLicenses() { return licenses; }
    public void setLicenses(String licenses) { this.licenses = licenses; }
    public List<String> getApobList() { return apobList; }
    public void setApobList(List<String> apobList) { this.apobList = apobList; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
