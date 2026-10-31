package com.cauverystore.service;

import com.cauverystore.entities.GstRuleUpdate;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.repository.GstRuleUpdateRepository;
import com.cauverystore.repository.UserRepository;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Watches CBIC for GST rule changes: the notifications and circulars that are not rate
 * notifications.
 *
 * CbicNotificationDetector handles rates, because a missed rate is an invoice charged wrongly.
 * A missed rule is just as real - new e-invoicing categories, amended CGST Rules, a duty put on
 * e-commerce operators - but it cannot be applied by changing a number, so this only records
 * each one, emails the admins once per run, and leaves it on the GST compliance screen until a
 * person has read it and said whether the store must act.
 *
 * It reads the same public JSON the portal's own pages read on taxinformation.cbic.gov.in:
 * each notification category's full list, and the GST circulars list. Only items dated within
 * {@code gst.rule-watch.lookback-days} are recorded, so the 2017-onwards archive does not arrive
 * as hundreds of alerts on the first run.
 */
@Service
public class GstRuleWatchService {

    private static final Logger log = LoggerFactory.getLogger(GstRuleWatchService.class);

    private static final String GST_TAX_ID = "1000001";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; CauveryStore-GST-check/1.0)";

    /**
     * Notification categories watched here. The Central, Integrated and Union Territory "(Rate)"
     * categories belong to the rate detector. Compensation Cess (Rate) is here rather than there
     * because the rate drafting reads GST schedules, not cess tables; a cess change still needs
     * a person to update the seed's cessRate, and this makes sure someone is told.
     */
    static final List<String> RULE_CATEGORIES = List.of(
            "Central Tax", "Integrated Tax", "Union Territory Tax",
            "Compensation Cess", "Compensation Cess (Rate)");

    private final GstRuleUpdateRepository repo;
    private final UserRepository userRepo;
    private final EmailService emailService;
    private final RestTemplate restTemplate = CbicTls.restTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gst.rule-watch.enabled:true}")
    private boolean enabled = true;

    @Value("${gst.cbic-detector.base-url:https://taxinformation.cbic.gov.in}")
    private String baseUrl = "https://taxinformation.cbic.gov.in";

    @Value("${gst.rule-watch.lookback-days:90}")
    private int lookbackDays = 90;

    /** What the last run saw, for the admin screen: a quiet watcher must be told from a blind one. */
    private volatile LocalDateTime lastRunAt;
    private volatile List<String> lastProblems = List.of();

    public GstRuleWatchService(GstRuleUpdateRepository repo, UserRepository userRepo,
                               EmailService emailService) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.emailService = emailService;
    }

    /** One item from a CBIC list. */
    public record Item(String kind, String category, String number, String title,
                       LocalDate issuedOn, Long documentId, String documentPath) {}

    /** Daily, a quarter of an hour after the rate check. Never throws. */
    @Scheduled(cron = "${gst.rule-watch.cron:0 45 7 * * ?}")
    public void scheduledCheck() {
        if (!enabled) return;
        try {
            check();
        } catch (Exception e) {
            log.warn("The GST rule check could not run: {}", e.getMessage());
        }
    }

    /**
     * Reads every source, records what is new and emails the admins once.
     *
     * @return what was found and which sources could not be read
     */
    public Map<String, Object> check() {
        LocalDate since = LocalDate.now().minusDays(lookbackDays);
        List<Item> seen = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        for (String category : RULE_CATEGORIES) {
            try {
                seen.addAll(fetchNotifications(category, since));
            } catch (Exception e) {
                problems.add(category + " notifications: " + e.getMessage());
            }
        }
        try {
            seen.addAll(fetchCirculars(since));
        } catch (Exception e) {
            problems.add("Circulars: " + e.getMessage());
        }

        List<GstRuleUpdate> recorded = new ArrayList<>();
        Set<String> thisRun = new LinkedHashSet<>();
        for (Item item : seen) {
            if (item.documentId() == null || item.number() == null || item.number().isBlank()) continue;
            if (!thisRun.add(item.kind() + ":" + item.documentId())) continue;
            if (repo.existsByKindAndCbicDocumentId(item.kind(), item.documentId())) continue;
            GstRuleUpdate u = new GstRuleUpdate();
            u.setKind(item.kind());
            u.setCategory(item.category());
            u.setNumber(item.number());
            u.setTitle(item.title());
            u.setIssuedOn(item.issuedOn());
            u.setCbicDocumentId(item.documentId());
            u.setDocumentPath(item.documentPath());
            u.setFirstSeenAt(LocalDateTime.now());
            u.setStatus(GstRuleUpdate.STATUS_NEW);
            recorded.add(repo.save(u));
        }

        lastRunAt = LocalDateTime.now();
        lastProblems = List.copyOf(problems);
        int sources = RULE_CATEGORIES.size() + 1;
        if (problems.size() == sources) {
            // Nothing could be read: "nothing new" would be a guess, so say so loudly.
            log.error("The GST rule check could not read CBIC at all, so it is not watching for "
                    + "rule changes. {}", problems);
        } else {
            problems.forEach(p -> log.warn("Part of the GST rule check could not run: {}", p));
        }
        if (!recorded.isEmpty()) {
            for (GstRuleUpdate u : recorded) {
                log.warn("GST RULE CHANGE: CBIC published {} ({}): {}. Read it and record whether "
                        + "the store must act.", u.getNumber(), u.getIssuedOn(), u.getTitle());
            }
            alertAdmins(recorded);
        } else {
            log.info("GST rule check: {} item(s) in the last {} days, none new.", seen.size(), lookbackDays);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("found", recorded.size());
        out.put("seen", seen.size());
        out.put("problems", problems);
        out.put("reachable", problems.size() < sources);
        return out;
    }

    /** One rule-notification category from the last {@code since} onwards. */
    public List<Item> fetchNotifications(String category, LocalDate since) {
        URI url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .pathSegment("api", "cbic-notification-msts", "fetchNotificationByCategory", GST_TAX_ID, category)
                .encode().build().toUri();
        return parseList(GstRuleUpdate.KIND_NOTIFICATION, category, get(url), since);
    }

    /** The GST circulars list from {@code since} onwards. */
    public List<Item> fetchCirculars(LocalDate since) {
        URI url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .pathSegment("api", "cbic-circular-msts", "fetchAllCircularsByTaxId", GST_TAX_ID)
                .encode().build().toUri();
        return parseList(GstRuleUpdate.KIND_CIRCULAR, "Circulars", get(url), since);
    }

    /**
     * Reads a notification or circular list. The two use different field names for the same
     * things, so both are tried.
     */
    List<Item> parseList(String kind, String fallbackCategory, String body, LocalDate since) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("CBIC returned nothing.");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("not JSON - the portal may have changed", e);
        }
        if (!root.isArray()) {
            throw new IllegalStateException("not a list - the portal may have changed");
        }
        boolean circular = GstRuleUpdate.KIND_CIRCULAR.equals(kind);
        List<Item> out = new ArrayList<>();
        for (JsonNode n : root) {
            if ("N".equalsIgnoreCase(text(n, "isActive"))) continue;
            LocalDate dated = parseDate(text(n, circular ? "circularDt" : "notificationDt"));
            if (dated == null) dated = parseDate(text(n, "issueDt"));
            // Undated rows are kept: dropping one could hide the very item that matters, and the
            // repository check stops it repeating.
            if (dated != null && dated.isBefore(since)) continue;
            String category = text(n, circular ? "circularCategory" : "notificationCategory");
            JsonNode id = n.get("id");
            out.add(new Item(kind,
                    category == null || category.isBlank() ? fallbackCategory : category,
                    clean(text(n, circular ? "circularNo" : "notificationNo")),
                    clean(text(n, circular ? "circularName" : "notificationName")),
                    dated,
                    id == null || id.isNull() ? null : id.asLong(),
                    text(n, "docFilePath")));
        }
        return out;
    }

    /** The PDF of a recorded item, as CBIC serves it, for a reviewer to read. */
    public byte[] fetchDocument(GstRuleUpdate u) {
        String resource = GstRuleUpdate.KIND_CIRCULAR.equals(u.getKind())
                ? "cbic-circular-msts" : "cbic-notification-msts";
        URI url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .pathSegment("api", resource, "download", String.valueOf(u.getCbicDocumentId()), "ENG")
                .encode().build().toUri();
        String body = get(url);
        try {
            String encoded = text(objectMapper.readTree(body), "data");
            if (encoded == null || encoded.isBlank()) throw new IllegalStateException("no document in the reply");
            byte[] bytes = java.util.Base64.getDecoder().decode(encoded);
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P') {
                throw new IllegalStateException("CBIC returned something that is not a PDF");
            }
            return bytes;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("The document could not be read: " + e.getMessage(), e);
        }
    }

    /** Records a person's reading of an item. */
    public GstRuleUpdate review(Long id, String status, String note, String by) {
        if (!List.of(GstRuleUpdate.STATUS_NEW, GstRuleUpdate.STATUS_ACTION_NEEDED,
                GstRuleUpdate.STATUS_REVIEWED, GstRuleUpdate.STATUS_NOT_RELEVANT).contains(status)) {
            throw new IllegalArgumentException("Unknown status: " + status);
        }
        if (GstRuleUpdate.STATUS_ACTION_NEEDED.equals(status) && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("Say what the store needs to do.");
        }
        GstRuleUpdate u = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("That item is not recorded."));
        u.setStatus(status);
        u.setReviewNote(note == null || note.isBlank() ? null : note.trim());
        boolean reopened = GstRuleUpdate.STATUS_NEW.equals(status);
        u.setReviewedBy(reopened ? null : by);
        u.setReviewedAt(reopened ? null : LocalDateTime.now());
        return repo.save(u);
    }

    public List<GstRuleUpdate> list() {
        return repo.findAllByOrderByIssuedOnDescIdDesc();
    }

    public GstRuleUpdate get(Long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("That item is not recorded."));
    }

    /** Counts and last-run health for the screen. */
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("new", repo.countByStatus(GstRuleUpdate.STATUS_NEW));
        out.put("actionNeeded", repo.countByStatus(GstRuleUpdate.STATUS_ACTION_NEEDED));
        out.put("lastRunAt", lastRunAt);
        out.put("lastProblems", lastProblems);
        out.put("lookbackDays", lookbackDays);
        out.put("sources", "taxinformation.cbic.gov.in: " + String.join(", ", RULE_CATEGORIES)
                + " notifications, and GST circulars");
        return out;
    }

    private void alertAdmins(List<GstRuleUpdate> found) {
        try {
            Set<String> recipients = new LinkedHashSet<>();
            for (Role role : List.of(Role.SUPER_ADMIN, Role.ADMIN)) {
                for (User u : userRepo.findByRole(role)) {
                    if (u.getEmail() != null && !u.getEmail().isBlank() && u.isActive()) {
                        recipients.add(u.getEmail().trim());
                    }
                }
            }
            for (String to : recipients) emailService.sendGstRuleUpdatesDigest(to, found);
            if (recipients.isEmpty()) {
                log.error("GST rule changes were found and there is no active admin with an email "
                        + "address to tell. They are on the GST compliance screen.");
            }
        } catch (Exception e) {
            log.error("The GST rule change email could not be sent: {}. The changes are recorded "
                    + "and show on the GST compliance screen regardless.", e.getMessage());
        }
    }

    private String get(URI url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        // The portal serves an HTML shell to clients it does not recognise as browsers.
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return response.getBody();
    }

    private static String clean(String s) {
        return s == null ? null : String.join(" ", s.trim().split("\\s+"));
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.length() < 10) return null;
        try {
            return LocalDate.parse(raw.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
