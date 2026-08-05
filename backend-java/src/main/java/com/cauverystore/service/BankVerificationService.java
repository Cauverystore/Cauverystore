package com.cauverystore.service;

import com.cauverystore.client.BankVerifier;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.repository.SellerRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class BankVerificationService {

    private final BankVerifier bankVerifier;
    private final SellerRegistrationRepository regRepo;
    private final AuditService auditService;

    public BankVerificationService(BankVerifier bankVerifier,
                                   SellerRegistrationRepository regRepo,
                                   AuditService auditService) {
        this.bankVerifier = bankVerifier;
        this.regRepo = regRepo;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> verifyBank(Long userId, String accountNumber, String ifsc, String accountName) {
        SellerRegistration reg = regRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Registration not found. Start registration first."));

        Map<String, Object> resp = bankVerifier.verify(accountNumber, ifsc, accountName);
        boolean verified = Boolean.TRUE.equals(resp.get("verified"));

        if (accountNumber != null) reg.setBankAccountNumber(accountNumber);
        if (ifsc != null) reg.setBankIfsc(ifsc);
        if (accountName != null) reg.setBankAccountName(accountName);

        reg.setBankStatus(verified ? "VERIFIED" : "FAILED");
        reg.setBankVerifiedAt(LocalDateTime.now());
        reg.setBankVerificationRef(resp.get("reference") != null ? resp.get("reference").toString() : null);
        regRepo.save(reg);

        auditService.log(userId, "seller:" + userId, "BANK_" + (verified ? "VERIFIED" : "FAILED"),
                "SellerRegistration", reg.getId(),
                "Bank verification (via " + bankVerifier.getName() + "): " + (verified ? "passed" : "failed"), null);

        Map<String, Object> result = new LinkedHashMap<>(resp);
        result.put("bankStatus", reg.getBankStatus());
        return result;
    }
}
