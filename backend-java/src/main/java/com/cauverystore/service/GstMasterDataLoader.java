package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.HsnMaster;
import com.cauverystore.entities.MasterUpdateLog;
import com.cauverystore.entities.StateMaster;
import com.cauverystore.entities.UnitMaster;
import com.cauverystore.repository.GstRateMasterRepository;
import com.cauverystore.repository.HsnMasterRepository;
import com.cauverystore.repository.MasterUpdateLogRepository;
import com.cauverystore.repository.StateMasterRepository;
import com.cauverystore.repository.UnitMasterRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the GST master code files (downloaded from einvoice.gst.gov.in/master-codes and
 * committed under resources/master-data) into PostgreSQL.
 *
 * Runs on startup and is idempotent: it only writes rows that are new or whose description
 * changed, so restarting does not churn the tables. Every run is recorded in
 * master_update_logs with counts, which is what makes a later code revision traceable.
 *
 * Deliberately does NOT load GST rates - the portal publishes classification codes only,
 * and rates are entered/verified separately (see GstRateMaster).
 */
@Service
public class GstMasterDataLoader {

    private static final Logger log = LoggerFactory.getLogger(GstMasterDataLoader.class);

    /** Bump when the committed master files are refreshed from the portal. */
    private static final String MASTER_VERSION = "1.0 (portal last-updated 2026-06-24)";

    /** Bump when the rate seed is regenerated from a newer CBIC notification. */
    private static final String RATE_SOURCE_VERSION =
            "CBIC Ready Reckoner as on 22-09-2025 (Notif. 09/2025 & 10/2025-CT(Rate))";

