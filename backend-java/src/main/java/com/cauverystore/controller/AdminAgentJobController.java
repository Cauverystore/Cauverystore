package com.cauverystore.controller;

import com.cauverystore.service.AgentJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Admin screens for the agents: queue supplier links, review drafts, reject or mark as used. */
@RestController
@RequestMapping("/api/admin/agent-jobs")
@PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'SUPER_ADMIN')")
public class AdminAgentJobController {

    private final AgentJobService jobs;
    private final com.cauverystore.service.SupplierPhotoService photos;

    public AdminAgentJobController(AgentJobService jobs, com.cauverystore.service.SupplierPhotoService photos) {
        this.jobs = jobs;
        this.photos = photos;
    }

    /** How many supplier photos the agent saved for this product (0 if it did not come from a draft). */
    @GetMapping("/photos")
    public Map<String, Object> photoCount(@RequestParam Long productId) {
        return Map.of("available", photos.photosFor(productId).size());
    }

    /** Copies those supplier photos onto the product, on the server. */
    @PostMapping("/photos/import")
    public ResponseEntity<?> importPhotos(@RequestBody Map<String, Object> body) {
        Object p = body.get("productId");
        if (!(p instanceof Number n)) return ResponseEntity.badRequest().body(Map.of("error", "productId is required."));
        return ResponseEntity.ok(photos.importFor(n.longValue()));
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "ready") String view) {
        return jobs.list(view);
    }

    @GetMapping("/counts")
    public Map<String, Object> counts() {
        return jobs.counts();
    }

    @PostMapping("/queue")
    public ResponseEntity<?> queue(@RequestBody Map<String, Object> body) {
        List<String> links = new ArrayList<>();
        Object raw = body.get("links");
        if (raw instanceof String s) links.addAll(List.of(s.split("[\\r\\n\\s,]+")));
        else if (raw instanceof List<?> l) l.forEach(x -> links.add(String.valueOf(x)));
        Double cost = null;
        Object c = body.get("supplierCost");
        if (c instanceof Number n) cost = n.doubleValue();
        else if (c instanceof String s && !s.isBlank()) {
            try { cost = Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {}
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            return ResponseEntity.ok(jobs.queue(links, cost, auth != null ? auth.getName() : null));
        } catch (AgentJobService.BadRequest e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id) {
        return wrap(() -> jobs.reject(id));
    }

    @PutMapping("/{id}/requeue")
    public ResponseEntity<?> requeue(@PathVariable Long id) {
        return wrap(() -> jobs.requeue(id));
    }

    @PutMapping("/{id}/used")
    public ResponseEntity<?> used(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object p = body.get("productId");
        Long productId = p instanceof Number n ? n.longValue() : null;
        return wrap(() -> jobs.markUsed(id, productId));
    }

    private ResponseEntity<?> wrap(java.util.function.Supplier<Object> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (AgentJobService.BadRequest e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
