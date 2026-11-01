package com.cauverystore.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

/**
 * Rates set by law rather than by HSN - today, the s.52 TCS rate - read with their effective
 * dates from master-data/statutory_rates.json.
 *
 * Looked up by the date of the supply, so a change in law applies from its effective date by
 * itself and an invoice dated before it keeps the old rate. There is no default: a date the
 * file does not cover is an error, because guessing a statutory rate is how the 1% fallback
 * came to withhold double from sellers.
 */
public final class StatutoryRates {

    public static final String TCS_S52 = "TCS_S52";
    /** A rupee limit rather than a rate, read the same way. */
    public static final String EWB_CONSIGNMENT_THRESHOLD = "EWB_CONSIGNMENT_THRESHOLD";
    /** Turnover at or above which an e-invoice must reach the IRP within 30 days of its date. */
    public static final String EINVOICE_30_DAY_LIMIT_AATO = "EINVOICE_30_DAY_LIMIT_AATO";

    public record Period(LocalDate from, LocalDate to, double rate, String source) {
        boolean covers(LocalDate d) {
            return !d.isBefore(from) && (to == null || !d.isAfter(to));
        }
    }

    private static final Map<String, List<Period>> RATES = load("master-data/statutory_rates.json");

    private StatutoryRates() { }

    static Map<String, List<Period>> load(String resource) {
        Map<String, List<Period>> out = new HashMap<>();
        try (InputStream in = StatutoryRates.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException(resource + " is missing");
            JsonNode rates = new ObjectMapper().readTree(in).path("rates");
            rates.fields().forEachRemaining(entry -> {
                List<Period> periods = new ArrayList<>();
                for (JsonNode p : entry.getValue()) {
                    periods.add(new Period(
                            LocalDate.parse(p.path("from").asText()),
                            p.hasNonNull("to") ? LocalDate.parse(p.path("to").asText()) : null,
                            p.has("rate") ? p.path("rate").asDouble() : p.path("value").asDouble(),
                            p.path("source").asText(null)));
                }
                out.put(entry.getKey(), List.copyOf(periods));
            });
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read " + resource, e);
        }
        return Map.copyOf(out);
    }

    /** The period in force on {@code date}, or empty if the file does not cover it. */
    public static Optional<Period> periodOn(String code, LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        return RATES.getOrDefault(code, List.of()).stream().filter(p -> p.covers(d)).findFirst();
    }

    public static double rateOn(String code, LocalDate date) {
        return periodOn(code, date).map(Period::rate).orElseThrow(() -> new IllegalStateException(
                "No " + code + " rate for " + date + " in master-data/statutory_rates.json"));
    }

    /** Total s.52 TCS rate in percent (CGST+SGST, or IGST) on the date of supply. */
    public static double tcsRateOn(LocalDate date) {
        return rateOn(TCS_S52, date);
    }
}
