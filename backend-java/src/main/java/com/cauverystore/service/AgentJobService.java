package com.cauverystore.service;

import com.cauverystore.entities.AgentJob;
import com.cauverystore.entities.Category;
import com.cauverystore.repository.AgentJobRepository;
import com.cauverystore.repository.CategoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * The store's side of the agents service: a work queue the agents pull from, and the checks a
 * draft must pass before an admin sees it. The store, not the agent, decides the price (fixed
 * rule), whether a category exists and whether an HSN code is real.
 */
@Service
public class AgentJobService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> OPEN = List.of(AgentJob.QUEUED, AgentJob.WORKING, AgentJob.READY);
    private static final Set<String> SUPPLIER_HOSTS = Set.of("deodap.in", "www.deodap.in", "baapstore.com", "www.baapstore.com");
    private static final List<String> UOMS = List.of("NOS", "PCS", "SET", "PACK", "PAIR", "BOX", "KG", "G", "ML", "L");
    private static final int MAX_ATTEMPTS = 3;

    private final AgentJobRepository jobs;
    private final CategoryRepository categories;
    private final HsnClassificationService hsn;

    public AgentJobService(AgentJobRepository jobs, CategoryRepository categories, HsnClassificationService hsn) {
        this.jobs = jobs;
        this.categories = categories;
        this.hsn = hsn;
    }

    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) { super(message); }
    }

    // ---------------------------------------------------------------- admin side

    /** Queues supplier links, one job each. Links already waiting or drafted are skipped. */
    @Transactional
    public Map<String, Object> queue(List<String> links, Double supplierCost, String by) {
        int added = 0;
        List<String> skipped = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : links) {
            if (raw == null || raw.isBlank()) continue;
            String link = normaliseLink(raw.trim());
            if (link == null) { refused.add(raw.trim()); continue; }
            if (!seen.add(link) || jobs.existsBySourceUrlAndStatusIn(link, OPEN)) { skipped.add(link); continue; }
            AgentJob j = new AgentJob();
            j.setSourceUrl(link);
            j.setSupplierCost(supplierCost != null && supplierCost > 0 ? supplierCost : null);
            j.setQueuedBy(by);
            jobs.save(j);
            added++;
        }
        if (added + skipped.size() + refused.size() > 0 && added == 0 && skipped.isEmpty()) {
            throw new BadRequest("Only DeoDap and Baapstore single-product links can be queued (they contain /products/).");
        }
        return Map.of("added", added, "skipped", skipped, "refused", refused);
    }

    public List<Map<String, Object>> list(String view) {
        List<String> statuses = switch (view == null ? "" : view) {
            case "done" -> List.of(AgentJob.USED, AgentJob.REJECTED);
            case "failed" -> List.of(AgentJob.FAILED);
            case "waiting" -> List.of(AgentJob.QUEUED, AgentJob.WORKING);
            default -> List.of(AgentJob.READY);
        };
        return jobs.findByStatusInOrderByIdDesc(statuses, PageRequest.of(0, 200)).stream().map(this::view).toList();
    }

    public Map<String, Object> counts() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ready", jobs.findByStatusInOrderByIdDesc(List.of(AgentJob.READY), PageRequest.of(0, 500)).size());
        m.put("waiting", jobs.findByStatusInOrderByIdDesc(List.of(AgentJob.QUEUED, AgentJob.WORKING), PageRequest.of(0, 500)).size());
        m.put("failed", jobs.findByStatusInOrderByIdDesc(List.of(AgentJob.FAILED), PageRequest.of(0, 500)).size());
        return m;
    }

    @Transactional
    public Map<String, Object> reject(Long id) {
        AgentJob j = find(id);
        j.setStatus(AgentJob.REJECTED);
        return view(jobs.save(j));
    }

    @Transactional
    public Map<String, Object> markUsed(Long id, Long productId) {
        AgentJob j = find(id);
        j.setStatus(AgentJob.USED);
        j.setProductId(productId);
        return view(jobs.save(j));
    }

    @Transactional
    public Map<String, Object> requeue(Long id) {
        AgentJob j = find(id);
        j.setStatus(AgentJob.QUEUED);
        j.setAttempts(0);
        j.setNote(null);
        return view(jobs.save(j));
    }

    // ---------------------------------------------------------------- agent side

    /**
     * Hands the agent up to {@code limit} queued jobs and marks them as being worked on. Jobs an
     * agent took but never answered (it crashed) go back on the queue after 30 minutes.
     */
    @Transactional
    public List<Map<String, Object>> claim(String agentName, int limit) {
        for (AgentJob stuck : jobs.findByStatusAndUpdatedAtBefore(AgentJob.WORKING, LocalDateTime.now().minusMinutes(30))) {
            stuck.setStatus(stuck.getAttempts() >= MAX_ATTEMPTS ? AgentJob.FAILED : AgentJob.QUEUED);
            if (AgentJob.FAILED.equals(stuck.getStatus())) stuck.setNote("The agent did not finish this job after " + MAX_ATTEMPTS + " tries.");
            jobs.save(stuck);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentJob j : jobs.findByAgentNameAndStatusOrderByIdAsc(agentName, AgentJob.QUEUED, PageRequest.of(0, Math.max(1, Math.min(limit, 50))))) {
            j.setStatus(AgentJob.WORKING);
            j.setAttempts(j.getAttempts() + 1);
            jobs.save(j);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", j.getId());
            m.put("sourceUrl", j.getSourceUrl());
            m.put("supplierCost", j.getSupplierCost());
            out.add(m);
        }
        return out;
    }

    /**
     * How many jobs are waiting for this agent, without taking any. The agents service asks this
     * first so it only starts its AI model (and pays for the server) when there is work. Jobs an
     * agent took and never finished count too, since the next run will pick them up again.
     */
    public long pendingCount(String agentName) {
        return jobs.countByAgentNameAndStatus(agentName, AgentJob.QUEUED)
                + jobs.countByAgentNameAndStatusAndUpdatedAtBefore(agentName, AgentJob.WORKING, LocalDateTime.now().minusMinutes(30));
    }

    /** Store-side context the agent needs to write a draft: the category list. */
    public List<Map<String, Object>> categoryList() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Category c : categories.findAll()) out.add(Map.of("id", c.getId(), "name", c.getName() == null ? "" : c.getName()));
        return out;
    }

    /** The agent's answer for one job: either {"failed": "reason"} or the draft fields. */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> acceptResult(Long id, Map<String, Object> body) {
        AgentJob j = find(id);
        if (!AgentJob.WORKING.equals(j.getStatus()) && !AgentJob.QUEUED.equals(j.getStatus())) {
            throw new BadRequest("This job is no longer waiting for the agent (status " + j.getStatus() + ").");
        }
        Object failed = body.get("failed");
        if (failed instanceof String reason && !reason.isBlank()) {
            j.setStatus(AgentJob.FAILED);
            j.setNote(clip(reason, 1000));
            jobs.save(j);
            return view(j);
        }

        List<String> warnings = new ArrayList<>();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", clip(str(body.get("name")), 150));
        if (((String) d.get("name")).isBlank()) throw new BadRequest("A draft needs a product name.");
        d.put("brand", clip(str(body.get("brand")), 80));

        Long categoryId = asLong(body.get("categoryId"));
        Set<Long> known = new HashSet<>();
        categories.findAll().forEach(c -> known.add(c.getId()));
        if (categoryId != null && !known.contains(categoryId)) categoryId = null;
        if (categoryId == null) warnings.add("Choose the category yourself.");
        d.put("categoryId", categoryId);

        d.put("shortDescription", clip(str(body.get("shortDescription")), 150));
        d.put("description", clip(str(body.get("description")), 5000));
        d.put("technicalSpecs", clip(str(body.get("technicalSpecs")), 1000));
        d.put("metaTitle", clip(str(body.get("metaTitle")), 60));
        d.put("metaDescription", clip(str(body.get("metaDescription")), 160));
        d.put("keywords", clip(str(body.get("keywords")), 300));
        d.put("dimensions", clip(str(body.get("dimensions")), 60));
        String uom = str(body.get("uom")).toUpperCase(Locale.ROOT);
        d.put("uom", UOMS.contains(uom) ? uom : "NOS");
        Double w = asDouble(body.get("weightKg"));
        d.put("weightKg", w != null && w > 0 && w < 500 ? w : null);
        String origin = str(body.get("countryOfOrigin"));
        d.put("countryOfOrigin", origin.equalsIgnoreCase("China") ? "China" : origin.equalsIgnoreCase("India") ? "India" : "");

        // The HSN code is passed on only if it is a real code in the official master.
        String code = str(body.get("hsnCode")).replaceAll("\\D", "");
        Optional<String> official = code.length() >= 4 ? hsn.describeCode(code) : Optional.empty();
        if (official.isPresent()) {
            d.put("hsnCode", code);
            d.put("hsnDescription", official.get());
            d.put("hsnReason", clip(str(body.get("hsnReason")), 300));
            warnings.add("Check the HSN code and GST rate before saving; the agent's suggestion can be wrong.");
        } else {
            d.put("hsnCode", "");
            warnings.add("No valid HSN code" + (code.isEmpty() ? "" : " (" + code + " is not in the official list)") + ". Pick it with the HSN picker.");
        }

        // The price is the store's rule, not the agent's: cost x2, rounded up to end in 9.
        Double cost = j.getSupplierCost() != null ? j.getSupplierCost() : asDouble(body.get("supplierPrice"));
        if (cost != null && cost > 0) {
            d.put("supplierCost", cost);
            d.put("suggestedPrice", priceRule(cost));
        } else {
            warnings.add("No supplier cost found, so no price was suggested.");
        }
        Double mrp = asDouble(body.get("supplierMrp"));
        if (mrp != null && mrp > 0) {
            d.put("supplierMrp", mrp);
            Object price = d.get("suggestedPrice");
            if (price instanceof Number n && n.doubleValue() > mrp) {
                warnings.add("The suggested price ₹" + n.longValue() + " is above the supplier's MRP ₹" + mrp
                        + ". A product cannot be sold above its MRP; lower the price or check the MRP on the pack.");
            }
        }

        // The supplier's own item code, so the product can be matched to the supplier's stock later.
        d.put("sku", clip(str(body.get("supplierSku")).replaceAll("[^A-Za-z0-9_./-]", ""), 60));

        Object images = body.get("imageUrls");
        if (images instanceof List<?> list) {
            d.put("imageUrls", list.stream().map(String::valueOf).filter(s -> s.startsWith("https://")).limit(8).toList());
        }
        if (body.get("warnings") instanceof List<?> list) {
            list.stream().map(String::valueOf).filter(s -> !s.isBlank()).limit(10).forEach(s -> warnings.add(clip(s, 200)));
        }
        d.put("warnings", warnings);
        d.put("model", clip(str(body.get("model")), 80));

        try {
            j.setDraftJson(JSON.writeValueAsString(d));
        } catch (Exception e) {
            throw new BadRequest("The draft could not be stored.");
        }
        j.setStatus(AgentJob.READY);
        j.setNote(null);
        jobs.save(j);
        return view(j);
    }

    // ---------------------------------------------------------------- helpers

    /** Supplier cost doubled, rounded up to the next whole rupee ending in 9 (151.4 -> 309). */
    public static long priceRule(double cost) {
        long p = (long) Math.ceil(cost * 2 - 1e-9);
        long rem = p % 10;
        return rem == 9 ? p : p + (9 - rem);
    }

    /** Accepts only https links to one product on a known supplier site; returns a clean form. */
    static String normaliseLink(String link) {
        try {
            java.net.URI u = java.net.URI.create(link);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(u.getScheme()) || !SUPPLIER_HOSTS.contains(host)) return null;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("/products/([A-Za-z0-9\\-_%.]+?)(?:\\.js|\\.json)?/?$").matcher(u.getPath() == null ? "" : u.getPath());
            if (!m.find()) return null;
            return "https://" + host.replaceFirst("^www\\.", "") + "/products/" + m.group(1);
        } catch (Exception e) {
            return null;
        }
    }

    private AgentJob find(Long id) {
        return jobs.findById(id).orElseThrow(() -> new BadRequest("No such job."));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> view(AgentJob j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.getId());
        m.put("agentName", j.getAgentName());
        m.put("sourceUrl", j.getSourceUrl());
        m.put("status", j.getStatus());
        m.put("supplierCost", j.getSupplierCost());
        m.put("note", j.getNote());
        m.put("productId", j.getProductId());
        m.put("attempts", j.getAttempts());
        m.put("queuedBy", j.getQueuedBy());
        m.put("createdAt", j.getCreatedAt());
        m.put("updatedAt", j.getUpdatedAt());
        if (j.getDraftJson() != null) {
            try { m.put("draft", JSON.readValue(j.getDraftJson(), Map.class)); } catch (Exception ignored) {}
        }
        return m;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max).trim() : s;
    }

    private static Long asLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        try { return o == null ? null : Long.parseLong(String.valueOf(o).trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Double asDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return o == null ? null : Double.parseDouble(String.valueOf(o).trim()); } catch (NumberFormatException e) { return null; }
    }
}
