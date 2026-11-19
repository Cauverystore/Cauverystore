package com.cauverystore.service;

import com.cauverystore.entities.DocumentSeries;
import com.cauverystore.entities.GstConfiguration;
import com.cauverystore.repository.DocumentSeriesRepository;
import com.cauverystore.repository.GstConfigurationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Function;

/**
 * Gives out GST document numbers, one series per seller GSTIN (see {@link DocumentSeries}).
 *
 * Prefixes:
 * - Invoices use the seller's own invoice prefix when they set one. A seller on the default
 *   ("CS", or none) gets "CS" plus a short code from their GST settings record, for example
 *   "CS1A", so no two sellers share a series.
 * - Credit notes use "CN" plus the same code, debit notes "DN" plus the code.
 * A prefix already used by another GSTIN is refused.
 */
@Service
public class DocumentNumberService {
    public static final String INVOICE = "INV";
    public static final String CREDIT_NOTE = "CN";
    public static final String DEBIT_NOTE = "DN";
    static final String DEFAULT_INVOICE_PREFIX = "CS";
    public static final int MAX_PREFIX = 6;

    private final DocumentSeriesRepository seriesRepo;
    private final GstConfigurationRepository configRepo;
    private final TransactionTemplate newTx;

    public DocumentNumberService(DocumentSeriesRepository seriesRepo, GstConfigurationRepository configRepo,
                                 PlatformTransactionManager txManager) {
        this.seriesRepo = seriesRepo;
        this.configRepo = configRepo;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * The next number for this seller and document type. Call it inside the transaction that
     * saves the document, so the series stays locked until that document is saved.
     *
     * @param existingMax looks up the highest number already issued that starts with a given
     *                    "prefix + yyMM + /" (documents raised before this counter existed), so the
     *                    first number in a series carries on from them instead of clashing.
     */
    @Transactional
    public String next(String gstin, String docType, Function<String, String> existingMax) {
        if (gstin == null || gstin.isBlank()) {
            throw new IllegalStateException("A " + label(docType) + " number needs the seller's GSTIN.");
        }
        String g = gstin.trim().toUpperCase(Locale.ROOT);
        String prefix = prefixFor(g, docType);
        String period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"));
        String pattern = prefix + period + "/";

        DocumentSeries series = seriesRepo.lockSeries(docType, prefix, period).orElse(null);
        if (series == null) {
            createSeries(g, docType, prefix, period, startFrom(existingMax, pattern));
            series = seriesRepo.lockSeries(docType, prefix, period).orElseThrow(
                    () -> new IllegalStateException("Could not open the numbering series " + pattern));
        }
        if (!series.getGstin().equals(g)) {
            throw new IllegalStateException("The numbering series " + pattern + " belongs to another GSTIN.");
        }
        int n = series.getLastNumber() + 1;
        if (n > 99_999) {
            throw new IllegalStateException("The " + label(docType) + " series " + pattern
                    + " has reached 99999 this month. Set a different invoice prefix.");
        }
        series.setLastNumber(n);
        seriesRepo.save(series);
        return pattern + String.format("%05d", n);
    }

    /** The prefix this GSTIN uses for this document type. */
    public String prefixFor(String gstin, String docType) {
        GstConfiguration config = configRepo.findByGstin(gstin).orElse(null);
        String code = sellerCode(gstin, config);
        String prefix;
        if (INVOICE.equals(docType)) {
            String custom = config != null ? clean(config.getInvoicePrefix()) : "";
            prefix = custom.isEmpty() || custom.equals(DEFAULT_INVOICE_PREFIX)
                    ? DEFAULT_INVOICE_PREFIX + code : custom;
        } else {
            prefix = docType + code;
        }
        // 16 characters in all (Rule 46): prefix (6) + yyMM (4) + "/" (1) + 5 digits.
        if (prefix.length() > MAX_PREFIX) prefix = prefix.substring(0, MAX_PREFIX);
        String p = prefix;
        seriesRepo.findFirstByDocTypeAndPrefix(docType, p).ifPresent(s -> {
            if (!s.getGstin().equals(gstin)) {
                throw new IllegalStateException("The " + label(docType) + " prefix " + p
                        + " is already used by another seller. Choose a different invoice prefix in GST settings.");
            }
        });
        return prefix;
    }

    /** Short code for the seller: the GST settings record's id in base 36, or a code from the GSTIN. */
    static String sellerCode(String gstin, GstConfiguration config) {
        if (config != null && config.getId() != null) {
            return Long.toString(config.getId(), 36).toUpperCase(Locale.ROOT);
        }
        // No settings record: derive 3 characters from the whole GSTIN (a clash is refused by
        // the prefix check, not silently shared).
        long h = Integer.toUnsignedLong(gstin.hashCode()) % 46_656L; // 36^3
        String s = Long.toString(h, 36).toUpperCase(Locale.ROOT);
        return "X" + "0".repeat(Math.max(0, 3 - s.length())) + s;
    }

    static String clean(String prefix) {
        return prefix == null ? "" : prefix.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static int startFrom(Function<String, String> existingMax, String pattern) {
        if (existingMax == null) return 0;
        String max = existingMax.apply(pattern);
        if (max == null) return 0;
        try {
            return Integer.parseInt(max.substring(max.lastIndexOf('/') + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Opens the series in its own short transaction, so a race with another order is harmless. */
    private void createSeries(String gstin, String docType, String prefix, String period, int start) {
        try {
            newTx.executeWithoutResult(s -> seriesRepo.saveAndFlush(
                    new DocumentSeries(gstin, docType, prefix, period, start)));
        } catch (DataIntegrityViolationException alreadyThere) {
            // Another order opened it a moment ago: use that one.
        }
    }

    private static String label(String docType) {
        return switch (docType) {
            case CREDIT_NOTE -> "credit note";
            case DEBIT_NOTE -> "debit note";
            default -> "invoice";
        };
    }
}
