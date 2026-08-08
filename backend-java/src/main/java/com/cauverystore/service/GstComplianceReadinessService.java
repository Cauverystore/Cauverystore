package com.cauverystore.service;

import com.cauverystore.entities.GstConfiguration;
import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.repository.GstConfigurationRepository;
import com.cauverystore.repository.GstRateMasterRepository;
import com.cauverystore.repository.GstInvoiceRepository;
import com.cauverystore.repository.HsnMasterRepository;
import com.cauverystore.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers one question: can this store charge the correct tax on everything it sells?
 *
 * The rules are not optional: there is no fallback rate, and an unclassifiable product cannot
 * be published. That is the correct posture - but it means any live product the system cannot
 * tax will refuse to sell, and a seller deserves to know which ones before a customer meets
 * the error.
 *
 * So this names every live product that cannot be taxed correctly, says why, and says what to
 * do about it. Run it before deploying, work the list, and nothing is caught out.
 *
 * It also finds invoices already issued at the fallback rate, because those exist regardless of
 * what happens next and someone has to know which they are.
 */
@Service
public class GstComplianceReadinessService {

    private final ProductRepository productRepo;
    private final HsnMasterRepository hsnRepo;
    private final GstRateResolver rateResolver;
    private final GstInvoiceRepository invoiceRepo;
    private final GstConfigurationRepository configRepo;
    private final GstRateFreshnessService freshnessService;
    private final GstRateMasterRepository rateRepo;

    public GstComplianceReadinessService(ProductRepository productRepo,
                                         HsnMasterRepository hsnRepo,
                                         GstRateResolver rateResolver,
                                         GstInvoiceRepository invoiceRepo,
                                         GstConfigurationRepository configRepo,
                                         GstRateFreshnessService freshnessService,
                                         GstRateMasterRepository rateRepo) {
        this.productRepo = productRepo;
        this.hsnRepo = hsnRepo;
        this.rateResolver = rateResolver;
        this.invoiceRepo = invoiceRepo;
        this.configRepo = configRepo;
        this.freshnessService = freshnessService;
        this.rateRepo = rateRepo;
    }

    /** Why one product cannot be taxed correctly, and what fixes it. */
    public static class Blocker {
        public Long productId;
        public String productName;
        public String hsnCode;
        public String problem;
        public String fix;

        Blocker(Product p, String problem, String fix) {
            this.productId = p.getId();
            this.productName = p.getName();
            this.hsnCode = p.getHsnCode();
            this.problem = problem;
            this.fix = fix;
        }
    }

