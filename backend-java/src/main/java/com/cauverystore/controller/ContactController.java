package com.cauverystore.controller;

import com.cauverystore.entities.ContactMessage;
import com.cauverystore.repository.ContactMessageRepository;
import com.cauverystore.service.EmailService;
import com.cauverystore.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The Contact Us form. Anyone can send one (no sign-in), so it is size-checked and rate-limited
 * per address. Each message is saved, then emailed to the support inbox with Reply-To set to
 * the sender, so support can answer straight from their mail app.
 */
@RestController
public class ContactController {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ContactMessageRepository repo;
    private final EmailService emailService;
    private final RateLimiterService rateLimiter;

    public ContactController(ContactMessageRepository repo, EmailService emailService, RateLimiterService rateLimiter) {
        this.repo = repo;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/api/contact")
    public ResponseEntity<?> submit(@RequestBody Map<String, String> body, HttpServletRequest http) {
        String name = trim(body.get("name"));
        String email = trim(body.get("email"));
        String subject = trim(body.get("subject"));
        String message = trim(body.get("message"));

        if (name.isEmpty() || email.isEmpty() || message.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please fill in your name, email and message."));
        }
        if (email.length() > 200 || !EMAIL.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please enter a valid email address."));
        }
        if (name.length() > 100 || subject.length() > 200 || message.length() > 5000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Your message is too long. Please shorten it."));
        }
        // Five messages per address per hour is plenty for a person and stops a script.
        if (!rateLimiter.allow("contact:ip:" + http.getRemoteAddr(), 5, 3_600_000L)) {
            return ResponseEntity.status(429).body(Map.of("error", "Too many messages. Please try again later."));
        }

        ContactMessage m = new ContactMessage();
        m.setName(name);
        m.setEmail(email.toLowerCase());
        m.setSubject(subject.isEmpty() ? null : subject);
        m.setMessage(message);
        repo.save(m);

        emailService.sendContactMessage(m);
        return ResponseEntity.ok(Map.of("message", "Thanks! We have received your message and will reply by email."));
    }

    /** For staff: every Contact Us message, newest first (protected by the /api/admin/** rule). */
    @GetMapping("/api/admin/contact-messages")
    public List<ContactMessage> list() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
