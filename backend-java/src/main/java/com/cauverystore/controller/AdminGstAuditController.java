package com.cauverystore.controller;

import com.cauverystore.entities.GstAuditEvent;
import com.cauverystore.service.GstAuditService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of the append-only GST audit trail. There is deliberately no endpoint that
 * changes or removes an event.
 */
@RestController
@RequestMapping("/api/admin/gst/audit-events")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminGstAuditController {

    private final GstAuditService audit;

    public AdminGstAuditController(GstAuditService audit) {
        this.audit = audit;
    }

    /** Newest first. */
    @GetMapping
    public ResponseEntity<Page<GstAuditEvent>> recent(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(audit.recent(page, size));
    }

    /** Everything that happened to one document, e.g. entityType=GstInvoice&ref=CS/2026/0001. */
    @GetMapping("/history")
    public ResponseEntity<List<GstAuditEvent>> history(@RequestParam String entityType,
                                                       @RequestParam String ref) {
        return ResponseEntity.ok(audit.history(entityType, ref));
    }

    /** Re-hashes the whole trail and says whether any event was changed, removed or inserted. */
    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify() {
        GstAuditService.ChainCheck c = audit.verifyAll();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intact", c.intact());
        body.put("eventsChecked", c.checked());
        body.put("firstBadEventId", c.firstBadId());
        body.put("problem", c.problem());
        return ResponseEntity.ok(body);
    }
}
