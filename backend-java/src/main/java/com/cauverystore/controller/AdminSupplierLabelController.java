package com.cauverystore.controller;

import com.cauverystore.entities.AgentJob;
import com.cauverystore.entities.SupplierLabel;
import com.cauverystore.repository.AgentJobRepository;
import com.cauverystore.repository.SupplierLabelRepository;
import com.cauverystore.service.AgentAutoPublishService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Label details and photo permission a person confirmed from the supplier. */
@RestController
@RequestMapping("/api/admin/supplier-labels")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminSupplierLabelController {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final SupplierLabelRepository labels;
    private final AgentJobRepository jobs;
    private final AgentAutoPublishService publisher;

    public AdminSupplierLabelController(SupplierLabelRepository labels, AgentJobRepository jobs, AgentAutoPublishService publisher) {
        this.labels = labels;
        this.jobs = jobs;
        this.publisher = publisher;
    }

    @GetMapping
    @SuppressWarnings("unchecked")
    public Map<String, Object> list() {
        List<Map<String, Object>> waiting = new ArrayList<>();
        for (AgentJob j : jobs.findByStatusInOrderByIdDesc(List.of(AgentJob.READY), PageRequest.of(0, 200))) {
            Map<String, Object> d = Map.of();
            try { if (j.getDraftJson() != null) d = JSON.readValue(j.getDraftJson(), Map.class); } catch (Exception ignored) { }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("jobId", j.getId());
            row.put("name", d.getOrDefault("name", ""));
            row.put("sku", d.getOrDefault("sku", ""));
            row.put("mrp", d.get("supplierMrp"));
            row.put("hsnCode", d.getOrDefault("hsnCode", ""));
            row.put("countryOfOrigin", d.getOrDefault("countryOfOrigin", ""));
            row.put("note", j.getNote());
            waiting.add(row);
        }
        return Map.of("labels", labels.findAll(Sort.by("sku")), "waiting", waiting);
    }

    /** Saves one label, then re-checks the waiting drafts. */
    @PutMapping
    public ResponseEntity<?> save(@RequestBody Map<String, Object> b, Authentication auth) {
        String sku = text(b.get("sku")).replaceAll("[^A-Za-z0-9_./-]", "");
        if (sku.isEmpty() || sku.length() > 60) return ResponseEntity.badRequest().body(Map.of("error", "Enter the supplier's item code."));
        SupplierLabel l = labels.findBySkuIgnoreCase(sku).orElseGet(SupplierLabel::new);
        try {
            l.setSku(sku);
            String hsn = text(b.get("hsnCode")).replaceAll("\\s", "");
            if (!hsn.isEmpty() && !hsn.matches("\\d{4}|\\d{6}|\\d{8}")) throw new IllegalArgumentException("An HSN code has 4, 6 or 8 digits.");
            l.setHsnCode(hsn.isEmpty() ? null : hsn);
            l.setGstRateSelectionId(text(b.get("gstRateSelectionId")).isEmpty() ? null : Long.valueOf(text(b.get("gstRateSelectionId"))));
            l.setMrp(text(b.get("mrp")).isEmpty() ? null : Double.valueOf(text(b.get("mrp"))));
            l.setManufacturerName(clip(b.get("manufacturerName"), 200));
            l.setManufacturerAddress(clip(b.get("manufacturerAddress"), 500));
            l.setPackerName(clip(b.get("packerName"), 200));
            l.setPackerAddress(clip(b.get("packerAddress"), 500));
            l.setImporterName(clip(b.get("importerName"), 200));
            l.setImporterAddress(clip(b.get("importerAddress"), 500));
            l.setCountryOfOrigin(clip(b.get("countryOfOrigin"), 60));
            l.setStateOfOrigin(clip(b.get("stateOfOrigin"), 60));
            l.setNetQuantity(text(b.get("netQuantity")).isEmpty() ? null : new BigDecimal(text(b.get("netQuantity"))));
            l.setGrossQuantity(text(b.get("grossQuantity")).isEmpty() ? null : new BigDecimal(text(b.get("grossQuantity"))));
            l.setQuantityUnit(clip(b.get("quantityUnit"), 20));
            l.setMfgDate(text(b.get("mfgDate")).isEmpty() ? null : LocalDate.parse(text(b.get("mfgDate"))));
            l.setBatchNumber(clip(b.get("batchNumber"), 60));
            l.setPhotosAllowed(Boolean.TRUE.equals(b.get("photosAllowed")));
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            String m = e instanceof NumberFormatException ? "Check the numbers you entered."
                    : e instanceof java.time.DateTimeException ? "Check the manufacturing date." : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("error", m));
        }
        l.setConfirmedBy(auth != null ? auth.getName() : null);
        SupplierLabel saved = labels.save(l);
        return ResponseEntity.ok(Map.of("label", saved, "sweep", publisher.sweep()));
    }

    private static String text(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    private static String clip(Object o, int max) {
        String s = text(o);
        return s.isEmpty() ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
