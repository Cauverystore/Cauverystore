package com.cauverystore.controller;

import com.cauverystore.service.PostmasterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.Map;

/** Postmaster's locked door, with its own secret, plus the Admin route that approves a draft. */
@RestController
public class PostmasterController {

    private final PostmasterService postmaster;

    @Value("${postmaster.token:}")
    private String token;

    public PostmasterController(PostmasterService postmaster) {
        this.postmaster = postmaster;
    }

    private ResponseEntity<?> refuse(String header) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(503).body(Map.of("error", "Postmaster is not switched on (POSTMASTER_TOKEN is not set)."));
        }
        if (header == null || !MessageDigest.isEqual(header.trim().getBytes(StandardCharsets.UTF_8),
                token.trim().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).body(Map.of("error", "Wrong Postmaster token."));
        }
        return null;
    }

    @GetMapping("/api/agent/postmaster/mail")
    public ResponseEntity<?> mail(@RequestHeader(value = "X-Postmaster-Token", required = false) String t,
                                  @RequestParam(defaultValue = "10") int limit) {
        ResponseEntity<?> no = refuse(t);
        if (no != null) return no;
        return ResponseEntity.ok(postmaster.claim(limit));
    }

    @PostMapping("/api/agent/postmaster/mail/{id}/decision")
    public ResponseEntity<?> decision(@RequestHeader(value = "X-Postmaster-Token", required = false) String t,
                                      @PathVariable Long id, @RequestBody Map<String, Object> body) {
        ResponseEntity<?> no = refuse(t);
        if (no != null) return no;
        try {
            return ResponseEntity.ok(postmaster.decide(id, body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/admin/postmaster/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(postmaster.stats());
    }

    @PostMapping("/api/admin/postmaster/{id}/send")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> send(@PathVariable Long id, @RequestBody Map<String, String> body, Principal who) {
        try {
            return ResponseEntity.ok(postmaster.adminSend(id, body.get("text"), who == null ? "admin" : who.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }
}
