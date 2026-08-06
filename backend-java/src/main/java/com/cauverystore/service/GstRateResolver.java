package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.GstRateMasterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Single source of truth for "what GST applies to this product".
 *
 * Everything that charges or declares tax must go through here, so the rate a customer is
 * charged at checkout and the rate printed on the invoice can never diverge - previously
 * checkout hardcoded 12% while invoices read product.gstPercentage, so any product not set
 * to exactly 12% would have been billed at one rate and invoiced at another.
 *
 * Resolution order for a product:
 *   1. Verified rate for its exact HSN, in force on the supply date
 *   2. Verified rate for the parent heading (8-digit -> 6-digit -> 4-digit)
 *   3. Unresolved
 *
 * On "unresolved" the caller decides: strict mode refuses the sale, lenient mode falls back
 * to the legacy rate and logs loudly. It never silently guesses a plausible rate, because a
 * wrong rate looks exactly like a right one until an audit.
 */
@Service
public class GstRateResolver {

    private static final Logger log = LoggerFactory.getLogger(GstRateResolver.class);

    private final GstRateMasterRepository rateRepo;

    /**
     * When true, a product without a resolvable verified rate cannot be sold or invoiced.
     * Defaults to false so that enabling this system cannot take an existing store offline;
     * turn it on once gst_rate_master has been populated and signed off.
     */
    @Value("${gst.strict-mode:false}")
    private boolean strictMode;

    /** Rate applied when nothing resolves and strict mode is off. Matches historic behaviour. */
    @Value("${gst.fallback-rate:12.0}")
    private double fallbackRate;

