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
    private final ErpNextStoreSyncService storeSync;

    public ErpNextScheduler(ErpNextSyncService sync, ErpNextStoreSyncService storeSync) {
        this.sync = sync;
        this.storeSync = storeSync;
    }

    @Scheduled(fixedRateString = "${erpnext.sync.interval-ms:600000}", initialDelayString = "${erpnext.sync.initial-delay-ms:60000}")
    public void run() {
        try {
            var r = sync.syncAll(false);
            Object store = r.get("store");
            boolean storeActivity = store instanceof java.util.Map<?, ?> m
                    && (!"0".equals(String.valueOf(m.get("synced"))) || !"0".equals(String.valueOf(m.get("failed")))
                        || m.containsKey("error"));
            if (storeActivity || !"0".equals(String.valueOf(r.get("synced"))) || !"0".equals(String.valueOf(r.get("failed")))) {
                log.info("ERPNext sync: {}", r);
            }
        } catch (Exception e) {
            log.error("ERPNext sync error: {}", e.getMessage());
        }
    }

    /** Nightly: make the ERP's stock count match the website's (only once documents are submitted). */
    @Scheduled(cron = "${erpnext.stock-align.cron:0 30 3 * * ?}", zone = "Asia/Kolkata")
    public void alignStock() {
        try {
            var r = storeSync.alignStock();
            log.info("ERPNext stock alignment: {}", r);
        } catch (Exception e) {
            log.error("ERPNext stock alignment error: {}", e.getMessage());
        }
    }
}