    private final HsnMasterRepository hsnRepo;
    private final UnitMasterRepository unitRepo;
    private final StateMasterRepository stateRepo;
    private final GstRateMasterRepository rateRepo;
    private final MasterUpdateLogRepository logRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gst.master-data.load-on-startup:true}")
    private boolean loadOnStartup;

    public GstMasterDataLoader(HsnMasterRepository hsnRepo,
                               UnitMasterRepository unitRepo,
                               StateMasterRepository stateRepo,
                               GstRateMasterRepository rateRepo,
                               MasterUpdateLogRepository logRepo) {
        this.hsnRepo = hsnRepo;
        this.unitRepo = unitRepo;
        this.stateRepo = stateRepo;
        this.rateRepo = rateRepo;
        this.logRepo = logRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!loadOnStartup) {
            log.info("GST master data load skipped (gst.master-data.load-on-startup=false)");
            return;
        }
        try {
            loadAll();
        } catch (Exception e) {
            // Never prevent the application from starting over reference data.
            log.error("GST master data load failed: {}", e.getMessage(), e);
        }
    }

    /** Loads every master file. Safe to call repeatedly. */
    @Transactional
    public Map<String, Integer> loadAll() {
        Map<String, Integer> summary = new HashMap<>();
        summary.put("hsn", loadHsn());
        summary.put("unit", loadUnits());
        summary.put("state", loadStates());
        summary.put("gstRate", loadGstRates());
        return summary;
    }

    /**
     * Seeds gst_rate_master from the CBIC Ready Reckoner extract.
     *
     * A heading with exactly one published rate is loaded VERIFIED - it is the government's
     * own stated rate and there is nothing to decide. A heading carrying several rates
     * (rice 5% vs nil by packaging, apparel 5% vs 18% by price) is loaded UNVERIFIED,
     * because which one applies depends on the goods rather than the code, and the resolver
     * must not pick one arbitrarily.
     *
     * Only inserts where the (hsn, rate, effectiveFrom) row is absent, so an admin's later
     * edits and verifications are never overwritten by a restart.
     */
    private int loadGstRates() {
        List<JsonNode> rows = readArray("master-data/gst_rate_seed.json");
        if (rows.isEmpty()) return 0;

        Map<String, List<GstRateMaster>> existing = new HashMap<>();
        rateRepo.findAll().forEach(r ->
                existing.computeIfAbsent(r.getHsnCode(), k -> new ArrayList<>()).add(r));

        List<GstRateMaster> toSave = new ArrayList<>();
        int inserted = 0, ambiguous = 0;
        for (JsonNode n : rows) {
            String hsn = text(n, "hsnCode");
            JsonNode rateNode = n.get("gstRate");
            String fromStr = text(n, "effectiveFrom");
            if (hsn == null || hsn.isBlank() || rateNode == null || fromStr == null) continue;

            double rate = rateNode.asDouble();
            LocalDate from = LocalDate.parse(fromStr);
            boolean isAmbiguous = n.hasNonNull("ambiguous") && n.get("ambiguous").asBoolean();
            if (isAmbiguous) ambiguous++;

            boolean present = existing.getOrDefault(hsn, List.of()).stream()
                    .anyMatch(r -> Double.compare(r.getGstRate(), rate) == 0
                            && from.equals(r.getEffectiveFrom()));
            if (present) continue;

            GstRateMaster r = new GstRateMaster();
            r.setHsnCode(hsn);
            r.setGstRate(rate);
            r.setEffectiveFrom(from);
            r.setStatus(isAmbiguous ? GstRateMaster.STATUS_UNVERIFIED : GstRateMaster.STATUS_VERIFIED);
            r.setSource(text(n, "source"));
            r.setNotes(text(n, "notes"));
            if (!isAmbiguous) {
                r.setVerifiedBy("CBIC master data import");
                r.setVerifiedAt(LocalDateTime.now());
            }
            toSave.add(r);
            inserted++;
        }
        if (!toSave.isEmpty()) rateRepo.saveAll(toSave);

        if (inserted > 0) {
            MasterUpdateLog entry = new MasterUpdateLog();
            entry.setFileName("gst_rate_seed.json");
            entry.setVersion(RATE_SOURCE_VERSION);
            entry.setRowsLoaded(rows.size());
            entry.setRowsInserted(inserted);
            entry.setRowsUpdated(0);
            entry.setStatus("SUCCESS");
            entry.setChangesDetected(inserted + " rate rows inserted; " + ambiguous
                    + " left UNVERIFIED because the heading carries more than one rate");
            logRepo.save(entry);
        }
        log.info("GST rates: {} rows in file, {} inserted ({} ambiguous held for review)",
                rows.size(), inserted, ambiguous);
        return rows.size();
    }

    private int loadHsn() {
        List<JsonNode> rows = readArray("master-data/hsn_master.json");
        if (rows.isEmpty()) return 0;

        Map<String, HsnMaster> existing = new HashMap<>();
        hsnRepo.findAll().forEach(h -> existing.put(h.getHsnCode(), h));

        List<HsnMaster> toSave = new ArrayList<>();
        int inserted = 0, updated = 0;
        for (JsonNode n : rows) {
            String code = text(n, "code");
            String desc = text(n, "description");
            if (code == null || code.isBlank() || desc == null || desc.isBlank()) continue;

            HsnMaster cur = existing.get(code);
            if (cur == null) {
                toSave.add(new HsnMaster(code, desc));
                inserted++;
            } else if (!desc.equals(cur.getDescription())) {
                cur.setDescription(desc);
                toSave.add(cur);
                updated++;
            }
        }
        if (!toSave.isEmpty()) hsnRepo.saveAll(toSave);
        recordLog("hsn_master.json", rows.size(), inserted, updated);
        return rows.size();
    }

    private int loadUnits() {
        List<JsonNode> rows = readArray("master-data/unit_master.json");
        if (rows.isEmpty()) return 0;

        Map<String, UnitMaster> existing = new HashMap<>();
        unitRepo.findAll().forEach(u -> existing.put(u.getUnitCode(), u));

        List<UnitMaster> toSave = new ArrayList<>();
        int inserted = 0, updated = 0;
        for (JsonNode n : rows) {
            String code = text(n, "code");
            String desc = text(n, "description");
            if (code == null || code.isBlank()) continue;

            UnitMaster cur = existing.get(code);
            if (cur == null) {
                toSave.add(new UnitMaster(code, desc));
                inserted++;
            } else if (desc != null && !desc.equals(cur.getUnitDescription())) {
                cur.setUnitDescription(desc);
                toSave.add(cur);
                updated++;
            }
        }
        if (!toSave.isEmpty()) unitRepo.saveAll(toSave);
        recordLog("unit_master.json", rows.size(), inserted, updated);
        return rows.size();
    }

    private int loadStates() {
        List<JsonNode> rows = readArray("master-data/state_master.json");
        if (rows.isEmpty()) return 0;

        Map<String, StateMaster> existing = new HashMap<>();
        stateRepo.findAll().forEach(s -> existing.put(s.getStateCode(), s));

        List<StateMaster> toSave = new ArrayList<>();
        int inserted = 0, updated = 0;
        for (JsonNode n : rows) {
            String code = text(n, "code");
            String name = text(n, "name");
            if (code == null || code.isBlank()) continue;
            // State codes are two digits; the source drops the leading zero on 1-9.
            if (code.length() == 1) code = "0" + code;

            StateMaster cur = existing.get(code);
            if (cur == null) {
                toSave.add(new StateMaster(code, name));
                inserted++;
            } else if (name != null && !name.equals(cur.getStateName())) {
                cur.setStateName(name);
                toSave.add(cur);
                updated++;
            }
        }
        if (!toSave.isEmpty()) stateRepo.saveAll(toSave);
        recordLog("state_master.json", rows.size(), inserted, updated);
        return rows.size();
    }

    private void recordLog(String fileName, int loaded, int inserted, int updated) {
        if (inserted == 0 && updated == 0) {
            log.info("GST master {}: {} rows, already up to date", fileName, loaded);
            return;
        }
        MasterUpdateLog entry = new MasterUpdateLog();
        entry.setFileName(fileName);
        entry.setVersion(MASTER_VERSION);
        entry.setRowsLoaded(loaded);
        entry.setRowsInserted(inserted);
        entry.setRowsUpdated(updated);
        entry.setStatus("SUCCESS");
        entry.setChangesDetected(inserted + " inserted, " + updated + " updated");
        logRepo.save(entry);
        log.info("GST master {}: {} rows ({} inserted, {} updated)", fileName, loaded, inserted, updated);
    }

    private List<JsonNode> readArray(String path) {
        List<JsonNode> out = new ArrayList<>();
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            if (root != null && root.isArray()) root.forEach(out::add);
        } catch (Exception e) {
            log.error("Could not read master data file {}: {}", path, e.getMessage());
        }
        return out;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
