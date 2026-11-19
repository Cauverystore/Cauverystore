package com.cauverystore.controller;

import com.cauverystore.service.InboundEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Resend's "email.received" webhook.
 *
 * The body is read as raw text because Svix signs the exact bytes sent; parsed-and-reserialised
 * JSON would never match.
 */
@RestController
@RequestMapping("/api/email/inbound")
public class InboundEmailWebhookController {

    private static final Logger log = LoggerFactory.getLogger(InboundEmailWebhookController.class);

    private final InboundEmailService inboundEmailService;

    public InboundEmailWebhookController(InboundEmailService inboundEmailService) {
        this.inboundEmailService = inboundEmailService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> receive(@RequestHeader(value = "svix-id", required = false) String id,
                                                       @RequestHeader(value = "svix-timestamp", required = false) String timestamp,
                                                       @RequestHeader(value = "svix-signature", required = false) String signature,
                                                       @RequestBody(required = false) String rawBody) {
        if (!inboundEmailService.isSecretConfigured()) {
            log.error("Inbound email refused: RESEND_WEBHOOK_SECRET is not set");
            return ResponseEntity.status(503).body(Map.of("error", "not configured"));
        }
        if (!inboundEmailService.signatureValid(id, timestamp, signature, rawBody)) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid signature"));
        }
        try {
            String result = inboundEmailService.handle(rawBody);
            log.info("Inbound email webhook: {}", result);
            return ResponseEntity.ok(Map.of("result", result));
        } catch (Exception e) {
            // 5xx makes Resend retry later, which is right if its API or our database blipped.
            log.error("Inbound email not recorded: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "not recorded"));
        }
    }
}
