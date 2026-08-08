package com.cauverystore.service;

import com.cauverystore.entities.GstRateSource;
import com.cauverystore.repository.GstRateSourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Watches CBIC for rate notifications this store has not applied yet.
 *
 * <h2>Why this exists now when it could not before</h2>
 *
 * CBIC's tax information portal is a single-page app and everything under /api/notifications
 * answers 401, which is why the rate work up to this point relied on a person checking. That
 * turned out to be the wrong conclusion drawn from the wrong endpoint. The portal's own
 * front-end reads its "latest updates" feed from
 * {@code /api/cbic-notification-msts/fetchUpdatesByTaxId/1000001}, which needs no credentials
 * and returns plain JSON. This reads exactly what a visitor's browser reads.
 *
 * <h2>What it does with what it finds</h2>
 *
 * It records that the notification exists, downloads the PDF, and stores its SHA-256. It does
 * not read the PDF for rates and never touches gst_rate_master - a rate enters this system only
 * when a person has read the notification and imported it, and that is the property that makes
 * every rate here defensible. What this removes is the chance of nobody noticing for a month,
 * and the fingerprint means the document a reviewer reads can be shown to be the one CBIC
 * served on the day it was found.
 *
 * A found notification is written with applied=false, so it shows on the compliance screen as
 * an outstanding change and the daily chase logs it as a compliance error until the rates are
 * regenerated.
 *
 * <h2>Why daily rather than weekly</h2>
 *
 * The feed carries only the four most recent updates across all of GST - circulars included -
 * so a busy fortnight can push a rate notification off the end of it. Weekly polling would
 * eventually miss one. Daily costs one request.
 */
@Service
public class CbicNotificationDetector {

    private static final Logger log = LoggerFactory.getLogger(CbicNotificationDetector.class);

    /** The portal's own id for GST. 1000002 is customs; the rest return an empty list. */
    private static final String GST_TAX_ID = "1000001";

