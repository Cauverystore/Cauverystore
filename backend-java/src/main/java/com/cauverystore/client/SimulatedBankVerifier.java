package com.cauverystore.client;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Simulated penny-drop verification: accepts an IFSC matching the standard
 * 4-letter + 7-digit format and an 9-18 digit account number. Swap this bean
 * with a real provider (Razorpay, bank API) behind a feature flag for live use.
 */
@Component
public class SimulatedBankVerifier implements BankVerifier {

    private static final Pattern IFSC_PATTERN = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^\\d{9,18}$");

    @Override
    public String getName() {
        return "PENNY_DROP_SIMULATED";
    }

    @Override
    public Map<String, Object> verify(String accountNumber, String ifsc, String accountName) {
        Map<String, Object> resp = new LinkedHashMap<>();
        boolean valid = accountNumber != null && ACCOUNT_PATTERN.matcher(accountNumber).matches()
                && ifsc != null && IFSC_PATTERN.matcher(ifsc).matches();
        resp.put("accountNumber", accountNumber);
        resp.put("ifsc", ifsc);
        resp.put("validFormat", valid);
        resp.put("verified", valid);
        resp.put("accountName", accountName != null ? accountName : "SIMULATED ACCOUNT HOLDER");
        resp.put("bankName", valid ? "SIMULATED BANK" : null);
        resp.put("reference", "PENNY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        resp.put("simulated", true);
        resp.put("message", valid ? "Bank account verified (simulated)." : "Invalid bank account or IFSC.");
        return resp;
    }
}
