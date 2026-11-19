package com.cauverystore.service;

import com.cauverystore.entities.InboundEmail;
import com.cauverystore.repository.InboundEmailRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resend.services.emails.model.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Receives email sent to the store's own addresses.
 *
 * Resend accepts the mail (the domain's MX record points at it) and calls our webhook with
 * only the email's id - never the body. So for each call we: check the Svix signature, fetch
 * the full email from Resend's API, save it for Admin -> Inbox, and forward a copy to the
 * personal inbox(es) in inbound.forward-to.
 */
@Service
public class InboundEmailService {

    private static final Logger log = LoggerFactory.getLogger(InboundEmailService.class);
    @Value("${resend.api.base-url:https://api.resend.com}")
    private String api;
    /** Svix rejects webhooks older or newer than this, to stop replays of a captured call. */
    private static final long TOLERANCE_SECONDS = 5 * 60;
    static final String FORWARD_PREFIX = "[Cauvery Inbox] ";

    private final InboundEmailRepository repo;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${resend.api.key:}")
    private String apiKey;

    @Value("${resend.webhook.secret:}")
    private String webhookSecret;

    @Value("${inbound.forward-to:}")
    private String forwardTo;

    public InboundEmailService(InboundEmailRepository repo, EmailService emailService, ObjectMapper objectMapper) {
        this.repo = repo;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- signature

    /**
     * Svix signature check: HMAC-SHA256 over "id.timestamp.body" with the base64 key after
     * "whsec_", compared against every "v1,<sig>" in the svix-signature header.
     */
    public boolean signatureValid(String id, String timestamp, String signatureHeader, String body) {
        return signatureValid(webhookSecret, id, timestamp, signatureHeader, body, Instant.now().getEpochSecond());
    }

    static boolean signatureValid(String secret, String id, String timestamp, String signatureHeader,
                                  String body, long nowEpochSeconds) {
        if (secret == null || secret.isBlank() || id == null || timestamp == null
                || signatureHeader == null || body == null) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(nowEpochSeconds - ts) > TOLERANCE_SECONDS) return false;
        try {
            String s = secret.trim();
            if (s.startsWith("whsec_")) s = s.substring("whsec_".length());
            byte[] key = Base64.getDecoder().decode(s);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] expected = mac.doFinal((id + "." + timestamp.trim() + "." + body).getBytes(StandardCharsets.UTF_8));
            for (String part : signatureHeader.trim().split(" ")) {
                int comma = part.indexOf(',');
                if (comma < 0 || !"v1".equals(part.substring(0, comma))) continue;
                byte[] given;
                try {
                    given = Base64.getDecoder().decode(part.substring(comma + 1));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (MessageDigest.isEqual(expected, given)) return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Inbound email signature check failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isSecretConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    // ---------------------------------------------------------------- webhook

    /**
     * Handles one verified webhook body. Returns a short result for the log. Throws when the
     * email could not be fetched, so the controller answers 5xx and Resend retries.
     */
    public String handle(String rawBody) throws Exception {
        JsonNode event = objectMapper.readTree(rawBody);
        String type = event.path("type").asText("");
        if (!"email.received".equals(type)) return "ignored " + type;

        String emailId = event.path("data").path("email_id").asText("");
        if (emailId.isBlank()) return "ignored: no email_id";
        if (repo.existsByResendEmailId(emailId)) return "duplicate " + emailId;

        JsonNode email = getJson("/emails/receiving/" + emailId);
        InboundEmail saved = new InboundEmail();
        saved.setResendEmailId(emailId);
        String fromHeader = email.path("headers").path("from").asText(null);
        String fromAddress = email.path("from").asText(event.path("data").path("from").asText(""));
        saved.setFromHeader(clip(fromHeader != null && !fromHeader.isBlank() ? fromHeader : fromAddress, 500));
        saved.setFromAddress(clip(bareAddress(fromAddress), 320));
        List<String> to = textList(email.path("to"));
        saved.setToAddresses(clip(String.join(", ", to), 1000));
        saved.setCcAddresses(clip(String.join(", ", textList(email.path("cc"))), 1000));
        saved.setSubject(clip(email.path("subject").asText(""), 1000));
        saved.setTextBody(nullIfBlank(email.path("text").asText(null)));
        saved.setHtmlBody(nullIfBlank(email.path("html").asText(null)));
        saved.setReceivedAt(parseTime(email.path("created_at").asText(null)));
        JsonNode auth = email.path("authentication");
        if (auth.isObject()) {
            saved.setAuthSummary(clip("SPF " + authResult(auth.path("spf")) + ", DKIM " + authResult(auth.path("dkim"))
                    + ", DMARC " + authResult(auth.path("dmarc")), 200));
        }

        List<JsonNode> attachments = new ArrayList<>();
        if (email.path("attachments").isArray() && email.path("attachments").size() > 0) {
            try {
                JsonNode list = getJson("/emails/receiving/" + emailId + "/attachments?limit=100");
                list.path("data").forEach(attachments::add);
            } catch (Exception e) {
                log.warn("Could not list attachments of received email {}: {}", emailId, e.getMessage());
            }
        }
        List<String> names = new ArrayList<>();
        attachments.forEach(a -> names.add(a.path("filename").asText("attachment")));
        if (names.isEmpty()) email.path("attachments").forEach(a -> names.add(a.path("filename").asText("attachment")));
        saved.setAttachmentNames(names.isEmpty() ? null : String.join("\n", names));

        try {
            saved = repo.save(saved);
        } catch (DataIntegrityViolationException e) {
            return "duplicate " + emailId; // a retry raced this one
        }

        List<String> targets = forwardTargets(to);
        if (targets.isEmpty() || isOwnForward(saved)) {
            return "saved " + saved.getId() + " (not forwarded)";
        }
        boolean sent = forward(saved, targets, attachments);
        if (sent) {
            saved.setForwardedTo(String.join(", ", targets));
            repo.save(saved);
        }
        return "saved " + saved.getId() + (sent ? ", forwarded" : ", forward FAILED");
    }

    /** inbound.forward-to minus any address the email was sent to, so a mis-set value cannot loop. */
    List<String> forwardTargets(List<String> receivedFor) {
        Set<String> received = new LinkedHashSet<>();
        receivedFor.forEach(a -> received.add(bareAddress(a).toLowerCase(Locale.ROOT)));
        List<String> out = new ArrayList<>();
        if (forwardTo == null) return out;
        for (String a : forwardTo.split(",")) {
            String t = a.trim();
            if (!t.isEmpty() && t.contains("@") && !received.contains(t.toLowerCase(Locale.ROOT))) out.add(t);
        }
        return out;
    }

    /** One of our own forwards bounced back in (e.g. forward-to pointed at a store address). */
    private static boolean isOwnForward(InboundEmail e) {
        return e.getSubject() != null && e.getSubject().startsWith(FORWARD_PREFIX);
    }

    private boolean forward(InboundEmail e, List<String> targets, List<JsonNode> attachments) {
        String subject = FORWARD_PREFIX + (e.getSubject() == null || e.getSubject().isBlank() ? "(no subject)" : e.getSubject());
        List<Attachment> files = new ArrayList<>();
        for (JsonNode a : attachments) {
            String url = a.path("download_url").asText("");
            if (url.isBlank()) continue;
            Attachment.Builder b = Attachment.builder().fileName(a.path("filename").asText("attachment")).path(url);
            String ct = a.path("content_type").asText("");
            if (!ct.isBlank()) b.contentType(ct);
            files.add(b.build());
        }
        String html = forwardHtml(e, null);
        if (emailService.forwardReceived(targets, e.getFromAddress(), subject, html, files)) return true;
        if (files.isEmpty()) return false;
        // Attachments are the usual reason a send is refused (too large, odd type). Send the
        // message without them rather than not at all; they stay listed in Admin -> Inbox.
        return emailService.forwardReceived(targets, e.getFromAddress(), subject,
                forwardHtml(e, "The attachments could not be forwarded. Open Admin &rarr; Inbox to see their names."), List.of());
    }

    private static String forwardHtml(InboundEmail e, String notice) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:Arial,sans-serif;font-size:13px;color:#374151;background:#F5F1EA;padding:12px 14px;border-radius:6px;margin-bottom:14px;'>")
                .append("<strong>Received at ").append(EmailService.escape(e.getToAddresses())).append("</strong><br/>")
                .append("From: ").append(EmailService.escape(e.getFromHeader())).append("<br/>");
        if (e.getCcAddresses() != null && !e.getCcAddresses().isBlank()) {
            sb.append("Cc: ").append(EmailService.escape(e.getCcAddresses())).append("<br/>");
        }
        sb.append("Subject: ").append(EmailService.escape(e.getSubject())).append("<br/>");
        if (e.getAuthSummary() != null) {
            sb.append("<span style='color:#6b7280;'>Checks: ").append(EmailService.escape(e.getAuthSummary())).append("</span><br/>");
        }
        sb.append("<span style='color:#6b7280;'>Press Reply to answer the sender directly.</span>");
        if (notice != null) sb.append("<br/><span style='color:#D93A2A;'>").append(notice).append("</span>");
        sb.append("</div>");
        if (e.getHtmlBody() != null) {
            sb.append(e.getHtmlBody());
        } else if (e.getTextBody() != null) {
            sb.append("<pre style='white-space:pre-wrap;font-family:Arial,sans-serif;font-size:14px;'>")
                    .append(EmailService.escape(e.getTextBody())).append("</pre>");
        } else {
            sb.append("<p style='color:#6b7280;'>(empty message)</p>");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode getJson(String path) throws Exception {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("placeholder")) {
            throw new IllegalStateException("RESEND_API_KEY is not set");
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(api + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Resend " + path + " answered " + res.statusCode() + ": " + clip(res.body(), 300));
        }
        return objectMapper.readTree(res.body());
    }

    private static List<String> textList(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n.isArray()) n.forEach(x -> { if (!x.asText("").isBlank()) out.add(x.asText()); });
        else if (n.isTextual() && !n.asText().isBlank()) out.add(n.asText());
        return out;
    }

    static String bareAddress(String s) {
        if (s == null) return "";
        int lt = s.lastIndexOf('<');
        int gt = s.lastIndexOf('>');
        if (lt >= 0 && gt > lt) return s.substring(lt + 1, gt).trim();
        return s.trim();
    }

    private static String authResult(JsonNode n) {
        if (n.isTextual()) return n.asText();
        if (n.has("result")) return n.path("result").asText("?");
        if (n.has("status")) return n.path("status").asText("?");
        return n.isMissingNode() || n.isNull() ? "?" : n.toString();
    }

    private static LocalDateTime parseTime(String s) {
        try {
            if (s != null) return OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
        } catch (Exception ignored) {
            // fall through
        }
        return LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String nullIfBlank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
