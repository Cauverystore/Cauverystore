package com.cauverystore.erp;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Status and manual trigger for the ERPNext mirror. Admin only. */
@RestController
@RequestMapping("/api/admin/erp")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminErpNextController {

    private final ErpNextClient client;
    private final ErpNextSyncService sync;
    private final ErpSyncRecordRepository records;

    public AdminErpNextController(ErpNextClient client, ErpNextSyncService sync, ErpSyncRecordRepository records) {
        this.client = client;
        this.sync = sync;
        this.records = records;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", client.isConfigured());
        m.put("synced", records.countByStatus(ErpSyncRecord.SYNCED));
        m.put("failed", records.countByStatus(ErpSyncRecord.FAILED));
        m.put("recentFailures", records.findTop50ByStatusOrderByUpdatedAtDesc(ErpSyncRecord.FAILED).stream()
                .map(r -> Map.of("type", r.getEntityType(), "id", r.getEntityId(),
                        "attempts", r.getAttempts(), "error", String.valueOf(r.getErrorMessage())))
                .toList());
        return m;
    }

    /** ?force=true retries items that exhausted their automatic attempts. */
    @PostMapping("/sync")
    public Map<String, Object> syncNow(@RequestParam(defaultValue = "false") boolean force) {
        return sync.syncAll(force);
    }
}
