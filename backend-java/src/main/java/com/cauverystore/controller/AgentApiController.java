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
 * An agent can read its queue and the category list, hand back drafts, and report what a supplier
 * shows for a product. It cannot create or edit products: the store's own rules decide what a
 * supplier reading changes (see AgentWatchService).
 */
@RestController
@RequestMapping("/api/agent")
public class AgentApiController {

    private final AgentJobService jobs;
    private final com.cauverystore.service.AgentWatchService watch;

    @Value("${agents.token:}")
    private String token;

    public AgentApiController(AgentJobService jobs, com.cauverystore.service.AgentWatchService watch) {
        this.jobs = jobs;
        this.watch = watch;
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

    @GetMapping("/watch/products")
    public ResponseEntity<?> watchTargets(@RequestHeader(value = "X-Agent-Token", required = false) String t) {
        ResponseEntity<?> no = refuse(t);
        if (no != null) return no;
        return ResponseEntity.ok(watch.targets());
    }

    @PostMapping("/watch/products/{productId}")
    public ResponseEntity<?> watchReport(@RequestHeader(value = "X-Agent-Token", required = false) String t,
                                         @PathVariable Long productId, @RequestBody Map<String, Object> body) {
        ResponseEntity<?> no = refuse(t);
        if (no != null) return no;
        try {
            return ResponseEntity.ok(watch.report(productId, body));
        } catch (AgentJobService.BadRequest e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
