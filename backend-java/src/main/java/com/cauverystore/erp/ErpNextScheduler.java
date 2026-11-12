package com.cauverystore.erp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Pushes new records to ERPNext on an interval. Off unless erpnext.enabled=true. */
@Component
@ConditionalOnProperty(name = "erpnext.enabled", havingValue = "true")
public class ErpNextScheduler {
    private static final Logger log = LoggerFactory.getLogger(ErpNextScheduler.class);
    private final ErpNextSyncService sync;

    public ErpNextScheduler(ErpNextSyncService sync) { this.sync = sync; }

    @Scheduled(fixedRateString = "${erpnext.sync.interval-ms:600000}", initialDelayString = "${erpnext.sync.initial-delay-ms:60000}")
    public void run() {
        try {
            var r = sync.syncAll(false);
            if (!"0".equals(String.valueOf(r.get("synced"))) || !"0".equals(String.valueOf(r.get("failed")))) {
                log.info("ERPNext sync: {}", r);
            }
        } catch (Exception e) {
            log.error("ERPNext sync error: {}", e.getMessage());
        }
    }
}
