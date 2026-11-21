package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Puts an agent draft on sale by itself only when nothing is missing: a person has confirmed the
 * pack label and the supplier's photo permission (SupplierLabel), and the compliance engine scores
 * the finished listing 100. Anything else stays in Agent Drafts with the reasons.
 *
 * With agents.publish.live=false (the default) it creates nothing and only reports what it would do.
 */
@Service
public class AgentAutoPublishService {

    private static final Logger log = LoggerFactory.getLogger(AgentAutoPublishService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final int MAX_PER_SWEEP = 5;
    static final String PREVIEW_NOTE = "Ready to publish automatically. Preview mode is on, so it was not published.";

    private final AgentJobRepository jobs;
    private final SupplierLabelRepository labels;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductService productService;
    private final ProductComplianceService compliance;
    private final ProductComplianceRepository complianceRecords;
    private final SupplierPhotoService photos;
    private final AuditService audit;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${agents.publish.live:false}")
    boolean live;

    @Value("${agents.publish.seller-id:}")
    String sellerId = "";

    public AgentAutoPublishService(AgentJobRepository jobs, SupplierLabelRepository labels, CategoryRepository categories,
                                   ProductRepository products, ProductService productService,
                                   ProductComplianceService compliance, ProductComplianceRepository complianceRecords,
                                   SupplierPhotoService photos, AuditService audit) {
        this.jobs = jobs;
        this.labels = labels;
        this.categories = categories;
        this.products = products;
        this.productService = productService;
        this.compliance = compliance;
        this.complianceRecords = complianceRecords;
        this.photos = photos;
        this.audit = audit;
    }

    record Plan(Product product, ProductCompliance record, List<String> blockers) {}

    /** Checks every draft waiting for review. Only one sweep runs at a time. */
    public Map<String, Object> sweep() {
        if (!running.compareAndSet(false, true)) return Map.of("busy", true);
        int checked = 0, published = 0, waiting = 0, ready = 0, failed = 0;
        try {
            List<AgentJob> open = new ArrayList<>(jobs.findByStatusInOrderByIdDesc(List.of(AgentJob.READY), PageRequest.of(0, 200)));
            Collections.reverse(open);
            for (AgentJob job : open) {
                if (published >= MAX_PER_SWEEP) break;
                checked++;
                try {
                    switch (process(job)) {
                        case "PUBLISHED" -> published++;
                        case "READY" -> ready++;
                        default -> waiting++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("Auto-publish failed for job {}: {}", job.getId(), e.getMessage());
                    note(job, "Not published automatically: " + (e.getMessage() == null ? "an unexpected problem." : e.getMessage()));
                }
            }
        } finally {
            running.set(false);
        }
        return Map.of("checked", checked, "published", published, "ready", ready, "waiting", waiting, "failed", failed, "live", live);
    }

    String process(AgentJob job) {
        Plan plan = plan(job);
        if (!plan.blockers().isEmpty()) {
            note(job, "Not published automatically. Still needed: " + String.join("; ", plan.blockers()) + ".");
            return "WAITING";
        }
        if (!live) {
            if (!PREVIEW_NOTE.equals(job.getNote())) {
                audit.log(null, "auto-publisher", "AUTO_PUBLISH_PREVIEW", "AgentJob", job.getId(), plan.product().getName(), null);
            }
            note(job, PREVIEW_NOTE);
            return "READY";
        }
        return publish(job, plan);
    }

    /** Builds the finished listing in memory and lists everything stopping it. Saves nothing. */
    @SuppressWarnings("unchecked")
    Plan plan(AgentJob job) {
        List<String> no = new ArrayList<>();
        Map<String, Object> d;
        try {
            d = JSON.readValue(job.getDraftJson(), Map.class);
        } catch (Exception e) {
            return new Plan(null, null, List.of("a readable draft"));
        }
        String sku = text(d.get("sku"));
        SupplierLabel label = sku.isEmpty() ? null : labels.findBySkuIgnoreCase(sku).orElse(null);
        if (sku.isEmpty()) no.add("the supplier's item code");
        else if (label == null) no.add("confirmed label details for " + sku + " (Admin > Supplier Labels)");
        if (label != null && !label.isPhotosAllowed()) no.add("the supplier's written permission to use photos");
        if (label != null && isBlank(label.getHsnCode())) no.add("a confirmed HSN code");
        if (!(d.get("imageUrls") instanceof List<?> images) || images.isEmpty()) no.add("product photos");

        String name = text(d.get("name"));
        if (name.toLowerCase(Locale.ROOT).matches(".*(deodap|baapstore).*")) no.add("a product name without the supplier's name");

        Long seller = parseLong(sellerId);
        if (seller == null) no.add("the store's seller account (AGENTS_PUBLISH_SELLER_ID)");

        Category category = d.get("categoryId") instanceof Number n ? categories.findById(n.longValue()).orElse(null) : null;
        if (category == null) no.add("a category");
        Double price = d.get("suggestedPrice") instanceof Number n ? n.doubleValue() : null;
        if (price == null) no.add("a selling price");
        if (label == null) return new Plan(null, null, no);

        Product p = new Product();
        p.setName(name);
        p.setBrand(text(d.get("brand")));
        p.setDescription(text(d.get("description")));
        p.setShortDescription(text(d.get("shortDescription")));
        p.setTechnicalSpecs(text(d.get("technicalSpecs")));
        p.setMetaTitle(text(d.get("metaTitle")));
        p.setMetaDescription(text(d.get("metaDescription")));
        p.setMetaKeywords(text(d.get("keywords")));
        p.setDimensions(text(d.get("dimensions")));
        p.setUom(isBlank(text(d.get("uom"))) ? "NOS" : text(d.get("uom")));
        if (d.get("weightKg") instanceof Number w) p.setWeight(w.doubleValue());
        p.setSku(sku);
        p.setCategory(category);
        p.setSellerId(seller);
        p.setPrice(price);
        p.setMrp(label.getMrp());
        p.setStock(10);
        p.setHsnCode(label.getHsnCode());
        p.setGstRateSelectionId(label.getGstRateSelectionId());
        p.setCountryOfOrigin(label.getCountryOfOrigin());
        p.setManufacturer(label.getManufacturerName());
        p.setBadges(new ArrayList<>(List.of("New Arrival")));
        p.setActive(false);
        p.setApprovalStatus("pending");
        p.setProductStatus("draft");

        ProductCompliance c = new ProductCompliance();
        copy(label, c);
        compliance.fillDefaults(c);
        ProductComplianceService.Result result = compliance.evaluate(p, c);
        if (!result.compliant()) {
            result.issues().stream().map(ProductComplianceService.Issue::label).distinct().forEach(no::add);
        }
        return new Plan(p, c, no.stream().distinct().toList());
    }

    private String publish(AgentJob job, Plan plan) {
        Long id = job.getProductId();
        if (id == null) {
            // Saved hidden first. If anything below fails it stays hidden, and the next sweep resumes it.
            id = productService.addProduct(plan.product()).getId();
            job.setProductId(id);
            jobs.save(job);
        }
        Product saved = products.findById(id).orElseThrow(() -> new IllegalStateException("The product was removed."));
        ProductCompliance record = compliance.recordFor(saved);
        copy(labels.findBySkuIgnoreCase(saved.getSku()).orElseThrow(), record);
        complianceRecords.save(record);

        if (productService.countImages(id) == 0) {
            Object added = photos.importFor(id).get("added");
            if (!(added instanceof Number n) || n.intValue() == 0) {
                note(job, "Not published automatically. Still needed: the supplier's photos could not be copied.");
                return "WAITING";
            }
        }
        ProductComplianceService.Result check = compliance.check(id);
        if (!check.compliant()) {
            note(job, "Not published automatically. Still needed: "
                    + String.join("; ", check.issues().stream().map(ProductComplianceService.Issue::label).distinct().toList()) + ".");
            return "WAITING";
        }
        productService.publishChecked(id);
        job.setStatus(AgentJob.USED);
        job.setNote("Published automatically.");
        jobs.save(job);
        audit.log(null, "auto-publisher", "AUTO_PUBLISHED", "Product", id, saved.getName(), null);
        return "PUBLISHED";
    }

    private void note(AgentJob job, String text) {
        String clipped = text.length() > 1000 ? text.substring(0, 1000) : text;
        if (clipped.equals(job.getNote())) return;
        job.setNote(clipped);
        jobs.save(job);
    }

    static void copy(SupplierLabel l, ProductCompliance c) {
        c.setManufacturerName(l.getManufacturerName());
        c.setManufacturerAddress(l.getManufacturerAddress());
        boolean samePacker = isBlank(l.getPackerName()) && isBlank(l.getPackerAddress());
        c.setPackerSameAsManufacturer(samePacker);
        c.setPackerName(samePacker ? null : l.getPackerName());
        c.setPackerAddress(samePacker ? null : l.getPackerAddress());
        c.setImporterName(l.getImporterName());
        c.setImporterAddress(l.getImporterAddress());
        c.setStateOfOrigin(l.getStateOfOrigin());
        c.setNetQuantity(l.getNetQuantity());
        c.setGrossQuantity(l.getGrossQuantity());
        c.setQuantityUnit(l.getQuantityUnit());
        c.setMfgDate(l.getMfgDate());
        c.setBatchNumber(l.getBatchNumber());
    }

    private static String text(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static Long parseLong(String s) {
        try { return isBlank(s) ? null : Long.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
