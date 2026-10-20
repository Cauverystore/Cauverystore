package com.cauverystore.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encryption for sellers' identity and bank data: KYC document files and bank account numbers.
 *
 * Separate from CryptoUtil on purpose. CryptoUtil falls back to a development key that is
 * readable in this public repository, so anything it encrypts on a server without CRYPTO_KEY
 * is protected by nothing. This class has no fallback: without KYC_ENCRYPTION_KEY it refuses
 * to encrypt, and the seller is told the step cannot be saved yet, rather than their data being
 * stored under a key anyone can read.
 *
 * The key must never change once data is stored with it - rows written under the old key could
 * no longer be read.
 */
@Component
public class KycCrypto {

    private static final Logger log = LoggerFactory.getLogger(KycCrypto.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    static final String PREFIX = "KYC1:";

    /** JPA converters are created by Hibernate, not Spring, so they reach the bean through this. */
    private static volatile KycCrypto instance;

    private final SecretKey key;

    public KycCrypto(@Value("${kyc.encryption-key:${KYC_ENCRYPTION_KEY:}}") String secret) {
        if (secret == null || secret.isBlank()) {
            this.key = null;
            log.error("SECURITY: KYC_ENCRYPTION_KEY is not set. Sellers cannot save bank details or upload "
                    + "verification documents until it is. Generate one with: "
                    + "node -e \"console.log(require('crypto').randomBytes(32).toString('base64'))\"");
        } else {
            try {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                this.key = new SecretKeySpec(sha.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
            } catch (Exception e) {
                throw new IllegalStateException("Unable to initialise the KYC encryption key", e);
            }
        }
        instance = this;
    }

    public static KycCrypto get() {
        KycCrypto k = instance;
        if (k == null) throw new IllegalStateException("KYC encryption is not initialised");
        return k;
    }

    public boolean isConfigured() {
        return key != null;
    }

    /** Thrown when seller data would have to be stored without a usable key. */
    public static class NotConfiguredException extends IllegalStateException {
        public NotConfiguredException() {
            super("Seller verification storage is not set up yet, so this cannot be saved. "
                    + "Please try again later or contact support@cauverystore.in.");
        }
    }

    public byte[] encryptBytes(byte[] plain) {
        if (key == null) throw new NotConfiguredException();
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain);
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return combined;
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public byte[] decryptBytes(byte[] combined) {
        if (key == null) throw new NotConfiguredException();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, combined, 0, 12));
            return cipher.doFinal(combined, 12, combined.length - 12);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed - was KYC_ENCRYPTION_KEY changed?", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank() || plain.startsWith(PREFIX)) return plain;
        return PREFIX + Base64.getEncoder().encodeToString(encryptBytes(plain.getBytes(StandardCharsets.UTF_8)));
    }

    /** Values stored before encryption existed come back unchanged, so old rows keep working. */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return stored;
        return new String(decryptBytes(Base64.getDecoder().decode(stored.substring(PREFIX.length()))), StandardCharsets.UTF_8);
    }

    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    /** "XXXXXX1234": enough for a seller or admin to recognise the account, not to use it. */
    public static String maskAccount(String number) {
        if (number == null || number.isBlank()) return null;
        String digits = number.replaceAll("\\s", "");
        if (digits.length() <= 4) return digits;
        return "X".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

    /**
     * Keeps only the last four Aadhaar digits. The full number is not kept at all: a business that
     * is not authorised by UIDAI should not hold it.
     */
    public static String maskAadhaar(String value) {
        if (value == null) return null;
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty()) return value.isBlank() ? null : value;
        String last4 = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        return "XXXX XXXX " + last4;
    }
}
