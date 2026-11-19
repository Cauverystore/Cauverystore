package com.cauverystore.controller;

import com.cauverystore.entities.GstRuleUpdate;
import com.cauverystore.service.GstRuleWatchService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GST rule changes found at CBIC (notifications that are not rate notifications, and circulars),
 * and a person's reading of each one.
 */
@RestController
@RequestMapping("/api/admin/gst/rule-updates")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminGstRuleUpdateController {

    private final GstRuleWatchService service;

    public AdminGstRuleUpdateController(GstRuleWatchService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        Map<String, Object> out = new LinkedHashMap<>(service.summary());
        out.put("items", service.list());
        return ResponseEntity.ok(out);
    }

    /** Runs the check now rather than waiting for the morning run. */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> check() {
        return ResponseEntity.ok(service.check());
    }

    /** Body: {"status": "REVIEWED" | "ACTION_NEEDED" | "NOT_RELEVANT" | "NEW", "note": "..."}. */
    @PostMapping("/{id}/review")
    public ResponseEntity<?> review(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            GstRuleUpdate u = service.review(id, body.get("status"), body.get("note"), currentUser());
            return ResponseEntity.ok(u);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** The PDF as CBIC serves it today, so a reviewer can read it without leaving the screen. */
    @GetMapping("/{id}/document")
    public ResponseEntity<?> document(@PathVariable Long id) {
        try {
            GstRuleUpdate u = service.get(id);
            byte[] pdf = service.fetchDocument(u);
            String name = u.getNumber().replaceAll("[^A-Za-z0-9._-]+", "-") + ".pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error",
                    "CBIC did not return the document: " + e.getMessage()));
        }
    }

    private static String currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
