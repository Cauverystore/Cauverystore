package com.cauverystore.service;

import com.cauverystore.entities.CountryMaster;
import com.cauverystore.repository.CountryMasterRepository;
import com.cauverystore.entities.CurrencyMaster;
import com.cauverystore.repository.CurrencyMasterRepository;
import com.cauverystore.entities.PortMaster;
import com.cauverystore.repository.PortMasterRepository;
import com.cauverystore.entities.PincodeStateRange;
import com.cauverystore.repository.PincodeStateRangeRepository;
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
 * The classification codes (HSN, units, states) come from the GSTN portal, which does not
 * publish rates. Rates come from a separate seed extracted from the CBIC notifications and
 * are loaded here too, but only ever as proposals plus the subset the seed can justify
 * approving outright - see loadGstRates and demoteStaleAutoVerified.
 */
@Service
public class GstMasterDataLoader {

    private static final Logger log = LoggerFactory.getLogger(GstMasterDataLoader.class);

    /** Bump when the committed master files are refreshed from the portal. */
    private static final String MASTER_VERSION = "1.0 (portal last-updated 2026-06-24)";

    /** Marks a verification the loader performed itself, so it can withdraw its own later. */
    private static final String AUTO_VERIFIER = "CBIC master data import";

    /** Bump when the rate seed is regenerated from a newer CBIC notification. */
    private static final String RATE_SOURCE_VERSION =
            "CBIC Notif. 09/2025-CT(Rate) as amended by 19/2025-CT(Rate) (eff. 01-02-2026) and 01/2026-CT(Rate) (eff. 01-05-2026)";