    /** "30-Apr-2026", as the portal formats it. */
    private static final DateTimeFormatter PORTAL_DATE =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final GstRateSourceRepository sourceRepo;
    private final GstRateFreshnessService freshnessService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gst.cbic-detector.enabled:true}")
    private boolean enabled;

    @Value("${gst.cbic-detector.base-url:https://taxinformation.cbic.gov.in}")
    private String baseUrl;

    public CbicNotificationDetector(GstRateSourceRepository sourceRepo,
                                    GstRateFreshnessService freshnessService) {
        this.sourceRepo = sourceRepo;
        this.freshnessService = freshnessService;
    }

    /** One CBIC update, reduced to what matters here. */
    public record Update(String number, String name, LocalDate dated, String category,
                         String type, String documentPath, Long documentId) {

        /**
         * Whether this changes rates.
         *
         * Only "Central Tax (Rate)" and its integrated and union-territory counterparts set
         * rates. A plain "Central Tax" notification changes procedure - the last one empowered
         * a tribunal bench - and treating it as a rate change would raise an alarm every time
         * CBIC published anything at all, which is the fastest way to get alarms ignored.
         */
        public boolean changesRates() {
            String haystack = ((category == null ? "" : category) + " " + (number == null ? "" : number))
                    .toLowerCase();
            return haystack.contains("(rate)");
        }
    }

    /**
     * The daily check.
     *
     * Runs an hour after the freshness chase so the two do not report the same morning's state
     * from different sides. Never throws: an unreachable portal must not take the scheduler
     * down, and the freshness clock keeps running regardless, so a detector that is quietly
     * failing still surfaces as an ageing verification.
     */
    @Scheduled(cron = "${gst.cbic-detector.cron:0 30 7 * * ?}")
    public void detect() {
        if (!enabled) return;
        try {
            List<Update> found = fetchLatestUpdates();
            List<Update> rateChanges = found.stream().filter(Update::changesRates).toList();
            int recorded = 0;

            for (Update u : rateChanges) {
                if (u.number() == null || u.number().isBlank()) continue;
                if (sourceRepo.findByNotificationNumber(u.number()).isPresent()) continue;

                // Effective date is not in the feed and is rarely the notification date, so it
                // is left null rather than guessed. Guessing it would put a date on a rate
                // change that nobody had read.
                GstRateSource pending = freshnessService.recordPendingNotification(
                        u.number(), u.dated(), null,
                        u.name() + (u.documentPath() == null ? ""
                                : " [CBIC document: " + u.documentPath() + "]"));

                // Fetch and fingerprint the notification as CBIC serves it today. The whole
                // argument for these rates is that they trace to a published document, and that
                // is only worth anything if the document can be shown to be unchanged since it
                // was read. Failing to fetch it must not lose the alert, which is the part that
                // actually protects anyone.
                try {
                    Document doc = fetchDocument(u.documentId());
                    if (doc != null && pending != null) {
                        pending.setCbicDocumentId(u.documentId());
                        pending.setDocumentFileName(doc.fileName());
                        pending.setDocumentSha256(doc.sha256());
                        sourceRepo.save(pending);
                        log.info("Archived the fingerprint of {} ({}, {} bytes, sha256 {}).",
                                u.number(), doc.fileName(), doc.bytes().length, doc.sha256());
                    }
                } catch (Exception docFailure) {
                    log.warn("Recorded {} but could not fetch its PDF: {}. The notification still "
                            + "needs reading; fetch it from CBIC by hand.",
                            u.number(), docFailure.getMessage());
                }
                recorded++;
                log.error("GST COMPLIANCE: CBIC has published {} ({}), which this store has not "
                                + "applied. Read it and import the rates before invoicing "
                                + "anything it covers. {}",
                        u.number(), u.dated(), u.name());
            }

            if (recorded == 0) {
                log.info("CBIC check: {} recent update(s), {} rate notification(s), none new.",
                        found.size(), rateChanges.size());
            }
        } catch (Exception e) {
            // A detector that cannot reach CBIC is not evidence that nothing has changed.
            if (isCertificateProblem(e)) {
                // Worth separating: a trust failure is a broken JVM truststore on this host, not
                // CBIC being down, and it will never recover on its own. Reported louder because
                // the fix is ours and the check is silently blind until someone makes it.
                log.error("The CBIC notification check cannot verify CBIC's TLS certificate, so "
                        + "it is not watching for rate changes at all. This is a truststore "
                        + "problem on this host, not an outage - it will not clear by itself. "
                        + "Cause: {}", e.getMessage());
            } else {
                log.warn("The CBIC notification check could not run: {}. The rate verification "
                        + "age is unaffected and still applies.", e.getMessage());
            }
        }
    }

    /** True when the failure is TLS trust rather than CBIC being unreachable or changed. */
    private boolean isCertificateProblem(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.security.cert.CertificateException
                    || t instanceof javax.net.ssl.SSLHandshakeException) return true;
            String m = t.getMessage();
            if (m != null && (m.contains("PKIX path building failed")
                    || m.contains("unable to find valid certification path"))) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }

    /**
     * Reads the portal's latest-updates feed.
     *
     * Public and unauthenticated - this is the same request the portal's own front-end makes.
     */
    public List<Update> fetchLatestUpdates() {
        String url = baseUrl + "/api/cbic-notification-msts/fetchUpdatesByTaxId/" + GST_TAX_ID;
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        // The portal serves an HTML shell to clients it does not recognise as browsers.
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; CauveryStore-GST-check/1.0)");

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        String body = response.getBody();
        if (body == null || body.isBlank()) return List.of();

        List<Update> updates = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray()) {
                // An HTML shell here means the portal changed shape. Say so rather than
                // reporting nothing found, which reads identically to all-clear.
                throw new IllegalStateException(
                        "CBIC returned something that is not the updates list. The portal's feed "
                                + "may have moved, and this check is no longer watching anything.");
            }
            for (JsonNode n : root) {
                updates.add(new Update(
                        text(n, "notificationNo"),
                        text(n, "notificationName"),
                        parsePortalDate(text(n, "updatedDate")),
                        text(n, "updateCategory"),
                        text(n, "updateType"),
                        text(n, "docFilePath"),
                        n.get("id") == null || n.get("id").isNull() ? null : n.get("id").asLong()));
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("CBIC's updates feed could not be read: " + e.getMessage(), e);
        }
        return updates;
    }

    /** A notification PDF exactly as CBIC served it. */
    public record Document(String fileName, byte[] bytes, String sha256) {}

    /**
     * Downloads one notification.
     *
     * CBIC returns the PDF base64-encoded inside a JSON envelope rather than as a file, which
     * is why a plain fetch of the page URL yields an HTML shell and looks like the document has
     * gone. Verified against 01/2026-Central Tax (Rate): the bytes this returns are identical
     * to the copy committed under cbic-source.
     */
    public Document fetchDocument(Long documentId) {
        if (documentId == null) return null;
        String url = baseUrl + "/api/cbic-notification-msts/download/" + documentId + "/ENG";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; CauveryStore-GST-check/1.0)");

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        String body = response.getBody();
        if (body == null || body.isBlank()) return null;

        try {
            JsonNode root = objectMapper.readTree(body);
            String encoded = text(root, "data");
            if (encoded == null || encoded.isBlank()) return null;
            byte[] bytes = java.util.Base64.getDecoder().decode(encoded);
            // A PDF that is not a PDF means the envelope changed shape, and a hash of the wrong
            // bytes is worse than no hash - it would look like provenance.
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P') {
                throw new IllegalStateException(
                        "CBIC returned something that is not a PDF for document " + documentId);
            }
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest(bytes)) hex.append(String.format("%02x", b));
            return new Document(text(root, "fileName"), bytes, hex.toString());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("CBIC document " + documentId
                    + " could not be read: " + e.getMessage(), e);
        }
    }

    /** What the check currently sees, for the compliance screen and for testing it by hand. */
    public Map<String, Object> preview() {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            List<Update> found = fetchLatestUpdates();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Update u : found) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("notificationNumber", u.number());
                row.put("name", u.name());
                row.put("date", u.dated());
                row.put("category", u.category());
                row.put("changesRates", u.changesRates());
                row.put("alreadyKnown", u.number() != null
                        && sourceRepo.findByNotificationNumber(u.number()).isPresent());
                rows.add(row);
            }
            out.put("source", baseUrl + "/api/cbic-notification-msts/fetchUpdatesByTaxId/" + GST_TAX_ID);
            out.put("updates", rows);
            out.put("reachable", true);
        } catch (Exception e) {
            out.put("reachable", false);
            out.put("error", e.getMessage());
        }
        return out;
    }

    private LocalDate parsePortalDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim(), PORTAL_DATE);
        } catch (Exception e) {
            return null;   // a date we cannot read is not worth failing the whole check over
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Notifications found but not yet applied, newest first. */
    public List<GstRateSource> outstanding() {
        return freshnessService.pendingNotifications();
    }
}
