package com.cauverystore.service;

import com.cauverystore.dto.SellerRegistrationRequest;
import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SellerRegistrationService {

    private final SellerRegistrationRepository regRepo;
    private final SellerDocumentRepository docRepo;
    private final SellerComplianceRepository complianceRepo;
    private final UserRepository userRepo;
    private final SellerStoreRepository storeRepo;
    private final SellerApobRepository apobRepo;
    private final AuditService auditService;

    public SellerRegistrationService(SellerRegistrationRepository regRepo,
                                     SellerDocumentRepository docRepo,
                                     SellerComplianceRepository complianceRepo,
                                     UserRepository userRepo,
                                     SellerStoreRepository storeRepo,
                                     SellerApobRepository apobRepo,
                                     AuditService auditService) {
        this.regRepo = regRepo;
        this.docRepo = docRepo;
        this.complianceRepo = complianceRepo;
        this.userRepo = userRepo;
        this.storeRepo = storeRepo;
        this.apobRepo = apobRepo;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> startRegistration(Long userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Optional<SellerRegistration> existing = regRepo.findByUserId(userId);
        if (existing.isPresent()) {
            return Map.of("registration", existing.get(), "step", existing.get().getOnboardingStep(), "status", existing.get().getStatus());
        }
        SellerRegistration reg = new SellerRegistration();
        reg.setUser(user);
        reg.setBusinessEmail(user.getEmail());
        reg.setContactPerson(user.getFullName());
        reg.setBusinessPhone(user.getPhone() != null ? user.getPhone() : "");
        reg.setStatus("DRAFT");
        reg.setOnboardingStep(1);
        regRepo.save(reg);
        createComplianceChecklist(user);
        return Map.of("registration", reg, "step", 1, "status", "DRAFT");
    }

    @Transactional
    public Map<String, Object> saveStep(Long userId, SellerRegistrationRequest req) {
        SellerRegistration reg = regRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Registration not found. Start registration first."));
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        int step = req.getOnboardingStep() != null ? req.getOnboardingStep() : 1;

        switch (step) {
            case 1:
                if (req.getBusinessName() != null) reg.setBusinessName(req.getBusinessName());
                if (req.getContactPerson() != null) reg.setContactPerson(req.getContactPerson());
                if (req.getBusinessEmail() != null) reg.setBusinessEmail(req.getBusinessEmail());
                if (req.getBusinessPhone() != null) reg.setBusinessPhone(req.getBusinessPhone());
                if (req.getBusinessAddress() != null) reg.setBusinessAddress(req.getBusinessAddress());
                if (req.getCity() != null) reg.setCity(req.getCity());
                if (req.getState() != null) reg.setState(req.getState());
                if (req.getPincode() != null) reg.setPincode(req.getPincode());
                if (req.getBusinessType() != null) reg.setBusinessType(req.getBusinessType());
                if (req.getBusinessCategory() != null) reg.setBusinessCategory(req.getBusinessCategory());
                if (req.getSignatureImageUrl() != null) reg.setSignatureImageUrl(req.getSignatureImageUrl());
                if (req.getAuthorisedSignatory() != null) reg.setAuthorisedSignatory(req.getAuthorisedSignatory());
                if (req.getBooksBeginningDate() != null && !req.getBooksBeginningDate().isBlank()) {
                    try {
                        reg.setBooksBeginningDate(java.time.LocalDate.parse(req.getBooksBeginningDate()));
                    } catch (java.time.format.DateTimeParseException e) {
                        throw new RuntimeException("Books beginning date must be a date, as YYYY-MM-DD.");
                    }
                }
                if (req.getWebsite() != null) reg.setWebsite(req.getWebsite());
                if (req.getProductCategories() != null) reg.setProductCategories(req.getProductCategories());
                if (req.getSocialMediaLinks() != null) reg.setSocialMediaLinks(req.getSocialMediaLinks());
                break;
            case 2:
                if (req.getGstin() != null) reg.setGstin(req.getGstin());
                if (req.getPanNumber() != null) reg.setPanNumber(req.getPanNumber());
                if (req.getAadhaarNumber() != null) reg.setAadhaarNumber(req.getAadhaarNumber());
                break;
            case 3:
                if (req.getBankAccountName() != null) reg.setBankAccountName(req.getBankAccountName());
                if (req.getBankAccountNumber() != null) reg.setBankAccountNumber(req.getBankAccountNumber());
                if (req.getBankIfsc() != null) reg.setBankIfsc(req.getBankIfsc());
                if (req.getBankName() != null) reg.setBankName(req.getBankName());
                if (req.getBankBranch() != null) reg.setBankBranch(req.getBankBranch());
                break;
            case 5:
                if ("true".equals(req.getAgreedToTerms())) {
                    reg.setStatus("SUBMITTED");
                    reg.setSubmittedAt(LocalDateTime.now());
                    // Role promotion to SELLER now happens only after admin approval.
                }
                break;
        }

        if (step > reg.getOnboardingStep()) {
            reg.setOnboardingStep(step);
        }
        regRepo.save(reg);
        auditService.log(userId, "seller:" + userId, "REGISTRATION_STEP_" + step, "SellerRegistration", reg.getId(), "Step " + step + " completed", null);
        return Map.of("registration", reg, "step", reg.getOnboardingStep(), "status", reg.getStatus());
    }

    @Transactional
    public Map<String, Object> submitRegistration(Long userId) {
        SellerRegistration reg = regRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Registration not found. Start registration first."));
        if ("APPROVED".equals(reg.getStatus())) {
            throw new RuntimeException("Registration is already approved");
        }
        if (reg.getBusinessName() == null || reg.getBusinessName().isBlank()) {
            throw new RuntimeException("Complete business details (step 1) before submitting");
        }
        if (reg.getGstin() == null || reg.getGstin().isBlank()) {
            throw new RuntimeException("GSTIN is required (step 2)");
        }
        if (reg.getBankAccountNumber() == null || reg.getBankAccountNumber().isBlank()) {
            throw new RuntimeException("Bank account details are required (step 3)");
        }
        reg.setStatus("SUBMITTED");
        reg.setSubmittedAt(LocalDateTime.now());
        reg.setOnboardingStep(5);
        regRepo.save(reg);
        auditService.log(userId, "seller:" + userId, "REGISTRATION_SUBMITTED", "SellerRegistration", reg.getId(),
                "Seller registration submitted for admin approval", null);
        return Map.of("registration", reg, "step", reg.getOnboardingStep(), "status", reg.getStatus());
    }

    @Transactional
    public Map<String, Object> saveApob(Long userId, com.cauverystore.entities.SellerApob apob) {
        SellerRegistration reg = regRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Registration not found. Start registration first."));
        if (apob.getAddress() == null || apob.getAddress().isBlank()) {
            throw new RuntimeException("APOB address is required");
        }
        apob.setSellerId(userId);
        apob.setGstin(reg.getGstin());
        if (apob.getState() != null && apob.getStateCode() == null) {
            apob.setStateCode(com.cauverystore.util.StateCodeUtil.stateNameToCode(apob.getState()));
        }
        apob.setStatus("PENDING");
        apobRepo.save(apob);
        auditService.log(userId, "seller:" + userId, "APOB_ADDED", "SellerApob", apob.getId(), "Added APOB", null);
        return Map.of("apob", apob, "message", "Additional Place of Business saved");
    }

    @Transactional
    public Map<String, Object> verifyApob(Long adminId, Long apobId, boolean approved, String note) {
        com.cauverystore.entities.SellerApob apob = apobRepo.findById(apobId)
                .orElseThrow(() -> new RuntimeException("APOB not found"));
        apob.setStatus(approved ? "VERIFIED" : "REJECTED");
        apob.setVerifiedAt(LocalDateTime.now());
        apobRepo.save(apob);
        auditService.log(adminId, "admin:" + adminId, "APOB_" + (approved ? "VERIFIED" : "REJECTED"), "SellerApob", apobId,
                note != null ? note : (approved ? "APOB verified" : "APOB rejected"), null);
        return Map.of("apob", apob, "message", "APOB " + (approved ? "verified" : "rejected"));
    }

    @Transactional
    public Map<String, Object> deleteApob(Long userId, Long apobId) {
        com.cauverystore.entities.SellerApob apob = apobRepo.findById(apobId)
                .orElseThrow(() -> new RuntimeException("APOB not found"));
        if (!apob.getSellerId().equals(userId)) {
            throw new RuntimeException("APOB does not belong to this seller");
        }
        apobRepo.delete(apob);
        return Map.of("message", "APOB deleted");
    }

    public List<com.cauverystore.entities.SellerApob> listApobs(Long userId) {
        return apobRepo.findBySellerIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public Map<String, Object> uploadDocument(Long userId, String documentType, String documentName, String fileUrl, String originalName, Long fileSize, String mimeType, LocalDate expiryDate) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        SellerDocument doc = new SellerDocument();
        doc.setUser(user);
        doc.setDocumentType(documentType);
        doc.setDocumentName(documentName);
        doc.setFileUrl(fileUrl);
        doc.setFileOriginalName(originalName);
        doc.setFileSize(fileSize);
        doc.setMimeType(mimeType);
        doc.setStatus("PENDING");
        doc.setExpiryDate(expiryDate);
        docRepo.save(doc);

        updateComplianceForDocument(userId, documentType, doc.getId());
        auditService.log(userId, "seller:" + userId, "DOCUMENT_UPLOAD", "SellerDocument", doc.getId(), "Uploaded " + documentType, null);
        return Map.of("document", doc, "message", documentType + " uploaded successfully");
    }

    @Transactional
    public Map<String, Object> verifyDocument(Long adminId, Long documentId, boolean approved, String rejectionReason) {
        SellerDocument doc = docRepo.findById(documentId).orElseThrow(() -> new RuntimeException("Document not found"));
        doc.setStatus(approved ? "VERIFIED" : "REJECTED");
        doc.setVerifiedBy(adminId);
        doc.setVerifiedAt(LocalDateTime.now());
        if (rejectionReason != null) doc.setRejectionReason(rejectionReason);
        docRepo.save(doc);
        if (approved) {
            updateComplianceForDocument(doc.getUser().getId(), doc.getDocumentType(), doc.getId());
        }
        auditService.log(adminId, "admin:" + adminId, "DOCUMENT_" + (approved ? "VERIFIED" : "REJECTED"), "SellerDocument", documentId,
                (approved ? "Verified " : "Rejected ") + doc.getDocumentType() + (rejectionReason != null ? ": " + rejectionReason : ""), null);
        return Map.of("status", doc.getStatus(), "message", "Document " + (approved ? "verified" : "rejected"));
    }

    public Map<String, Object> getRegistrationStatus(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Optional<SellerRegistration> reg = regRepo.findByUserId(userId);
        if (reg.isEmpty()) {
            result.put("registered", false);
            result.put("step", 0);
            result.put("status", "NOT_STARTED");
            return result;
        }
        SellerRegistration r = reg.get();
        result.put("registered", true);
        result.put("registration", r);
        result.put("step", r.getOnboardingStep());
        result.put("status", r.getStatus());

        List<SellerDocument> docs = docRepo.findByUserId(userId);
        result.put("documents", docs);
        long verifiedDocs = docs.stream().filter(d -> "VERIFIED".equals(d.getStatus())).count();
        long pendingDocs = docs.stream().filter(d -> "PENDING".equals(d.getStatus())).count();
        long rejectedDocs = docs.stream().filter(d -> "REJECTED".equals(d.getStatus())).count();
        result.put("verifiedDocuments", verifiedDocs);
        result.put("pendingDocuments", pendingDocs);
        result.put("rejectedDocuments", rejectedDocs);

        List<SellerCompliance> compliance = complianceRepo.findByUserId(userId);
        result.put("compliance", compliance);
        long completed = compliance.stream().filter(SellerCompliance::getIsCompleted).count();
        long total = compliance.size();
        result.put("complianceCompleted", completed);
        result.put("complianceTotal", total);

        Optional<SellerStore> store = storeRepo.findBySellerId(userId);
        store.ifPresent(s -> result.put("store", s));

        List<Map<String, Object>> expiryAlerts = new ArrayList<>();
        for (SellerDocument d : docs) {
            if (d.getExpiryDate() != null && d.getExpiryDate().isBefore(LocalDate.now().plusDays(30))) {
                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("documentType", d.getDocumentType());
                alert.put("documentName", d.getDocumentName());
                alert.put("expiryDate", d.getExpiryDate().toString());
                alert.put("daysRemaining", LocalDate.now().until(d.getExpiryDate()).getDays());
                expiryAlerts.add(alert);
            }
        }
        result.put("expiryAlerts", expiryAlerts);
        return result;
    }

    public Map<String, Object> getPendingVerifications(int page, int size) {
        List<SellerDocument> pending = new ArrayList<>();
        for (SellerDocument d : docRepo.findAll()) {
            if ("PENDING".equals(d.getStatus())) pending.add(d);
        }
        int start = page * size;
        int end = Math.min(start + size, pending.size());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", start < pending.size() ? pending.subList(start, end) : List.of());
        resp.put("totalElements", pending.size());
        resp.put("totalPages", (int) Math.ceil((double) pending.size() / size));
        resp.put("page", page);
        return resp;
    }

    public List<Map<String, Object>> getComplianceAlerts(Long userId) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        List<SellerCompliance> incomplete = complianceRepo.findByUserIdAndIsCompletedFalse(userId);
        for (SellerCompliance c : incomplete) {
            if (Boolean.TRUE.equals(c.getIsMandatory())) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("type", "MISSING_REQUIREMENT");
                a.put("severity", "critical");
                a.put("requirement", c.getRequirementName());
                a.put("message", c.getRequirementName() + " is mandatory and not yet completed");
                alerts.add(a);
            }
        }
        List<SellerDocument> docs = docRepo.findByUserId(userId);
        for (SellerDocument d : docs) {
            if (d.getExpiryDate() != null && d.getExpiryDate().isBefore(LocalDate.now().plusDays(30))) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("type", "DOCUMENT_EXPIRY");
                a.put("severity", d.getExpiryDate().isBefore(LocalDate.now()) ? "critical" : "warning");
                a.put("documentType", d.getDocumentType());
                a.put("expiryDate", d.getExpiryDate().toString());
                a.put("message", d.getDocumentName() + " expires on " + d.getExpiryDate());
                alerts.add(a);
            }
        }
        return alerts;
    }

    private void createComplianceChecklist(User user) {
        Long userId = user.getId();
        List<SellerCompliance> items = new ArrayList<>();

        items.add(createComplianceItem(user, "GST_REGISTRATION", "GST Registration",
                "Mandatory for taxable turnover above threshold. Required for all business types.", true, "all"));
        items.add(createComplianceItem(user, "PAN_CARD", "PAN Card",
                "Mandatory for tax compliance and KYC. Required for all sellers.", true, "all"));
        items.add(createComplianceItem(user, "BUSINESS_REGISTRATION", "Business Registration Certificate",
                "Certificate of Incorporation (LLP/Pvt Ltd) or Partnership Deed or Proprietorship declaration.", true, "all"));
        items.add(createComplianceItem(user, "BANK_ACCOUNT", "Bank Account Verification",
                "Verified bank account in the business name for payouts.", true, "all"));
        items.add(createComplianceItem(user, "FSSAI_LICENSE", "FSSAI License",
                "Mandatory for food products, groceries, and kitchen essentials.", true, "food,grocery"));
        items.add(createComplianceItem(user, "BIS_ISI_CERTIFICATION", "BIS/ISI Certification",
                "Mandatory for electronics, appliances, and electrical products.", true, "electronics,appliances"));
        items.add(createComplianceItem(user, "DRUG_LICENSE", "Drug License",
                "Mandatory for pharmaceuticals, cosmetics, and health supplements.", true, "pharma,cosmetics"));
        items.add(createComplianceItem(user, "TRADEMARK", "Trademark Certificate",
                "Recommended for branded products to protect intellectual property.", false, "branded"));
        items.add(createComplianceItem(user, "IEC_CODE", "Import/Export Code",
                "Required if selling imported goods or exporting products.", false, "import,export"));
        items.add(createComplianceItem(user, "MSME_CERTIFICATE", "MSME/Udyam Registration",
                "For small and medium enterprises. Optional but provides benefits.", false, "all"));
        items.add(createComplianceItem(user, "ADHAAR_VERIFICATION", "Aadhaar Verification",
                "For individual sellers or proprietorships. Used for KYC.", true, "individual,proprietorship"));

        complianceRepo.saveAll(items);
    }

    private SellerCompliance createComplianceItem(User user, String type, String name, String desc, boolean mandatory, String categories) {
        SellerCompliance c = new SellerCompliance();
        c.setUser(user);
        c.setRequirementType(type);
        c.setRequirementName(name);
        c.setDescription(desc);
        c.setIsMandatory(mandatory);
        c.setIsRequired(true);
        c.setIsCompleted(false);
        c.setStatus("PENDING");
        c.setApplicableCategories(categories);
        return c;
    }

    private void updateComplianceForDocument(Long userId, String documentType, Long documentId) {
        complianceRepo.findByUserIdAndRequirementType(userId, documentType).ifPresent(c -> {
            c.setIsCompleted(true);
            c.setCompletedDate(LocalDate.now());
            c.setStatus("COMPLETED");
            c.setDocumentId(documentId);
            complianceRepo.save(c);
        });
    }
}
