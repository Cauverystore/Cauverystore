package com.cauverystore.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "gst.sync.enabled", havingValue = "true", matchIfMissing = false)
public class GstSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(GstSyncScheduler.class);
    private final GstInvoiceService invoiceService;

    public GstSyncScheduler(GstInvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @Scheduled(fixedRateString = "${gst.sync.interval-ms:300000}")
    public void processSyncQueue() {
        try {
            int processed = invoiceService.processSyncQueue(10);
            if (processed > 0) {
                log.info("GST sync queue: processed {} items", processed);
            }
        } catch (Exception e) {
            log.error("GST sync queue processing error: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void nightlySyncRetry() {
        log.info("Running nightly GST sync retry for failed items");
        try {
            int processed = invoiceService.processSyncQueue(50);
            log.info("Nightly sync retry processed {} items", processed);
        } catch (Exception e) {
            log.error("Nightly sync retry error: {}", e.getMessage());
        }
    }
}
