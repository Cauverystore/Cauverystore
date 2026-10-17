package com.cauverystore.controller;

import com.cauverystore.service.ShiprocketService;
import com.fasterxml.jackson.databind.JsonNode;
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
 * Receives Shiprocket's tracking webhook.
 *
 * Shiprocket refuses webhook addresses that contain its own name (or "sr"/"kr"), hence the
 * neutral path. It sends the token typed into its webhook settings in the x-api-key header;
 * without the matching token nothing is changed.
 */
@RestController
@RequestMapping("/api/courier-updates")
public class CourierUpdateController {

    private static final Logger log = LoggerFactory.getLogger(CourierUpdateController.class);

    private final ShiprocketService shiprocketService;

    public CourierUpdateController(ShiprocketService shiprocketService) {
        this.shiprocketService = shiprocketService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> receive(@RequestHeader(value = "x-api-key", required = false) String token,
                                                       @RequestBody(required = false) JsonNode body) {
        if (!shiprocketService.webhookTokenMatches(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid token"));
        }
        if (body == null) {
            return ResponseEntity.ok(Map.of("result", "ignored: empty body"));
        }
        try {
            String result = shiprocketService.applyTrackingUpdate(body);
            log.info("Courier update: {}", result);
            return ResponseEntity.ok(Map.of("result", result));
        } catch (RuntimeException e) {
            // Answer 200 anyway: Shiprocket retries failures, and a retry of an update we cannot
            // apply would fail the same way. The log keeps the reason.
            log.warn("Courier update for AWB {} not applied: {}", body.path("awb").asText(""), e.getMessage());
            return ResponseEntity.ok(Map.of("result", "not applied"));
        }
    }
}
