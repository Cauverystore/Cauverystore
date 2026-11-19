package com.cauverystore.service;

import com.cauverystore.entities.GstConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raises last month's commission invoices once a month, so nobody has to remember to.
 *
 * Runs on the 16th by default rather than the 1st: an order placed on the last day of the month
 * needs a few days to be delivered and then 7 more for the return window, and a return found
 * before billing is simply left off the invoice instead of needing a credit note afterwards.
 *
 * Billing is safe to repeat - a seller already invoiced for the month is returned as it stands -
 * so a restart or a manual run through the admin API on the same month does no harm.
 *
 * Does nothing while the marketplace has no GSTIN: a commission invoice charges GST, and an
 * unregistered business cannot issue one.
 */
@Component
@ConditionalOnProperty(name = "commission.auto-invoice.enabled", havingValue = "true", matchIfMissing = true)
public class CommissionInvoiceScheduler {

    private static final Logger log = LoggerFactory.getLogger(CommissionInvoiceScheduler.class);
    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("MMyyyy");

    private final CommissionInvoiceService service;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CommissionInvoiceScheduler(CommissionInvoiceService service) {
        this(service, Clock.system(INDIA));
    }

    CommissionInvoiceScheduler(CommissionInvoiceService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "${commission.auto-invoice.cron:0 0 4 16 * ?}", zone = "Asia/Kolkata")
    public void billLastMonth() {
        try {
            billMonth(YearMonth.now(clock).minusMonths(1));
        } catch (Exception e) {
            log.error("Monthly commission invoicing failed: {}", e.getMessage(), e);
        }
    }

    /** Bills every seller for one month. Returns what was issued and what was skipped. */
    Map<String, Object> billMonth(YearMonth month) {
        String period = month.format(PERIOD);

        // The same configuration generateForSeller invoices from.
        String gstin = service.marketplaceConfig()
                .map(GstConfiguration::getGstin).orElse(null);
        if (gstin == null || gstin.isBlank()) {
            log.warn("Commission invoices for {} not raised: the store has no GSTIN yet. "
                    + "Enter it in Admin > GST Compliance; the next monthly run will bill.", period);
            Map<String, Object> skipped = new LinkedHashMap<>();
            skipped.put("period", period);
            skipped.put("skipped", "Store has no GSTIN");
            return skipped;
        }

        Map<String, Object> result = service.generateForPeriod(period, "system");
        List<?> issued = (List<?>) result.getOrDefault("issued", List.of());
        List<?> skipped = (List<?>) result.getOrDefault("skipped", List.of());
        log.info("Commission invoices for {}: {} issued or already issued, {} sellers skipped",
                period, issued.size(), skipped.size());
        for (Object reason : skipped) {
            // "no billable orders" is the normal case for a quiet seller; anything else needs a look.
            if (String.valueOf(reason).contains("nothing to invoice")) log.debug("{}", reason);
            else log.warn("Commission not billed - {}", reason);
        }
        return result;
    }
}
