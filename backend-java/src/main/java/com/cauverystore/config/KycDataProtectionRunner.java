package com.cauverystore.config;

import com.cauverystore.util.KycCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Brings seller rows saved before KYC protection existed up to date, on every start.
 *
 * Full Aadhaar numbers are cut down to the last four digits whether or not a key is set - that
 * needs no key, and holding the full number is the problem. Plain bank account numbers are
 * encrypted once KYC_ENCRYPTION_KEY is set. Both steps skip rows already done, so running again
 * changes nothing.
 */
@Component
public class KycDataProtectionRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KycDataProtectionRunner.class);

    private final JdbcTemplate jdbc;
    private final KycCrypto crypto;

    public KycDataProtectionRunner(JdbcTemplate jdbc, KycCrypto crypto) {
        this.jdbc = jdbc;
        this.crypto = crypto;
    }

    @Override
    public void run(String... args) {
        try {
            int aadhaar = 0;
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, aadhaar_number FROM seller_registrations WHERE aadhaar_number IS NOT NULL AND aadhaar_number NOT LIKE 'XXXX XXXX %'");
            for (Map<String, Object> r : rows) {
                String masked = KycCrypto.maskAadhaar((String) r.get("aadhaar_number"));
                aadhaar += jdbc.update("UPDATE seller_registrations SET aadhaar_number = ? WHERE id = ?", masked, r.get("id"));
            }

            int bank = 0;
            if (crypto.isConfigured()) {
                rows = jdbc.queryForList(
                        "SELECT id, bank_account_number FROM seller_registrations WHERE bank_account_number IS NOT NULL "
                                + "AND bank_account_number <> '' AND bank_account_number NOT LIKE 'KYC1:%'");
                for (Map<String, Object> r : rows) {
                    String enc = crypto.encrypt((String) r.get("bank_account_number"));
                    bank += jdbc.update("UPDATE seller_registrations SET bank_account_number = ? WHERE id = ?", enc, r.get("id"));
                }
            }
            if (aadhaar > 0 || bank > 0) {
                log.info("KYC protection: masked {} Aadhaar number(s), encrypted {} bank account number(s).", aadhaar, bank);
            }
        } catch (Exception e) {
            // Never stop the store starting over this; the next start tries again.
            log.error("KYC protection pass failed: {}", e.getMessage());
        }
    }
}
