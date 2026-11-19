package com.cauverystore.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboundEmailServiceTest {

    // Example from Svix's own documentation for manual verification.
    private static final String SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
    private static final String ID = "msg_p5jXN8AQM9LWM0D4loKWxJek";
    private static final String TS = "1614265330";
    private static final String BODY = "{\"test\": 2432232314}";
    private static final String SIG = "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=";

    @Test
    void acceptsSvixDocumentationExample() {
        assertTrue(InboundEmailService.signatureValid(SECRET, ID, TS, SIG, BODY, 1614265330L));
    }

    @Test
    void acceptsWhenOneOfSeveralSignaturesMatches() {
        assertTrue(InboundEmailService.signatureValid(SECRET, ID, TS, "v1,AAAA " + SIG, BODY, 1614265330L));
    }

    @Test
    void rejectsChangedBody() {
        assertFalse(InboundEmailService.signatureValid(SECRET, ID, TS, SIG, "{\"test\": 1}", 1614265330L));
    }

    @Test
    void rejectsOldTimestamp() {
        assertFalse(InboundEmailService.signatureValid(SECRET, ID, TS, SIG, BODY, 1614265330L + 3600));
    }

    @Test
    void rejectsWhenNoSecret() {
        assertFalse(InboundEmailService.signatureValid("", ID, TS, SIG, BODY, 1614265330L));
    }

    @Test
    void ownComputationMatches() throws Exception {
        byte[] key = Base64.getDecoder().decode(SECRET.substring(6));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        String sig = Base64.getEncoder().encodeToString(mac.doFinal((ID + "." + TS + "." + BODY).getBytes(StandardCharsets.UTF_8)));
        assertEquals(SIG.substring(3), sig);
    }

    @Test
    void bareAddressStripsDisplayName() {
        assertEquals("asha@example.com", InboundEmailService.bareAddress("Asha K <asha@example.com>"));
        assertEquals("asha@example.com", InboundEmailService.bareAddress(" asha@example.com "));
    }
}
