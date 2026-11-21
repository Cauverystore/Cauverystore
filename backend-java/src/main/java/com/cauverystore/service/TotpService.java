package com.cauverystore.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * RFC-6238 TOTP implementation (HMAC-SHA1, 30-second steps, 6 digits). No
 * external dependency - enough to support authenticator apps such as Google
 * Authenticator or Authy.
 */
@Service
public class TotpService {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int WINDOW = 1;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    /** Authenticator apps only accept keys in Base32 (capital letters and the digits 2-7). */
    static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0, bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
            buffer &= (1 << bits) - 1;
        }
        if (bits > 0) out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    private static byte[] fromBase32(String s) {
        byte[] out = new byte[s.length() * 5 / 8];
        int buffer = 0, bits = 0, n = 0;
        for (char c : s.toCharArray()) {
            buffer = (buffer << 5) | BASE32.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out[n++] = (byte) (buffer >> (bits - 8));
                bits -= 8;
                buffer &= (1 << bits) - 1;
            }
        }
        return out;
    }

    /** New keys are 32 Base32 characters. Older stored keys are 27 Base64 characters and keep working. */
    private static byte[] keyBytes(String secret) {
        return secret.length() == 32 && secret.chars().allMatch(c -> BASE32.indexOf(c) >= 0)
                ? fromBase32(secret)
                : Base64.getDecoder().decode(secret);
    }

    public String generateQrUri(String issuer, String accountName, String secret) {
        String label = urlEncode(issuer + ":" + accountName);
        String params = "secret=" + secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS
                + "&period=" + TIME_STEP_SECONDS;
        return "otpauth://totp/" + label + "?" + params;
    }

    public boolean verify(String code, String secret) {
        if (code == null || secret == null || code.isBlank()) {
            return false;
        }
        long currentCounter = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            if (constantTimeEquals(code, generateCode(secret, currentCounter + offset))) {
                return true;
            }
        }
        return false;
    }

    String generateCode(String secret, long counter) {
        try {
            byte[] key = keyBytes(secret);
            byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            return "";
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ba.length != bb.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < ba.length; i++) {
            result |= ba[i] ^ bb[i];
        }
        return result == 0;
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