    public GstRateResolver(GstRateMasterRepository rateRepo) {
        this.rateRepo = rateRepo;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    /** The GST split that applies to one supply. */
    public static class Resolved {
        private final double totalRate;
        private final double cgstRate;
        private final double sgstRate;
        private final double igstRate;
        private final String hsnCode;
        private final boolean fromMaster;

        Resolved(double totalRate, boolean interState, String hsnCode, boolean fromMaster) {
            this.totalRate = totalRate;
            this.hsnCode = hsnCode;
            this.fromMaster = fromMaster;
            if (interState) {
                this.igstRate = totalRate;
                this.cgstRate = 0.0;
                this.sgstRate = 0.0;
            } else {
                this.igstRate = 0.0;
                this.cgstRate = totalRate / 2.0;
                this.sgstRate = totalRate / 2.0;
            }
        }

        public double getTotalRate() { return totalRate; }
        public double getCgstRate() { return cgstRate; }
        public double getSgstRate() { return sgstRate; }
        public double getIgstRate() { return igstRate; }
        public String getHsnCode() { return hsnCode; }
        /** False when this came from the fallback rather than a verified master row. */
        public boolean isFromMaster() { return fromMaster; }

        public double taxOn(double taxableValue) {
            return round(taxableValue * totalRate / 100.0);
        }

        public double cgstOn(double taxableValue) { return round(taxableValue * cgstRate / 100.0); }
        public double sgstOn(double taxableValue) { return round(taxableValue * sgstRate / 100.0); }
        public double igstOn(double taxableValue) { return round(taxableValue * igstRate / 100.0); }

        /**
         * Total tax as the sum of the components actually printed on the invoice.
         *
         * Use this rather than taxOn() wherever the total has to foot against the CGST/SGST
         * lines: rounding each half to paise separately can differ from rounding the whole
         * by a paisa (399.75 @ 5% gives 9.99 + 9.99 = 19.98, but 19.99 rounded as one).
         */
        public double totalTaxOn(double taxableValue) {
            return round(cgstOn(taxableValue) + sgstOn(taxableValue) + igstOn(taxableValue));
        }

        private static double round(double v) {
            return Math.round(v * 100.0) / 100.0;
        }
    }

    /**
     * Resolve the rate for a product on a given supply date.
     * Empty means no verified rate could be found.
     */
    public Optional<Double> findRate(String hsnCode, LocalDate onDate) {
        return findRate(hsnCode, onDate, null);
    }

    /**
     * @param unitPrice per-piece selling price, needed only for headings whose rate depends on
     *                  value (apparel, footwear). Null is fine for everything else.
     */
    public Optional<Double> findRate(String hsnCode, LocalDate onDate, Double unitPrice) {
        if (hsnCode == null || hsnCode.isBlank()) return Optional.empty();
        String hsn = hsnCode.replaceAll("\\s", "");
        LocalDate date = onDate != null ? onDate : LocalDate.now();

        // Exact code, then progressively broader headings (8 -> 6 -> 4 -> 2). Chapter-level
        // matters here: apparel rates are published against "61", not against 6109.
        for (String candidate : List.of(hsn,
                hsn.length() > 6 ? hsn.substring(0, 6) : hsn,
                hsn.length() > 4 ? hsn.substring(0, 4) : hsn,
                hsn.length() > 2 ? hsn.substring(0, 2) : hsn)) {
            List<GstRateMaster> hits =
                    rateRepo.findApplicable(candidate, GstRateMaster.STATUS_VERIFIED, date);
            if (hits.isEmpty()) continue;

            // A row whose value condition matches the item's price wins outright - that is the
            // whole point of the condition. Only if none match do we consider unconditional rows.
            List<GstRateMaster> conditionMatches = hits.stream()
                    .filter(GstRateMaster::isConditional)
                    .filter(r -> r.appliesToUnitPrice(unitPrice))
                    .toList();
            if (conditionMatches.size() == 1) {
                return Optional.of(conditionMatches.get(0).getGstRate());
            }

            List<GstRateMaster> unconditional = hits.stream()
                    .filter(r -> !r.isConditional())
                    .toList();
            if (unconditional.size() == 1) {
                return Optional.of(unconditional.get(0).getGstRate());
            }
            if (unconditional.size() > 1) {
                // Several unconditional rates on one heading: the goods description decides,
                // which we cannot evaluate. Refuse rather than pick the first.
                log.warn("HSN {} has {} unconditional verified rates on {} - cannot choose without "
                        + "the goods description; leaving unresolved.", candidate, unconditional.size(), date);
                return Optional.empty();
            }
            // Conditional rows exist but none matched this price (e.g. footwear over the
            // threshold where only the lower band is published). Do not fall through to a
            // broader heading and silently pick an unrelated rate.
            if (!hits.isEmpty()) {
                log.warn("HSN {} has value-conditional rates but none apply to unit price {} - "
                        + "leaving unresolved.", candidate, unitPrice);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve the full split for a product.
     *
     * @param interState true when supplier and place of supply are in different states (IGST)
     * @throws GstRateUnresolvedException in strict mode when no verified rate exists
     */
    public Resolved resolve(Product product, boolean interState, LocalDate onDate) {
        return resolve(product, interState, onDate, null);
    }

    /**
     * @param unitPrice the per-piece price actually charged, which decides the rate for
     *                  value-banded headings such as apparel and footwear
     */
    public Resolved resolve(Product product, boolean interState, LocalDate onDate, Double unitPrice) {
        String hsn = product != null ? product.getHsnCode() : null;
        Optional<Double> rate = findRate(hsn, onDate, unitPrice);

        if (rate.isPresent()) {
            return new Resolved(rate.get(), interState, hsn, true);
        }

        String productName = product != null ? product.getName() : "unknown";
        if (strictMode) {
            throw new GstRateUnresolvedException(
                    "No verified GST rate for product '" + productName + "'"
                            + (hsn == null || hsn.isBlank() ? " (no HSN code set)" : " (HSN " + hsn + ")")
                            + ". Set the HSN and a verified rate in the GST master before selling it.");
        }

        log.warn("GST rate unresolved for product '{}' (HSN {}) - falling back to {}%. "
                        + "Populate gst_rate_master and enable gst.strict-mode to stop guessing.",
                productName, hsn, fallbackRate);
        return new Resolved(fallbackRate, interState, hsn, false);
    }

    /** Thrown in strict mode when a supply cannot be taxed correctly. */
    public static class GstRateUnresolvedException extends RuntimeException {
        public GstRateUnresolvedException(String message) {
            super(message);
        }
    }
}
