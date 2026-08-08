package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.MasterUpdateLog;
import com.cauverystore.repository.GstRateMasterRepository;
import com.cauverystore.repository.HsnMasterRepository;
import com.cauverystore.repository.MasterUpdateLogRepository;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports GST rates from a spreadsheet a human has prepared from a CBIC notification.
 *
 * <h2>Why an upload rather than a feed</h2>
 *
 * The obvious design is a job that pulls rates from the GST portal on a schedule. CBIC's
 * notification API requires credentials, and the public site is a single-page app with no
 * server-rendered listing, so no such job can be written honestly. What is sometimes done
 * instead - scraping a tax blog, or shipping a "simulated" feed for now - is worse than having
 * nothing: it produces an authoritative-looking rate with no authority behind it, and the first
 * anyone would know is an assessment.
 *
 * So the file is the boundary. Somebody reads the notification, transcribes it, and their name
 * goes on the import. That is a slower loop than a feed and a truthful one, and it is the same
 * loop a chartered accountant already runs.
 *
 * <h2>Why nothing imported is trusted</h2>
 *
 * Every row lands UNVERIFIED. A spreadsheet is a transcription, and transcriptions carry typos
 * - a rate one column left, a heading missing a digit, 5 read as 0.5. None of them are charged
 * to anyone until a second person approves them on the rate review desk, so a slip costs a
 * review rather than an invoice.
 *
 * A rate that already exists is never silently overwritten either. Rates are effective-dated:
 * the old row is closed off the day before the new one starts and both are kept, because an
 * invoice raised last month has to keep resolving to the rate that applied last month.
 */
@Service
public class GstRateImportService {

    private static final Logger log = LoggerFactory.getLogger(GstRateImportService.class);

    /** Accepted headers, lower-cased and stripped, mapped to what they mean. */
    private static final Map<String, String> HEADERS = Map.of(
            "hsn", "hsn", "hsncode", "hsn", "code", "hsn",
            "rate", "rate", "gstrate", "rate",
            "effectivefrom", "from", "effectivedate", "from",
            "description", "description", "goods", "description");

    private final GstRateMasterRepository rateRepo;
    private final HsnMasterRepository hsnRepo;
    private final MasterUpdateLogRepository logRepo;

    public GstRateImportService(GstRateMasterRepository rateRepo,
                                HsnMasterRepository hsnRepo,
                                MasterUpdateLogRepository logRepo) {
        this.rateRepo = rateRepo;
        this.hsnRepo = hsnRepo;
        this.logRepo = logRepo;
    }

    public static class ImportException extends RuntimeException {
        public ImportException(String message) { super(message); }
    }

    /**
     * Reads a rate spreadsheet and stages every row for review.
     *
     * @param notification which notification these rates come from. Required: a rate whose
     *                     source cannot be named is one nobody can check, and the whole point
     *                     of this table is that every rate traces to a published document.
     * @param importedBy   who transcribed it.
     */
    @Transactional
    public Map<String, Object> importRates(MultipartFile file, String notification,
                                           String importedBy) {
        if (file == null || file.isEmpty()) {
            throw new ImportException("No file was uploaded.");
        }
        if (notification == null || notification.isBlank()) {
            throw new ImportException(
                    "Name the notification these rates come from - for example "
                            + "\"09/2025-Central Tax (Rate)\". A rate with no source cannot be "
                            + "checked by anyone reviewing it later.");
        }
        if (importedBy == null || importedBy.isBlank()) {
            throw new ImportException("An import has to be attributed to someone.");
        }

        List<Map<String, Object>> rejected = new ArrayList<>();
        List<GstRateMaster> staged = new ArrayList<>();
        int rowCount = 0;

        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) throw new ImportException("The first sheet is empty.");

            Map<String, Integer> columns = mapColumns(header);
            if (!columns.containsKey("hsn") || !columns.containsKey("rate")) {
                throw new ImportException(
                        "The sheet needs at least an HSN column and a rate column. Found: "
                                + columns.keySet() + ". Accepted headings are HSN / HSN Code / "
                                + "Code, Rate / GST Rate, Effective From, Description.");
            }

