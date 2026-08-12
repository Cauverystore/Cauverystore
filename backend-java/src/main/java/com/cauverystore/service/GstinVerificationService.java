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
        boolean simulated = gstnClient.isSimulated();

        record.setStatus(valid ? "VERIFIED" : "FAILED");
        // A simulator does not know anybody's registered name, and writing the one it makes up
        // into the field that holds the real one leaves "SIMULATED LEGAL NAME" sitting in the
        // database looking like a fact somebody checked. Nothing is better than something false.
        record.setLegalName(simulated ? null : (String) gstnResp.get("legalName"));
        record.setTradeName(simulated ? null : (String) gstnResp.get("tradeName"));
        // The state code is the first two characters of the GSTIN itself, so it is true whoever
        // answered - it is read off the number, not looked up.
        record.setStateCode((String) gstnResp.get("stateCode"));
        // Prefixed by who actually answered. A reference beginning GSTN- reads as something the
        // portal issued and can be quoted back to it; for a simulated check there is no such
        // thing, and inventing one is how a made-up number ends up in a compliance file.
        record.setVerificationRef((simulated ? "SIM-" : "GSTN-")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        record.setSource(simulated ? "SIMULATED" : "GSTN");
        record.setVerifiedBy(userId);
        record.setVerifiedAt(LocalDateTime.now());
        gstinRepo.save(record);

        reg.setGstinStatus(valid ? "VERIFIED" : "FAILED");
        reg.setGstinVerifiedAt(LocalDateTime.now());
        reg.setGstinVerificationSource(simulated ? "SIMULATED" : "GSTN");
        reg.setGstinLegalName(simulated ? null : (String) gstnResp.get("legalName"));
        reg.setGstinStateCode((String) gstnResp.get("stateCode"));
        regRepo.save(reg);

        auditService.log(userId, "seller:" + userId, "GSTIN_" + (valid ? "VERIFIED" : "FAILED"),
                "SellerGstin", record.getId(), "GSTIN " + gstin.toUpperCase() + " verification: " + (valid ? "passed" : "failed"), null);

        Map<String, Object> result = new LinkedHashMap<>(gstnResp);
        result.put("gstinStatus", record.getStatus());
        result.put("verificationRef", record.getVerificationRef());
        result.put("verificationSource", record.getSource());
        result.put("simulated", simulated);
        if (simulated) {
            // Said to the seller too, not only to the admin. Somebody who watches their GSTIN go
            // green should not believe it has been checked against the register when it has not.
            result.put("verificationNote", "This GSTIN passed a format and check-digit test only. "
                    + "It has not been confirmed against the GST portal, so it has not been "
                    + "checked that the registration exists or is active.");
        }
        result.put("records", gstinRepo.findBySellerId(userId));
        return result;
    }
}