    private final HsnMasterRepository hsnRepo;
    private final UnitMasterRepository unitRepo;
    private final StateMasterRepository stateRepo;
    private final GstRateMasterRepository rateRepo;
    private final MasterUpdateLogRepository logRepo;
    private final CountryMasterRepository countryRepo;
    private final CurrencyMasterRepository currencyRepo;
    private final PortMasterRepository portRepo;
    private final PincodeStateRangeRepository pincodeRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gst.master-data.load-on-startup:true}")
    private boolean loadOnStartup;

    public GstMasterDataLoader(HsnMasterRepository hsnRepo,
                               UnitMasterRepository unitRepo,
                               StateMasterRepository stateRepo,
                               GstRateMasterRepository rateRepo,
                               MasterUpdateLogRepository logRepo,
                               CountryMasterRepository countryRepo,
                               CurrencyMasterRepository currencyRepo,
                               PortMasterRepository portRepo,
                               PincodeStateRangeRepository pincodeRepo) {
        this.hsnRepo = hsnRepo;
        this.unitRepo = unitRepo;
        this.stateRepo = stateRepo;
        this.rateRepo = rateRepo;
        this.logRepo = logRepo;
        this.countryRepo = countryRepo;
        this.currencyRepo = currencyRepo;
        this.portRepo = portRepo;
        this.pincodeRepo = pincodeRepo;
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
        summary.put("country", loadCountries());
        summary.put("currency", loadCurrencies());
        summary.put("port", loadPorts());
        summary.put("pincodeRange", loadPincodeRanges());
        summary.put("gstRate", loadGstRates());
        return summary;
    }

    /**
     * Seeds gst_rate_master from the CBIC Ready Reckoner extract.
     *
     * A heading is loaded VERIFIED only when the rate can be settled without reading the goods
     * description: either it publishes a single rate, or it publishes a complete pair of value
     * bands (apparel is 5% up to Rs 2500 per piece and 18% above, which the resolver decides
     * from the selling price). Anything else - rice at 5% pre-packaged versus nil loose, or
     * footwear where only the lower band is published - is loaded UNVERIFIED, because the
     * resolver must not pick one arbitrarily.
     *
     * Inserts are keyed on (hsn, rate, effectiveFrom, condition), so an admin's later edits
     * and verifications are never overwritten by a restart.
     */
    private int loadGstRates() {
        List<JsonNode> rows = readArray("master-data/gst_rate_seed.json");
        if (rows.isEmpty()) return 0;

        Map<String, List<GstRateMaster>> existing = new HashMap<>();
        rateRepo.findAll().forEach(r ->
                existing.computeIfAbsent(r.getHsnCode(), k -> new ArrayList<>()).add(r));

        // Every (hsn, rate, effectiveFrom, condition) the current seed is willing to stand behind.
        java.util.Set<String> seedVerified = new java.util.HashSet<>();
        java.util.Set<String> seedHeadings = new java.util.HashSet<>();
        for (JsonNode n : rows) {
            String h = text(n, "hsnCode");
            if (h == null || n.get("gstRate") == null || text(n, "effectiveFrom") == null) continue;
            seedHeadings.add(h);
            if (GstRateMaster.STATUS_VERIFIED.equals(text(n, "status"))) {
                seedVerified.add(rateKey(h, n.get("gstRate").asDouble(),
                        text(n, "effectiveFrom"), text(n, "conditionType")));
            }
        }

        List<GstRateMaster> toSave = new ArrayList<>();
        int inserted = 0, ambiguous = 0;
        for (JsonNode n : rows) {
            String hsn = text(n, "hsnCode");
            JsonNode rateNode = n.get("gstRate");
            String fromStr = text(n, "effectiveFrom");
            if (hsn == null || hsn.isBlank() || rateNode == null || fromStr == null) continue;

            double rate = rateNode.asDouble();
            LocalDate from = LocalDate.parse(fromStr);
            // The seed decides status: a heading the resolver can settle on its own (a single
            // unconditional rate, or a complete value-banded pair) arrives VERIFIED; anything
            // needing a human to read the goods description arrives UNVERIFIED.
            boolean isAmbiguous = !GstRateMaster.STATUS_VERIFIED.equals(text(n, "status"));
            if (isAmbiguous) ambiguous++;

            String seedCondition = text(n, "conditionType");
            boolean present = existing.getOrDefault(hsn, List.of()).stream()
                    .anyMatch(r -> Double.compare(r.getGstRate(), rate) == 0
                            && from.equals(r.getEffectiveFrom())
                            && java.util.Objects.equals(
                                    r.getConditionType() == null ? GstRateMaster.CONDITION_NONE : r.getConditionType(),
                                    seedCondition == null ? GstRateMaster.CONDITION_NONE : seedCondition));
            if (present) continue;

            GstRateMaster r = new GstRateMaster();
            r.setHsnCode(hsn);
            r.setGstRate(rate);
            r.setEffectiveFrom(from);
            String toStr = text(n, "effectiveTo");
            if (toStr != null && !toStr.isBlank()) r.setEffectiveTo(LocalDate.parse(toStr));
            r.setStatus(isAmbiguous ? GstRateMaster.STATUS_UNVERIFIED : GstRateMaster.STATUS_VERIFIED);
            r.setSource(text(n, "source"));
            r.setNotes(text(n, "notes"));
            String ctype = text(n, "conditionType");
            r.setConditionType(ctype != null ? ctype : GstRateMaster.CONDITION_NONE);
            if (n.hasNonNull("thresholdAmount")) r.setThresholdAmount(n.get("thresholdAmount").asDouble());
            r.setThresholdUnit(text(n, "thresholdUnit"));
            r.setConditionText(text(n, "conditionText"));
            if (!isAmbiguous) {
                r.setVerifiedBy(AUTO_VERIFIER);
                r.setVerifiedAt(LocalDateTime.now());
            }
            toSave.add(r);
            inserted++;
        }
        if (!toSave.isEmpty()) rateRepo.saveAll(toSave);

        int closed = applySeedEndDates(rows, existing);
        int demoted = demoteStaleAutoVerified(existing, seedVerified, seedHeadings);

        if (inserted > 0 || demoted > 0 || closed > 0) {
            MasterUpdateLog entry = new MasterUpdateLog();
            entry.setFileName("gst_rate_seed.json");
            entry.setVersion(RATE_SOURCE_VERSION);
            entry.setRowsLoaded(rows.size());
            entry.setRowsInserted(inserted);
            entry.setRowsUpdated(demoted);
            entry.setStatus("SUCCESS");
            entry.setChangesDetected(inserted + " rate rows inserted; " + ambiguous
                    + " left UNVERIFIED because the heading carries more than one rate; "
                    + demoted + " previously auto-verified rows demoted for review; "
                    + closed + " rows closed off on the date a later notification ended them");
            logRepo.save(entry);
        }
        log.info("GST rates: {} rows in file, {} inserted ({} ambiguous held for review), "
                        + "{} demoted, {} closed off", rows.size(), inserted, ambiguous, demoted, closed);
        return rows.size();
    }

    /**
     * Closes off rows the seed now says stopped applying on a date.
     *
     * A rate is never deleted when a later notification ends it, because an invoice raised
     * while it was in force still has to reprint at it. Instead the row gets an end date and
     * the resolver stops offering it from the following day - which is also what keeps two
     * rates for one heading from both looking current and leaving the resolver unable to
     * choose.
     *
     * Only ever fills in an end date that is currently absent. If an admin has already closed
     * a row off by hand, their date stands: the seed is a starting position, not an authority
     * over someone who has looked at the notification themselves.
     */
    private int applySeedEndDates(List<JsonNode> rows, Map<String, List<GstRateMaster>> existing) {
        Map<String, LocalDate> endDates = new HashMap<>();
        for (JsonNode n : rows) {
            String to = text(n, "effectiveTo");
            if (to == null || to.isBlank()) continue;
            String hsn = text(n, "hsnCode");
            if (hsn == null || n.get("gstRate") == null || text(n, "effectiveFrom") == null) continue;
            endDates.put(rateKey(hsn, n.get("gstRate").asDouble(),
                    text(n, "effectiveFrom"), text(n, "conditionType")), LocalDate.parse(to));
        }
        if (endDates.isEmpty()) return 0;

        List<GstRateMaster> toClose = new ArrayList<>();
        for (List<GstRateMaster> perHsn : existing.values()) {
            for (GstRateMaster r : perHsn) {
                if (r.getEffectiveTo() != null) continue;
                LocalDate end = endDates.get(rateKey(r.getHsnCode(), r.getGstRate(),
                        r.getEffectiveFrom() == null ? null : r.getEffectiveFrom().toString(),
                        r.getConditionType()));
                if (end == null) continue;
                r.setEffectiveTo(end);
                r.setNotes((r.getNotes() == null || r.getNotes().isBlank() ? "" : r.getNotes() + " | ")
                        + "Closed off on " + end + " by a later notification.");
                toClose.add(r);
            }
        }
        if (!toClose.isEmpty()) {
            rateRepo.saveAll(toClose);
            log.info("Closed off {} GST rates that a later notification ended.", toClose.size());
        }
        return toClose.size();
    }

    /**
     * Withdraws automatic verification from rows an earlier, less careful seed had approved.
     *
     * The first seed marked a heading VERIFIED whenever it carried a single published rate.
     * That was wrong for value-banded goods: footwear (ch. 64) publishes only the "not
     * exceeding Rs 2500 per pair" band, so a lone 5% row was auto-approved and a Rs 4,999
     * pair would have been charged 5% instead of 18%. Fixing the seed alone does not help an
     * already-populated database, because the load is insert-only - the stale approval would
     * sit there forever, charging the wrong rate.
     *
     * Only rows this loader itself approved are touched. A rate a human verified is never
     * overridden: their sign-off outranks the generator's guess.
     */
    private int demoteStaleAutoVerified(Map<String, List<GstRateMaster>> existing,
                                        java.util.Set<String> seedVerified,
                                        java.util.Set<String> seedHeadings) {
        List<GstRateMaster> demote = new ArrayList<>();
        for (List<GstRateMaster> perHsn : existing.values()) {
            for (GstRateMaster r : perHsn) {
                if (!r.isVerified() || !AUTO_VERIFIER.equals(r.getVerifiedBy())) continue;
                if (!seedHeadings.contains(r.getHsnCode())) continue;
                String key = rateKey(r.getHsnCode(), r.getGstRate(),
                        r.getEffectiveFrom() == null ? null : r.getEffectiveFrom().toString(),
                        r.getConditionType());
                if (seedVerified.contains(key)) continue;

                r.setStatus(GstRateMaster.STATUS_UNVERIFIED);
                r.setVerifiedBy(null);
                r.setVerifiedAt(null);
                r.setNotes((r.getNotes() == null || r.getNotes().isBlank() ? "" : r.getNotes() + " | ")
                        + "Auto-verification withdrawn on " + LocalDate.now()
                        + ": the current CBIC extract no longer supports approving this rate "
                        + "without review (the heading's rate may depend on sale value or goods description).");
                demote.add(r);
            }
        }
        if (!demote.isEmpty()) {
            rateRepo.saveAll(demote);
            log.warn("Demoted {} auto-verified GST rates to UNVERIFIED - they need review before "
                    + "they will be charged again.", demote.size());
        }
        return demote.size();
    }

    private static String rateKey(String hsn, double rate, String from, String conditionType) {
        String cond = (conditionType == null || conditionType.isBlank())
                ? GstRateMaster.CONDITION_NONE : conditionType;
        return hsn + "|" + rate + "|" + from + "|" + cond;
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

    /**
     * Code lists the IRP validates an e-invoice against - countries, currencies and ports.
     *
     * Loaded the same way as the rest: insert what is missing, update a changed description,
     * leave everything else alone, so a restart does not churn the tables. Only needed once
     * exports begin, but a rejected e-invoice at that point is a blocked shipment.
     */
    private int loadCountries() {
        List<JsonNode> rows = readArray("master-data/country_master.json");
        if (rows.isEmpty()) return 0;

        Map<String, CountryMaster> existing = new HashMap<>();
        countryRepo.findAll().forEach(c -> existing.put(c.getCountryCode(), c));

        List<CountryMaster> toSave = new ArrayList<>();
        int inserted = 0, updated = 0;
        for (JsonNode n : rows) {
            String code = text(n, "code");
            String name = text(n, "name");
            if (code == null || code.isBlank()) continue;
            CountryMaster cur = existing.get(code);
            if (cur == null) {
                toSave.add(new CountryMaster(code, name));
                inserted++;
            } else if (name != null && !name.equals(cur.getCountryName())) {
                cur.setCountryName(name);
                toSave.add(cur);
                updated++;
            }
        }
        if (!toSave.isEmpty()) countryRepo.saveAll(toSave);
        recordLog("country_master.json", rows.size(), inserted, updated);
        return rows.size();
    }

    private int loadCurrencies() {
        List<JsonNode> rows = readArray("master-data/currency_master.json");
        if (rows.isEmpty()) return 0;

        Map<String, CurrencyMaster> existing = new HashMap<>();
        currencyRepo.findAll().forEach(c -> existing.put(c.getCurrencyCode(), c));

        List<CurrencyMaster> toSave = new ArrayList<>();
        int inserted = 0, updated = 0;
        for (JsonNode n : rows) {
            String code = text(n, "code");
            String name = text(n, "name");
            if (code == null || code.isBlank()) continue;
            CurrencyMaster cur = existing.get(code);
            if (cur == null) {
                toSave.add(new CurrencyMaster(code, name));
                inserted++;
            } else if (name != null && !name.equals(cur.getCurrencyName())) {
                cur.setCurrencyName(name);
                toSave.add(cur);
                updated++;
            }
        }
        if (!toSave.isEmpty()) currencyRepo.saveAll(toSave);
        recordLog("currency_master.json", rows.size(), inserted, updated);
        return rows.size();
    }

    private int loadPorts() {
        List<JsonNode> rows = readArray("master-data/port_master.json");
        if (rows.isEmpty()) return 0;

        Map<String, PortMaster> existing = new HashMap<>();
        portRepo.findAll().forEach(p -> existing.put(p.getPortCode(), p));

        List<PortMaster> toSave = new ArrayList<>();
        int inserted = 0, updated = 0;
        for (JsonNode n : rows) {
            String code = text(n, "code");
            String name = text(n, "name");
            if (code == null || code.isBlank()) continue;
            PortMaster cur = existing.get(code);
            if (cur == null) {
                toSave.add(new PortMaster(code, name));
                inserted++;
            } else if (name != null && !name.equals(cur.getPortName())) {
                cur.setPortName(name);
                toSave.add(cur);
                updated++;
            }
        }
        if (!toSave.isEmpty()) portRepo.saveAll(toSave);
        recordLog("port_master.json", rows.size(), inserted, updated);
        return rows.size();
    }

    /**
     * Pincode prefix ranges, parsed from the portal's "781 - 788" wording into two numbers so
     * they can be compared rather than string-matched.
     *
     * Replaced wholesale rather than merged. There are only 42 rows, they have no natural key -
     * a state legitimately appears several times - and matching them up to decide what changed
     * would be more code than simply reloading the table.
     */
    private int loadPincodeRanges() {
        List<JsonNode> rows = readArray("master-data/pincode_state_map.json");
        if (rows.isEmpty()) return 0;

        List<PincodeStateRange> parsed = new ArrayList<>();
        for (JsonNode n : rows) {
            String stateCode = text(n, "stateCode");
            String stateName = text(n, "stateName");
            String range = text(n, "pincodeRange");
            if (stateCode == null || range == null) continue;

            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d{3})\\s*-\\s*(\\d{3})").matcher(range);
            if (!m.find()) {
                log.warn("Could not read the pincode range '{}' for state {} - skipped.",
                        range, stateCode);
                continue;
            }
            parsed.add(new PincodeStateRange(stateCode, stateName,
                    Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        }
        if (parsed.isEmpty()) return 0;

        long before = pincodeRepo.count();
        if (before == parsed.size()) return rows.size();   // already loaded; leave it alone

        pincodeRepo.deleteAll();
        pincodeRepo.saveAll(parsed);
        recordLog("pincode_state_map.json", rows.size(), parsed.size(), 0);
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