    /**
     * Every live product that cannot be taxed correctly today.
     *
     * Only products actually on sale. A draft that is not classified is not a compliance
     * problem - nothing is being sold at the wrong rate - and including them would bury the
     * real list under work nobody needs to do yet.
     */
    public List<Blocker> blockingProducts() {
        List<Blocker> blockers = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Product p : productRepo.findAll()) {
            if (!p.isActive() || !"published".equalsIgnoreCase(String.valueOf(p.getProductStatus()))) {
                continue;
            }
            String hsn = p.getHsnCode();

            if (hsn == null || hsn.isBlank()) {
                blockers.add(new Blocker(p,
                        "No HSN code, so there is no lawful rate to charge.",
                        "Open the product and pick the code that describes the goods."));
                continue;
            }
            if (!hsnRepo.existsById(hsn.replaceAll("\\s", ""))) {
                blockers.add(new Blocker(p,
                        "'" + hsn + "' is not a code in the official GSTN master, so it can never "
                                + "match a published rate.",
                        "Search for the goods by name and pick a real code."));
                continue;
            }
            if (p.getGstRateSelectionId() != null) {
                continue;   // the seller has answered which published line applies
            }

            Double unitPrice = p.getOfferPrice() != null ? p.getOfferPrice() : p.getPrice();
            if (rateResolver.findRate(hsn, today, unitPrice, p.getPrePackagedAndLabelled()).isPresent()) {
                continue;
            }

            if (p.getPrePackagedAndLabelled() == null) {
                blockers.add(new Blocker(p,
                        "HSN " + hsn + " is taxed differently packaged and loose, and this product "
                                + "does not say which it is.",
                        "Tick or untick 'sold pre-packaged and labelled' on the product."));
            } else {
                blockers.add(new Blocker(p,
                        "HSN " + hsn + " carries more than one published rate and nothing on this "
                                + "product says which describes it.",
                        "Open the product and choose the wording that matches the goods."));
            }
        }
        return blockers;
    }

    /**
     * Which unreviewed headings are actually holding the shop up, busiest first.
     *
     * The rate master has 357 rates awaiting review across 175 headings, and read as a list of
     * work that is daunting enough to not get started. Most of it is irrelevant: a heading only
     * matters if something on sale resolves through it. This turns the queue into the handful
     * of headings that would unblock the most products, so the review can be done in an hour
     * rather than abandoned as a project.
     *
     * Each entry also carries the alternative. A seller can clear their own product by choosing
     * which published wording describes it, which works immediately and without waiting for
     * anyone - so a heading with one blocked product is usually a message to that seller, not a
     * job for whoever reviews rates.
     */
    public List<Map<String, Object>> reviewPriority() {
        Map<String, List<Blocker>> byHeading = new LinkedHashMap<>();
        for (Blocker b : blockingProducts()) {
            if (b.hsnCode == null || b.hsnCode.isBlank()) continue;
            byHeading.computeIfAbsent(b.hsnCode.replaceAll("\\s", ""), k -> new ArrayList<>()).add(b);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, List<Blocker>> e : byHeading.entrySet()) {
            String code = e.getKey();
            List<Blocker> blocked = e.getValue();

            // The rates competing on this code, at whichever level the resolver would stop.
            List<GstRateMaster> competing = new ArrayList<>();
            for (String candidate : List.of(code,
                    code.length() > 6 ? code.substring(0, 6) : code,
                    code.length() > 4 ? code.substring(0, 4) : code,
                    code.length() > 2 ? code.substring(0, 2) : code)) {
                List<GstRateMaster> rows = rateRepo.findByHsnCodeOrderByEffectiveFromDesc(candidate);
                if (!rows.isEmpty()) { competing.addAll(rows); break; }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("hsnCode", code);
            row.put("productsBlocked", blocked.size());
            row.put("products", blocked.stream().limit(5).map(b -> b.productName).toList());
            row.put("choices", competing.stream().map(r -> Map.of(
                    "rateId", r.getId() == null ? "" : r.getId(),
                    "gstRate", r.getGstRate(),
                    "status", r.getStatus(),
                    "wording", r.getConditionText() == null ? "" : r.getConditionText())).toList());
            row.put("fixedByReview", "Approve the correct rate for HSN " + code
                    + " on the rate review desk - clears every product under it at once.");
            row.put("fixedBySeller", "Or the seller opens each product and picks the wording that "
                    + "describes their goods, which works without waiting for a review.");
            out.add(row);
        }

        // Busiest first: the point is to show where an hour of review buys the most.
        out.sort((a, b) -> Integer.compare((Integer) b.get("productsBlocked"),
                (Integer) a.get("productsBlocked")));
        return out;
    }

    /** Invoices already issued at the fallback rate - a rate that is not lawful for anything. */
    public List<Map<String, Object>> invoicesTaxedByFallback() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GstInvoice inv : invoiceRepo.findAll()) {
            if (!inv.usedFallbackRate()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("invoiceId", inv.getId());
            row.put("invoiceNumber", inv.getInvoiceNumber());
            row.put("invoiceDate", inv.getInvoiceDate());
            row.put("sellerGstin", inv.getSellerGstin());
            row.put("totalTax", inv.getTotalTax());
            out.add(row);
        }
        return out;
    }

    /**
     * Whether the store can currently charge correct tax on everything it sells.
     *
     * Deliberately reports the marketplace's own gaps too. A store that taxes every product
     * correctly but cannot file GSTR-8 under the right registration is not compliant; listing
     * only the products would say it was.
     */
    public Map<String, Object> readiness() {
        List<Blocker> blockers = blockingProducts();
        List<Map<String, Object>> badInvoices = invoicesTaxedByFallback();
        List<String> marketplaceGaps = marketplaceGaps();

        Map<String, Object> out = new LinkedHashMap<>();
        boolean ready = blockers.isEmpty() && marketplaceGaps.isEmpty();
        out.put("ready", ready);
        out.put("summary", ready
                ? "Every live product resolves to a published rate and the marketplace's own "
                  + "registration is complete."
                : "Not compliant. " + blockers.size() + " live product(s) cannot be taxed correctly"
                  + (marketplaceGaps.isEmpty() ? "" : " and the marketplace registration is incomplete")
                  + ". Those products will refuse to sell until each is classified.");
        out.put("blockingProductCount", blockers.size());
        out.put("blockingProducts", blockers);
        out.put("marketplaceGaps", marketplaceGaps);
        // Rate freshness is reported alongside, because a store whose products all resolve is
        // still not compliant if the rates they resolve to were superseded months ago.
        out.put("rateFreshness", freshnessService.status());
        out.put("pendingRateNotifications", freshnessService.pendingNotifications());
        out.put("invoicesTaxedByFallbackCount", badInvoices.size());
        out.put("invoicesTaxedByFallback", badInvoices);
        return out;
    }

    private List<String> marketplaceGaps() {
        List<String> gaps = new ArrayList<>();
        GstConfiguration config = configRepo.findAll().stream().findFirst().orElse(null);
        if (config == null) {
            gaps.add("No marketplace GST configuration exists at all.");
            return gaps;
        }
        if (isBlank(config.getGstin())) {
            gaps.add("The marketplace has no GSTIN.");
        }
        if (config.resolveTcsGstin().isEmpty()) {
            gaps.add("No separate s.52 TCS registration. GSTR-8 filed under the regular GSTIN "
                    + "is rejected, so TCS cannot be filed at all.");
        }
        if (isBlank(config.getCin())) {
            gaps.add("No CIN recorded for the marketplace entity.");
        }
        if (isBlank(config.getNodalAccountNumber())) {
            gaps.add("No nodal escrow account. Money collected for sellers is held rather than "
                    + "owned, and settlement cannot be reconciled without it.");
        }
        return gaps;
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
