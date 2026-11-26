package com.cauverystore.billinglink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Sends new paid store orders to Cauvery Billing on an interval. Off unless billing-link.enabled=true. */
@Component
@ConditionalOnProperty(name = "billing-link.enabled", havingValue = "true")
public class BillingLinkScheduler {
    private static final Logger log = LoggerFactory.getLogger(BillingLinkScheduler.class);
    private final BillingLinkService link;

    public BillingLinkScheduler(BillingLinkService link) {
        this.link = link;
    }

    @Scheduled(fixedRateString = "${billing-link.interval-ms:600000}", initialDelayString = "${billing-link.initial-delay-ms:90000}")
    public void run() {
        try {
            Map<String, Object> r = link.sendAll(false);
            if (!"0".equals(String.valueOf(r.get("sent"))) || !"0".equals(String.valueOf(r.get("failed")))
                    || r.containsKey("skipped")) {
                log.info("Cauvery Billing link: {}", r);
            }
        } catch (Exception e) {
            log.error("Cauvery Billing link error: {}", e.getMessage());
        }
    }

}
