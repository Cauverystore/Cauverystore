package com.cauverystore.controller;

import com.cauverystore.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Receives Razorpay's payment webhook.
 *
 * The body is read as raw text because Razorpay signs the exact bytes it sent; re-serialising
 * parsed JSON would change them and the signature would never match.
 */
@RestController
@RequestMapping("/api/payment/webhook")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public PaymentWebhookController(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> receive(@RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                                       @RequestBody(required = false) String rawBody) {
        if (!paymentService.webhookSignatureMatches(rawBody, signature)) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid signature"));
        }
        try {
            JsonNode event = objectMapper.readTree(rawBody);
            String result = paymentService.recordCapturedPayment(event);
            log.info("Razorpay webhook: {}", result);
            return ResponseEntity.ok(Map.of("result", result));
        } catch (Exception e) {
            // A 5xx makes Razorpay retry, which is what we want if the database was briefly away.
            log.error("Razorpay webhook not recorded: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "not recorded"));
        }
    }
}
