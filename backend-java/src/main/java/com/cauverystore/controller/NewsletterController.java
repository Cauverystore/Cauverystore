package com.cauverystore.controller;

import com.cauverystore.entities.NewsletterSubscription;
import com.cauverystore.repository.NewsletterSubscriptionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/newsletter")
public class NewsletterController {

    private final NewsletterSubscriptionRepository repo;

    public NewsletterController(NewsletterSubscriptionRepository repo) {
        this.repo = repo;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }
        if (repo.existsByEmail(email)) {
            return ResponseEntity.ok(Map.of("message", "Already subscribed"));
        }
        NewsletterSubscription sub = new NewsletterSubscription();
        sub.setEmail(email.trim().toLowerCase());
        repo.save(sub);
        return ResponseEntity.ok(Map.of("message", "Subscribed successfully"));
    }
}
