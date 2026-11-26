package com.cauverystore.billinglink;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Status and "send now" for the Cauvery Billing link. Admin only. */
@RestController
@RequestMapping("/api/admin/billing-link")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminBillingLinkController {

    private final BillingLinkService link;

    @Value("${billing-link.enabled:false}") private boolean enabled;
    @Value("${billing-link.url:}") private String url;

    public AdminBillingLinkController(BillingLinkService link) {
        this.link = link;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("configured", link.isConfigured());
        m.put("billingUrl", url);
        m.put("sent", link.count(BillingLinkRecord.SENT));
        m.put("failed", link.count(BillingLinkRecord.FAILED));
        List<Map<String, Object>> failures = new ArrayList<>();
        for (BillingLinkRecord r : link.recentFailures()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("invoiceNumber", r.getInvoiceNumber());
            f.put("attempts", r.getAttempts());
            f.put("error", r.getErrorMessage());
            f.put("updatedAt", r.getUpdatedAt());
            failures.add(f);
        }
        m.put("failures", failures);
        return m;
    }

    /** Sends now instead of waiting for the next run. force=true also retries invoices given up on. */
    @PostMapping("/send")
    public Map<String, Object> send(@RequestParam(defaultValue = "false") boolean force) {
        return link.sendAll(force);
    }
}
