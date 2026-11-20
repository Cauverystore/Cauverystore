package com.cauverystore.controller;

import com.cauverystore.service.AgentJobService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * The door the separate agents service uses. It is not tied to a user login: every call must
 * carry the shared secret AGENTS_TOKEN in the X-Agent-Token header. With no token set on the
 * server the door stays shut.
 *
 * An agent can only read its queue and the category list, and hand back drafts. It cannot
 * create, change or publish products.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentApiController {

    private final AgentJobService jobs;

    @Value("${agents.token:}")
    private String token;

    public AgentApiController(AgentJobService jobs) {
        this.jobs = jobs;
    }

    private ResponseEntity<?> refuse(String header) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(503).body(Map.of("error", "Agents are not switched on (AGENTS_TOKEN is not set)."));
        }
        if (header == null || !MessageDigest.isEqual(header.trim().getBytes(StandardCharsets.UTF_8),
                token.trim().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).body(Map.of("error", "Wrong agent token."));
        }
        return null;
    }

    @GetMapping("/{agentName}/jobs")
    public ResponseEntity<?> claim(@RequestHeader(value = "X-Agent-Token", required = false) String t,
                                   @PathVariable String agentName,
                                   @RequestParam(defaultValue = "10") int limit) {
        ResponseEntity<?> no = refuse(t);
        if (no != null) return no;
        return ResponseEntity.ok(Map.of("jobs", jobs.claim(agentName, limit)));
    }

    @GetMapping("/{agentName}/pending")
    public ResponseEntity<?> pending(@RequestHeader(value = "X-Agent-Token", required = false) String t,
                                     @PathVariable String agentName) {
        ResponseEntity<?> no = refuse(t);
        if (no != null) return no;
        return ResponseEntity.ok(Map.of("pending", jobs.pendingCount(agentName)));
    }

    @GetMapping("/categories")
    public ResponseEntity<?> categories(@RequestHeader(value = "X-Agent-Token", required = false) String t) {
        ResponseEntity<?> no = refuse(t);
        if (no != null) return no;
        return ResponseEntity.ok(jobs.categoryList());
    }

    @PostMapping("/jobs/{id}/result")
    public ResponseEntity<?> result(@RequestHeader(value = "X-Agent-Token", required = false) String t,
                                    @PathVariable Long id, @RequestBody Map<String, Object> body) {
        ResponseEntity<?> no = refuse(t);
        if (no != null) return no;
        try {
            return ResponseEntity.ok(jobs.acceptResult(id, body));
        } catch (AgentJobService.BadRequest e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
