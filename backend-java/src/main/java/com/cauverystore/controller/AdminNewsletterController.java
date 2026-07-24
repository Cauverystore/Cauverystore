package com.cauverystore.controller;

import com.cauverystore.entities.NewsletterSubscription;
import com.cauverystore.service.NewsletterService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/newsletter")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@CrossOrigin("*")
public class AdminNewsletterController {
    private final NewsletterService svc;
    public AdminNewsletterController(NewsletterService svc) { this.svc = svc; }

    @GetMapping
    public ResponseEntity<List<NewsletterSubscription>> getAll() { return ResponseEntity.ok(svc.getAll()); }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getCount() { return ResponseEntity.ok(Map.of("count", svc.getCount())); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { svc.unsubscribe(id); return ResponseEntity.noContent().build(); }
}
