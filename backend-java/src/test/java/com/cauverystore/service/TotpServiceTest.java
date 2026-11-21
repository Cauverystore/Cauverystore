package com.cauverystore.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {

    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private final TotpService totp = new TotpService();

    @Test
    void newKeysAreInTheFormatAuthenticatorAppsAccept() {
        for (int i = 0; i < 200; i++) {
            String secret = totp.generateSecret();
            assertTrue(secret.matches("[A-Z2-7]{32}"), secret);
            assertTrue(totp.generateQrUri("Cauvery Store", "a@example.com", secret).contains("secret=" + secret + "&"));
        }
    }

    @Test
    void encodesTheStandardTestKey() {
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", TotpService.base32(RFC_KEY));
    }

    @Test
    void matchesTheOfficialRfc6238Codes() {
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertEquals("287082", totp.generateCode(secret, 59L / 30));
        assertEquals("081804", totp.generateCode(secret, 1111111109L / 30));
        assertEquals("005924", totp.generateCode(secret, 1234567890L / 30));
    }

    @Test
    void olderStoredKeysStillWork() {
        String old = Base64.getEncoder().withoutPadding().encodeToString(RFC_KEY);
        assertEquals(27, old.length());
        assertEquals("287082", totp.generateCode(old, 59L / 30));
    }

    @Test
    void acceptsTheCurrentCodeAndRefusesAWrongOne() {
        String secret = totp.generateSecret();
        String now = totp.generateCode(secret, System.currentTimeMillis() / 1000L / 30);
        assertTrue(totp.verify(now, secret));
        assertFalse(totp.verify("abcdef", secret));
    }
}
