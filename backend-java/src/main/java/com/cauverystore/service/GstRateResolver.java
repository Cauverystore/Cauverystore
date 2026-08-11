package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.GstRateMasterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * There is deliberately no fallback rate. A rate that cannot be determined is not charged at
 * all - the supply is refused - because every figure on a tax invoice has to be one CBIC
 * published for those goods. The old behaviour, charging a default when nothing resolved, put
 * an unlawful rate on real invoices and left no trace that anything had gone wrong; a wrong
 * rate looks exactly like a right one until an audit.
 */
@Service
public class GstRateResolver {

    private static final Logger log = LoggerFactory.getLogger(GstRateResolver.class);

    private final GstRateMasterRepository rateRepo;

    public GstRateResolver(GstRateMasterRepository rateRepo) {
        this.rateRepo = rateRepo;
    }

    /** The GST split that applies to one supply. */
    public static class Resolved {
        private final double totalRate;
        private final double cessRate;
        private final double cgstRate;
        private final double sgstRate;
        private final double igstRate;
        private final String hsnCode;
        private final boolean fromMaster;

        Resolved(double totalRate, double cessRate, boolean interState, String hsnCode, boolean fromMaster) {
            this.totalRate = totalRate;
            this.cessRate = cessRate;
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
        /**
         * Compensation cess, percent of taxable value, from the same master row that gave the
         * GST rate. Zero for goods the CBIC has not put cess on. Always the published rate:
         * like the GST rate it is never guessed, so the seller's own cessRate entry cannot make
         * a charge appear where the master says none exists.
         */
        public double getCessRate() { return cessRate; }
        public double getCgstRate() { return cgstRate; }
        public double getSgstRate() { return sgstRate; }
        public double getIgstRate() { return igstRate; }
        public String getHsnCode() { return hsnCode; }
        /**
         * Always true now that there is no fallback - every resolved rate is a published one.
         * Kept because invoices raised before the fallback was removed recorded it, and those
         * still have to be findable.
         */
        public boolean isFromMaster() { return fromMaster; }

        public double taxOn(double taxableValue) {
            return round(taxableValue * totalRate / 100.0);
        }

        public double cgstOn(double taxableValue) { return round(taxableValue * cgstRate / 100.0); }
        public double sgstOn(double taxableValue) { return round(taxableValue * sgstRate / 100.0); }
        public double igstOn(double taxableValue) { return round(taxableValue * igstRate / 100.0); }
        public double cessOn(double taxableValue) { return round(taxableValue * cessRate / 100.0); }

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

    public Optional<Double> findRate(String hsnCode, LocalDate onDate, Double unitPrice) {
        return findRate(hsnCode, onDate, unitPrice, null);
    }

    /**
     * @param unitPrice  per-piece selling price, needed only for headings whose rate depends on
     *                   value (apparel, footwear). Null is fine for everything else.
     * @param prePackaged whether the goods are pre-packaged and labelled, needed only for the
     *                    staples whose rate turns on packaging (rice, meat, dairy, cereals).
     */
    public Optional<Double> findRate(String hsnCode, LocalDate onDate,
                                     Double unitPrice, Boolean prePackaged) {
        return findRateRow(hsnCode, onDate, unitPrice, prePackaged).map(GstRateMaster::getGstRate);
    }

    /**
     * The verified master row that prices these goods on the given date, if any.
     *
     * Shared by findRate and resolve: the row that decides the GST rate also carries the cess
     * rate, and the two must come from the same row or a conditional heading could split its
     * cess from its GST. A caller that only needs the rate uses findRate; one that charges or
     * prints the line needs this row so it can carry both.
     */
    Optional<GstRateMaster> findRateRow(String hsnCode, LocalDate onDate,
                                        Double unitPrice, Boolean prePackaged) {
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

            // The last step of the walk is the whole chapter, and most chapter-level entries
            // name particular goods rather than pricing everything under them - chapter 71's
            // is "rupee notes or coins sold to the Reserve Bank" at nil, chapter 39's is
            // "paper sacks and bio-degradable bags" at 5%. Letting those stand as the
            // chapter's rate charged nil on jewellery and 5% on plastics owing 18%. Only an
            // entry that genuinely covers its chapter may answer for goods it does not name.
            if (candidate.length() == 2) {
                hits = hits.stream().filter(GstRateMaster::coversWholeChapter).toList();
            }
            if (hits.isEmpty()) continue;

            // A row whose value condition matches the item's price wins outright - that is the
            // whole point of the condition. Only if none match do we consider unconditional rows.
            List<GstRateMaster> conditionMatches = hits.stream()
                    .filter(GstRateMaster::isConditional)
                    .filter(r -> r.appliesTo(unitPrice, prePackaged))
                    .toList();
            if (conditionMatches.size() == 1) {
                return Optional.of(conditionMatches.get(0));
            }

            List<GstRateMaster> unconditional = hits.stream()
                    .filter(r -> !r.isConditional())
                    .toList();
            if (unconditional.size() == 1) {
                return Optional.of(unconditional.get(0));
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
                log.warn("HSN {} has conditional rates but none apply to unit price {} / "
                        + "pre-packaged {} - leaving unresolved.", candidate, unitPrice, prePackaged);
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
        // Packaging comes off the product itself - unlike price, no caller has to pass it.
        Boolean prePackaged = product != null ? product.getPrePackagedAndLabelled() : null;

        // A seller who has picked the published line describing their goods has answered the
        // question the rate table cannot: 0901 is 5% roasted and nil green, and only they know
        // which they sell. Their answer wins over the automatic walk below.
        Optional<GstRateMaster> chosen = selectedRateRow(product, onDate);
        if (chosen.isPresent()) {
            GstRateMaster row = chosen.get();
            return new Resolved(row.getGstRate(), nzCess(row.getCessRate()), interState, hsn, true);
        }

        Optional<GstRateMaster> rowOpt = findRateRow(hsn, onDate, unitPrice, prePackaged);

        if (rowOpt.isPresent()) {
            GstRateMaster row = rowOpt.get();
            return new Resolved(row.getGstRate(), nzCess(row.getCessRate()), interState, hsn, true);
        }

        // There is no fallback. Every rate charged has to be one CBIC published for these
        // goods, so when none can be determined the supply does not proceed. Charging a
        // plausible-looking rate instead would put an unlawful figure on a tax invoice and
        // leave no trace that anything was wrong.
        String productName = product != null ? product.getName() : "unknown";
        throw new GstRateUnresolvedException(
                "No published GST rate could be determined for '" + productName + "'"
                        + (hsn == null || hsn.isBlank()
                            ? " because it has no HSN code."
                            : " under HSN " + hsn + ".")
                        + " It cannot be sold or invoiced until that is settled - open the product "
                        + "and choose the code, or the published wording, that describes the goods.");
    }

    /**
     * The rate row the seller picked, if it still stands up.
     *
     * Deliberately accepts an UNVERIFIED row. Unverified means "ambiguous for this heading in
     * general", not "wrong" - it is exactly the state of a line that needs a human, and the
     * seller picking it is that human. What is still checked is that the row belongs to this
     * product's code and was in force on the supply date, so a selection cannot outlive the
     * notification that published it or survive the product being re-coded.
     */
    private Optional<GstRateMaster> selectedRateRow(Product product, LocalDate onDate) {
        if (product == null || product.getGstRateSelectionId() == null) return Optional.empty();
        LocalDate date = onDate != null ? onDate : LocalDate.now();

        Optional<GstRateMaster> row = rateRepo.findById(product.getGstRateSelectionId());
        if (row.isEmpty()) {
            log.warn("Product '{}' points at GST rate {} which no longer exists - falling back "
                    + "to automatic resolution.", product.getName(), product.getGstRateSelectionId());
            return Optional.empty();
        }
        GstRateMaster rate = row.get();

        String hsn = product.getHsnCode() == null ? "" : product.getHsnCode().replaceAll("\s", "");
        if (!hsn.startsWith(rate.getHsnCode())) {
            log.warn("Product '{}' (HSN {}) points at a rate published against HSN {} - the code "
                            + "must have changed since it was chosen. Ignoring the selection.",
                    product.getName(), hsn, rate.getHsnCode());
            return Optional.empty();
        }
        boolean inForce = !rate.getEffectiveFrom().isAfter(date)
                && (rate.getEffectiveTo() == null || !rate.getEffectiveTo().isBefore(date));
        if (!inForce) {
            log.warn("Product '{}' points at a GST rate that was not in force on {} - a later "
                    + "notification has moved on. Re-classify it.", product.getName(), date);
            return Optional.empty();
        }
        return Optional.of(rate);
    }

    private static double nzCess(Double cess) {
        return cess == null ? 0.0 : cess;
    }

    /** Thrown in strict mode when a supply cannot be taxed correctly. */
    public static class GstRateUnresolvedException extends RuntimeException {
        public GstRateUnresolvedException(String message) {
            super(message);
        }
    }
}
