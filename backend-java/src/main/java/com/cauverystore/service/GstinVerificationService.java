package com.cauverystore.service;

import com.cauverystore.client.BankVerifier;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.util.GstComplianceUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GSTIN verification: format validation plus the pluggable GSTN client (the
 * live GSTN adapter or the simulated one). The result is persisted on both the
 * SellerGstin entity (per-GSTIN record) and SellerRegistration (status column)
 * so the approval workflow can gate activation on a VERIFIED GSTIN.
 */
@Service
public class GstinVerificationService {

    private final com.cauverystore.repository.SellerGstinRepository gstinRepo;
    private final SellerRegistrationRepository regRepo;
    private final com.cauverystore.client.GstnClient gstnClient;
    private final AuditService auditService;

    public GstinVerificationService(com.cauverystore.repository.SellerGstinRepository gstinRepo,
                                    SellerRegistrationRepository regRepo,
                                    com.cauverystore.client.GstnClient gstnClient,
                                    AuditService auditService) {
        this.gstinRepo = gstinRepo;
        this.regRepo = regRepo;
        this.gstnClient = gstnClient;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> verifyGstin(Long userId, String gstin) {
        if (gstin == null || gstin.isBlank()) {
            throw new RuntimeException("GSTIN is required");
        }
        GstComplianceUtil.validateGstin(gstin);

        SellerRegistration reg = regRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Registration not found. Start registration first."));
        reg.setGstin(gstin.toUpperCase());

        Map<String, Object> gstnResp = gstnClient.validateGstin(gstin.toUpperCase());
        boolean valid = Boolean.TRUE.equals(gstnResp.get("valid"));

        com.cauverystore.entities.SellerGstin record = gstinRepo.findBySellerIdAndGstin(userId, gstin.toUpperCase())
                .orElseGet(() -> {
                    com.cauverystore.entities.SellerGstin sg = new com.cauverystore.entities.SellerGstin();
                    sg.setSellerId(userId);
                    sg.setGstin(gstin.toUpperCase());
                    return sg;
                });
        record.setStatus(valid ? "VERIFIED" : "FAILED");
        record.setLegalName((String) gstnResp.get("legalName"));
        record.setTradeName((String) gstnResp.get("tradeName"));
        record.setStateCode((String) gstnResp.get("stateCode"));
        record.setVerificationRef("GSTN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        record.setSource(gstnClient.isSimulated() ? "SIMULATED" : "GSTN");
        record.setVerifiedBy(userId);
        record.setVerifiedAt(LocalDateTime.now());
        gstinRepo.save(record);

        reg.setGstinStatus(valid ? "VERIFIED" : "FAILED");
        reg.setGstinVerifiedAt(LocalDateTime.now());
        reg.setGstinLegalName((String) gstnResp.get("legalName"));
        reg.setGstinStateCode((String) gstnResp.get("stateCode"));
        regRepo.save(reg);

        auditService.log(userId, "seller:" + userId, "GSTIN_" + (valid ? "VERIFIED" : "FAILED"),
                "SellerGstin", record.getId(), "GSTIN " + gstin.toUpperCase() + " verification: " + (valid ? "passed" : "failed"), null);

        Map<String, Object> result = new LinkedHashMap<>(gstnResp);
        result.put("gstinStatus", record.getStatus());
        result.put("verificationRef", record.getVerificationRef());
        result.put("records", gstinRepo.findBySellerId(userId));
        return result;
    }
}