            for (int i = header.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String hsn = text(row, columns.get("hsn"));
                if (hsn == null || hsn.isBlank()) continue;   // trailing blank rows
                rowCount++;

                hsn = hsn.replaceAll("\\s", "");
                Double rate = number(row, columns.get("rate"));
                if (rate == null) {
                    rejected.add(reject(i + 1, hsn, "No rate could be read from the rate column."));
                    continue;
                }
                // A GST rate is a percentage. A cell formatted as a percentage comes through as
                // 0.05, which as a rate would undercharge by a hundredfold.
                if (rate > 0 && rate < 1) rate = rate * 100;
                if (rate < 0 || rate > 100) {
                    rejected.add(reject(i + 1, hsn, "A rate of " + rate + "% is not a GST rate."));
                    continue;
                }
                if (!hsnRepo.existsById(hsn)) {
                    // Chapter-level codes are legitimate and are not in the HSN master, which
                    // only holds 4, 6 and 8 digits - so only those lengths are checked.
                    if (hsn.length() > 2) {
                        rejected.add(reject(i + 1, hsn,
                                "Not a code in the official GSTN master, so no product could carry it."));
                        continue;
                    }
                }

                LocalDate from = columns.containsKey("from") ? date(row, columns.get("from")) : null;
                if (from == null) {
                    rejected.add(reject(i + 1, hsn,
                            "No effective date. A rate with no start date cannot be applied to "
                                    + "one invoice without being applied to every past one."));
                    continue;
                }

                GstRateMaster staged_ = new GstRateMaster();
                staged_.setHsnCode(hsn);
                staged_.setGstRate(rate);
                staged_.setEffectiveFrom(from);
                staged_.setConditionText(columns.containsKey("description")
                        ? text(row, columns.get("description")) : null);
                staged_.setSource("Imported from " + file.getOriginalFilename()
                        + " as " + notification + ", by " + importedBy);
                staged_.setNotes("Imported on " + LocalDate.now() + " from a spreadsheet. Held "
                        + "UNVERIFIED until someone checks it against the notification itself.");
                // Never verified on import. A transcription is not evidence.
                staged_.setStatus(GstRateMaster.STATUS_UNVERIFIED);
                staged.add(staged_);
            }
        } catch (ImportException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportException("The file could not be read as a spreadsheet: " + e.getMessage());
        }

        // Close off what each new rate replaces, rather than overwriting it - last month's
        // invoices still have to resolve to last month's rate.
        int superseded = 0;
        for (GstRateMaster incoming : staged) {
            for (GstRateMaster current : rateRepo.findByHsnCodeOrderByEffectiveFromDesc(incoming.getHsnCode())) {
                if (current.getEffectiveTo() != null) continue;
                if (current.getEffectiveFrom() == null
                        || !current.getEffectiveFrom().isBefore(incoming.getEffectiveFrom())) continue;
                current.setEffectiveTo(incoming.getEffectiveFrom().minusDays(1));
                current.setNotes((current.getNotes() == null ? "" : current.getNotes() + " | ")
                        + "Closed off on import of " + notification + ", which sets a new rate "
                        + "for this heading from " + incoming.getEffectiveFrom() + ".");
                rateRepo.save(current);
                superseded++;
            }
        }
        if (!staged.isEmpty()) rateRepo.saveAll(staged);

        MasterUpdateLog entry = new MasterUpdateLog();
        entry.setFileName(file.getOriginalFilename());
        entry.setVersion(notification);
        entry.setRowsLoaded(rowCount);
        entry.setRowsInserted(staged.size());
        entry.setRowsUpdated(superseded);
        entry.setStatus(rejected.isEmpty() ? "SUCCESS" : "PARTIAL");
        entry.setChangesDetected(staged.size() + " rates staged for review; " + superseded
                + " existing rates closed off; " + rejected.size() + " rows rejected. Imported by "
                + importedBy + ".");
        logRepo.save(entry);

        log.info("GST rate import from {} by {}: {} staged, {} superseded, {} rejected",
                file.getOriginalFilename(), importedBy, staged.size(), superseded, rejected.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", file.getOriginalFilename());
        result.put("notification", notification);
        result.put("importedBy", importedBy);
        result.put("rowsRead", rowCount);
        result.put("staged", staged.size());
        result.put("supersededExisting", superseded);
        result.put("rejected", rejected);
        result.put("message", staged.size() + " rate(s) imported and held for review. None of "
                + "them will be charged to a customer until they are approved on the rate review "
                + "desk."
                + (rejected.isEmpty() ? "" : " " + rejected.size() + " row(s) were rejected and "
                    + "are listed with the reason - fix them in the sheet and import again."));
        return result;
    }

    private Map<String, Integer> mapColumns(Row header) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            String raw = text(header, c);
            if (raw == null) continue;
            String key = raw.toLowerCase().replaceAll("[^a-z]", "");
            String meaning = HEADERS.get(key);
            if (meaning != null) columns.putIfAbsent(meaning, c);
        }
        return columns;
    }

    private Map<String, Object> reject(int rowNumber, String hsn, String why) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("row", rowNumber);
        row.put("hsnCode", hsn);
        row.put("reason", why);
        return row;
    }

    private String text(Row row, Integer column) {
        if (column == null) return null;
        Cell cell = row.getCell(column);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            // A code read as a number loses its leading zero, and 0207 is not 207.
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : new java.math.BigDecimal(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private Double number(Row row, Integer column) {
        if (column == null) return null;
        Cell cell = row.getCell(column);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        try {
            return Double.parseDouble(cell.getStringCellValue().replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate date(Row row, Integer column) {
        if (column == null) return null;
        Cell cell = row.getCell(column);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            String raw = cell.getStringCellValue().trim();
            if (raw.isEmpty()) return null;
            // dd-MM-yyyy is how CBIC writes dates; ISO is how a spreadsheet exports them.
            if (raw.matches("\\d{2}[-/]\\d{2}[-/]\\d{4}")) {
                String[] p = raw.split("[-/]");
                return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
            }
            return LocalDate.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /** Past imports, newest first - the audit trail for where every rate came from. */
    public List<MasterUpdateLog> history() {
        return logRepo.findAll().stream()
                .sorted((a, b) -> {
                    LocalDateTime x = a.getCreatedAt(), y = b.getCreatedAt();
                    if (x == null || y == null) return 0;
                    return y.compareTo(x);
                })
                .toList();
    }
}
