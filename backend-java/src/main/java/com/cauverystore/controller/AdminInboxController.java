package com.cauverystore.controller;

import com.cauverystore.entities.InboundEmail;
import com.cauverystore.repository.InboundEmailRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Admin -> Inbox: emails received at the store's own addresses. */
@RestController
@RequestMapping("/api/admin/inbox")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminInboxController {

    private final InboundEmailRepository repo;

    public AdminInboxController(InboundEmailRepository repo) {
        this.repo = repo;
    }

    /** Newest first, without bodies (they can be large). */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        Page<InboundEmail> p = repo.findAllByOrderByReceivedAtDesc(PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        List<Map<String, Object>> items = p.getContent().stream().map(AdminInboxController::summary).toList();
        Map<String, Object> out = new HashMap<>();
        out.put("items", items);
        out.put("page", p.getNumber());
        out.put("totalPages", p.getTotalPages());
        out.put("total", p.getTotalElements());
        out.put("unread", repo.countByReadFalse());
        return ResponseEntity.ok(out);
    }

    /** One email in full. Opening it marks it read. */
    @GetMapping("/{id}")
    @Transactional
    public ResponseEntity<InboundEmail> get(@PathVariable Long id) {
        return repo.findById(id).map(e -> {
            if (!e.isRead()) {
                e.setRead(true);
                repo.save(e);
            }
            return ResponseEntity.ok(e);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> setRead(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return repo.findById(id).map(e -> {
            e.setRead(Boolean.TRUE.equals(body.get("read")));
            repo.save(e);
            return ResponseEntity.ok(summary(e));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> summary(InboundEmail e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId());
        m.put("fromHeader", e.getFromHeader());
        m.put("fromAddress", e.getFromAddress());
        m.put("toAddresses", e.getToAddresses());
        m.put("subject", e.getSubject());
        m.put("receivedAt", e.getReceivedAt());
        m.put("read", e.isRead());
        m.put("forwardedTo", e.getForwardedTo());
        m.put("hasAttachments", e.getAttachmentNames() != null && !e.getAttachmentNames().isBlank());
        String text = e.getTextBody();
        m.put("preview", text == null ? "" : text.replaceAll("\\s+", " ").trim().substring(0, Math.min(140, text.replaceAll("\\s+", " ").trim().length())));
        return m;
    }
}
