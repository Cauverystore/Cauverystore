package com.cauverystore.controller;

import com.cauverystore.entities.CustomerFeedback;
import com.cauverystore.repository.CustomerFeedbackRepository;
import com.cauverystore.service.EmailService;
import com.cauverystore.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Quick feedback from the "Feedback" tab on every shop page: a type (feedback, complaint or
 * suggestion), a message, an optional photo, and the sender's name and email (both required).
 * No sign-in needed, so it is size-checked and rate-limited per address like the Contact Us form.
 * Staff read it in Admin -> Feedback (the /api/admin/** rule protects those endpoints).
 */
@RestController
public class FeedbackController {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> TYPES = Set.of("FEEDBACK", "COMPLAINT", "SUGGESTION");
    private static final Set<String> STATUSES = Set.of("NEW", "DONE");
    private static final int MAX_PHOTO_BYTES = 1_000_000;

    private final CustomerFeedbackRepository repo;
    private final EmailService emailService;
    private final RateLimiterService rateLimiter;

    public FeedbackController(CustomerFeedbackRepository repo, EmailService emailService, RateLimiterService rateLimiter) {
        this.repo = repo;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping(value = "/api/feedback", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submit(@RequestParam(value = "type", required = false) String type,
                                    @RequestParam(value = "message", required = false) String message,
                                    @RequestParam(value = "name", required = false) String name,
                                    @RequestParam(value = "email", required = false) String email,
                                    @RequestParam(value = "page", required = false) String page,
                                    @RequestParam(value = "photo", required = false) MultipartFile photo,
                                    HttpServletRequest http) throws IOException {
        type = trim(type).toUpperCase();
        message = trim(message);
        name = trim(name);
        email = trim(email).toLowerCase();
        page = trim(page);

        if (!TYPES.contains(type)) type = "FEEDBACK";
        if (name.isEmpty()) {
            return bad("Please enter your name.");
        }
        if (email.isEmpty()) {
            return bad("Please enter your email address.");
        }
        if (message.isEmpty()) {
            return bad("Please write your message.");
        }
        if (name.length() > 100) {
            return bad("Your name is too long. Please shorten it.");
        }
        if (message.length() > 2000) {
            return bad("Your message is too long. Please shorten it.");
        }
        if (email.length() > 200 || !EMAIL.matcher(email).matches()) {
            return bad("Please enter a valid email address.");
        }
        // Only a path on this site (e.g. /product/12); staff open it as a link, so nothing else.
        if (!page.matches("/[A-Za-z0-9/_\\-.%]*") || page.startsWith("//")) page = "";
        if (page.length() > 500) page = page.substring(0, 500);

        byte[] bytes = null;
        String photoType = null;
        if (photo != null && !photo.isEmpty()) {
            if (photo.getSize() > MAX_PHOTO_BYTES) {
                return bad("The photo is too large. Please choose a smaller one.");
            }
            bytes = photo.getBytes();
            photoType = imageType(bytes);
            if (photoType == null) {
                return bad("Please attach a JPG, PNG or WebP photo.");
            }
        }

        // Five per address per hour is plenty for a person and stops a script.
        if (!rateLimiter.allow("feedback:ip:" + com.cauverystore.util.ClientIp.of(http), 5, 3_600_000L)) {
            return ResponseEntity.status(429).body(Map.of("error", "Too many messages. Please try again later."));
        }

        CustomerFeedback f = new CustomerFeedback();
        f.setType(type);
        f.setMessage(message);
        f.setName(name);
        f.setEmail(email);
        f.setPageUrl(page.isEmpty() ? null : page);
        f.setUserId(signedInUserId());
        f.setPhoto(bytes);
        f.setPhotoType(photoType);
        repo.save(f);

        emailService.sendFeedbackNotice(f);
        return ResponseEntity.ok(Map.of("message", "Thank you! We have received your message."));
    }

    /** For staff: feedback, newest first, optionally only NEW/DONE and one type. */
    @GetMapping("/api/admin/feedback")
    public Map<String, Object> list(@RequestParam(value = "status", required = false) String status,
                                    @RequestParam(value = "type", required = false) String type,
                                    @RequestParam(value = "page", defaultValue = "0") int page) {
        String s = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.toUpperCase();
        String t = type == null || type.isBlank() || "ALL".equalsIgnoreCase(type) ? null : type.toUpperCase();
        var result = repo.findRows(s, t, PageRequest.of(Math.max(page, 0), 30));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", result.getContent());
        out.put("page", result.getNumber());
        out.put("totalPages", result.getTotalPages());
        out.put("total", result.getTotalElements());
        out.put("newCount", repo.countByStatus("NEW"));
        return out;
    }

    @GetMapping("/api/admin/feedback/{id}/photo")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        return repo.findById(id)
                .filter(f -> f.getPhoto() != null && f.getPhotoType() != null)
                .map(f -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(f.getPhotoType()))
                        .cacheControl(CacheControl.noStore())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(f.getPhoto()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/api/admin/feedback/{id}/status")
    public ResponseEntity<?> setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = trim(body.get("status")).toUpperCase();
        if (!STATUSES.contains(status)) return bad("Unknown status.");
        return repo.findById(id).map(f -> {
            f.setStatus(status);
            repo.save(f);
            return ResponseEntity.ok(Map.of("status", status));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/admin/feedback/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    /** Works out the picture type from its first bytes; the browser's claimed type is not trusted. */
    static String imageType(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }

    private static Long signedInUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getDetails() instanceof Long id ? id : null;
    }

    private static ResponseEntity<Map<String, String>> bad(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
