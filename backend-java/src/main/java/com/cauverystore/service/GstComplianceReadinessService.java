package com.cauverystore.service;

import com.cauverystore.client.GstnClient;
import com.cauverystore.entities.GstConfiguration;
import com.cauverystore.util.GstComplianceUtil;
import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.repository.GstConfigurationRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
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
    private final SellerRegistrationRepository sellerRegRepo;
    private final GstnClient gstnClient;

    public GstComplianceReadinessService(ProductRepository productRepo,
                                         HsnMasterRepository hsnRepo,
                                         GstRateResolver rateResolver,
                                         GstInvoiceRepository invoiceRepo,
                                         GstConfigurationRepository configRepo,
                                         GstRateFreshnessService freshnessService,
                                         GstRateMasterRepository rateRepo,
                                         SellerRegistrationRepository sellerRegRepo,
                                         GstnClient gstnClient) {
        this.productRepo = productRepo;
        this.hsnRepo = hsnRepo;
        this.rateResolver = rateResolver;
        this.invoiceRepo = invoiceRepo;
        this.configRepo = configRepo;
        this.freshnessService = freshnessService;
        this.rateRepo = rateRepo;
        this.sellerRegRepo = sellerRegRepo;
        this.gstnClient = gstnClient;
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
        // The same subject said as a checklist rather than a list of complaints, so a reader can
        // see what has been confirmed and not only what is missing.
        out.put("marketplaceIdentity", marketplaceIdentity());
        out.put("sellerGaps", sellerGaps());
        // Rate freshness is reported alongside, because a store whose products all resolve is
        // still not compliant if the rates they resolve to were superseded months ago.
        out.put("rateFreshness", freshnessService.status());
        out.put("pendingRateNotifications", freshnessService.pendingNotifications());
        out.put("invoicesTaxedByFallbackCount", badInvoices.size());
        out.put("invoicesTaxedByFallback", badInvoices);
        out.put("irp", irpStatus());
        return out;
    }

    /**
     * Whether the e-invoice portal is really registering invoices, and what stands between the
     * operator and it.
     *
     * The store runs on the simulator unless someone has both selected the live client and
     * given it every credential - a live client that is selected but half-configured refuses
     * registration rather than guessing, which this surfaces as MISCONFIGURED so the refusal is
     * not mistaken for a quiet breakage.
     */
    private Map<String, Object> irpStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean simulated = gstnClient.isSimulated();
        boolean configured = gstnClient.isConfigured();
        status.put("mode", simulated ? "SIMULATED" : "LIVE");
        status.put("configured", configured);
        status.put("active", !simulated && configured);
        if (simulated) {
            status.put("status", "INACTIVE");
            status.put("message", "The e-invoice portal is not in use. Every invoice gets a locally "
                    + "drawn IRN and QR that the government portal has never seen - correct for "
                    + "development, and fine below the e-invoicing threshold. To switch on, set "
                    + "gstn.simulated=false and add the six gstn.* credentials after a run against "
                    + "the NIC sandbox.");
        } else if (!configured) {
            status.put("status", "MISCONFIGURED");
            status.put("message", "The live client is selected (gstn.simulated=false) but not fully "
                    + "configured. Invoice registration is refused, never simulated. Supply the six "
                    + "gstn.* credentials (client-id, client-secret, username, password, gstin, "
                    + "public-key) to activate.");
        } else {
            status.put("status", "ACTIVE");
            status.put("message", "Invoices are registered with the live IRP and carry a genuine IRN.");
        }
        return status;
    }

    /**
     * One line per thing the marketplace's own registration needs, said as present or not.
     *
     * marketplaceGaps below returns prose, and prose only lists what is wrong - which reads the
     * same whether five things were checked and passed or nothing was checked at all. Before
     * filing anything, the useful question is not "any problems?" but "what has actually been
     * confirmed?", so this reports every field either way.
     *
     * It also checks what is there rather than only that something is there. A GSTIN nobody could
     * hold, a state code that disagrees with the GSTIN it sits beside, or a PAN that is not the
     * one embedded in the GSTIN are all fields that are populated and wrong - and a screen that
     * counts them as done is worse than one that leaves them blank.
     */
    public Map<String, Object> marketplaceIdentity() {
        GstConfiguration config = configRepo.findAll().stream().findFirst().orElse(null);
        List<Map<String, Object>> items = new ArrayList<>();

        if (config == null) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("configured", false);
            out.put("summary", "No marketplace GST configuration exists. Nothing can be invoiced "
                    + "or filed until the marketplace's own registration is recorded.");
            out.put("items", items);
            out.put("readyToFile", false);
            return out;
        }

        String gstin = config.getGstin();
        check(items, "gstin", "Marketplace GSTIN", gstin,
                "Section 24(x) requires an e-commerce operator collecting TCS to register, "
                        + "whatever its turnover. Every invoice names it as the supplier.");
        if (!isBlank(gstin) && !GstComplianceUtil.isGstinChecksumValid(gstin)) {
            fail(items, "gstin", "Marketplace GSTIN", mask(gstin),
                    "This GSTIN fails its own check digit, so it is not a number anybody was "
                            + "issued. Every invoice raised carries it as the supplier.",
                    "Re-enter it from the registration certificate.");
        }

        String tcsGstin = config.resolveTcsGstin().orElse(null);
        check(items, "tcsGstin", "TCS registration (s.52)", tcsGstin,
                "GSTR-8 is filed under the TCS registration. Filed under the regular GSTIN it "
                        + "is rejected, so TCS cannot be filed at all.");
        if (tcsGstin != null && !GstComplianceUtil.isGstinChecksumValid(tcsGstin)) {
            fail(items, "tcsGstin", "TCS registration (s.52)", mask(tcsGstin),
                    "Fails its check digit - GSTR-8 would be filed under a number that does not "
                            + "exist.", "Re-enter it from the TCS registration certificate.");
        }

        check(items, "legalName", "Registered legal name", config.getLegalName(),
                "Rule 46 requires the supplier's name on every tax invoice.");
        check(items, "address", "Registered address", config.getAddress(),
                "Rule 46 requires the supplier's address on every tax invoice.");
        check(items, "pan", "PAN", config.getPan(), "Printed on the marketplace's own invoices.");
        check(items, "cin", "CIN", config.getCin(),
                "The MCA corporate identity number, printed on the marketplace's invoices.");
        check(items, "nodalAccountNumber", "Nodal escrow account", config.getNodalAccountNumber(),
                "Money collected for sellers is held, not owned. Settlement cannot be reconciled "
                        + "without a separate account for it.");

        // Cross-checks: fields that are individually filled in and cannot all be true at once.
        if (!isBlank(gstin) && !isBlank(config.getStateCode())
                && !gstin.trim().toUpperCase().startsWith(config.getStateCode().trim())) {
            fail(items, "stateCode", "State code", config.getStateCode(),
                    "The state code does not match the first two characters of the GSTIN, and the "
                            + "GSTIN is the one that decides whether a supply is inter-state.",
                    "Set it to " + gstin.trim().substring(0, 2) + ", or correct the GSTIN.");
        }
        if (!isBlank(gstin) && !isBlank(config.getPan())
                && gstin.trim().length() == 15
                && !GstComplianceUtil.panMatchesGstin(config.getPan(), gstin)) {
            fail(items, "pan", "PAN", config.getPan(),
                    "A GSTIN carries its holder's PAN at characters 3 to 12, and this PAN is not "
                            + "the one inside the GSTIN. One of the two belongs to somebody else.",
                    "Check both against the registration certificate.");
        }

        long missing = items.stream().filter(i -> !"OK".equals(i.get("status"))).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configured", true);
        out.put("items", items);
        out.put("outstanding", missing);
        // Separate from "compliant". This says the marketplace can identify itself on an invoice
        // and file a return - not that its catalogue is correctly classified, which is what the
        // rest of the readiness screen is about.
        out.put("readyToFile", missing == 0);
        out.put("summary", missing == 0
                ? "The marketplace's own registration is complete."
                : missing + " item(s) outstanding on the marketplace's own registration. Invoices "
                        + "and returns depend on these, not on the catalogue.");
        return out;
    }

    private void check(List<Map<String, Object>> items, String field, String label,
                       String value, String why) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("field", field);
        item.put("label", label);
        item.put("status", isBlank(value) ? "MISSING" : "OK");
        item.put("value", isBlank(value) ? null : mask(value));
        item.put("why", why);
        items.add(item);
    }

    /** Replaces an earlier OK for the same field - a populated wrong value is not done. */
    private void fail(List<Map<String, Object>> items, String field, String label,
                      String value, String why, String fix) {
        items.removeIf(i -> field.equals(i.get("field")));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("field", field);
        item.put("label", label);
        item.put("status", "INVALID");
        item.put("value", value);
        item.put("why", why);
        item.put("fix", fix);
        items.add(item);
    }

    /**
     * Enough of a value to recognise it, not enough to copy it.
     *
     * The bank account in particular has no business being echoed in full to a screen that only
     * needs to say whether it is set.
     */
    private String mask(String value) {
        String v = value.trim();
        if (v.length() <= 4) return v;
        return v.substring(0, 2) + "…" + v.substring(v.length() - 4);
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

        // The recorded turnover quietly decides two obligations, and getting it wrong is not a
        // cosmetic error. Above five crore, e-invoicing is compulsory and rule 48(5) says a
        // document that should have been e-invoiced but was not "shall not be treated as an
        // invoice" - so with a simulated IRP the store would be issuing things that are not
        // invoices at all, while the PDF prints a reference that looks like one.
        //
        // It also raises the HSN requirement to six digits, which is why an invoice can end up
        // declaring "HSN/SAC digits: 6" over a four-digit code and contradicting itself.
        if (GstComplianceUtil.requiresEInvoice(config.getAnnualTurnover())) {
            gaps.add("Turnover is recorded as ₹" + config.getAnnualTurnover() + ", which makes "
                    + "e-invoicing compulsory. Every invoice must carry a real IRN and QR from "
                    + "the government portal, and under rule 48(5) one that does not is not an "
                    + "invoice at all. Check this figure is right - a store below ₹5 crore should "
                    + "not have it set, and it also forces 6-digit HSN codes on every product.");
        }
        return gaps;
    }

    /**
     * Sellers who are approved to trade but should not be, or whom nobody has checked.
     *
     * The onboarding form now asks both questions, so no new seller can get past them. That
     * does nothing for anyone already approved - and it is the marketplace, not the seller,
     * that carries the exposure for facilitating a supply the seller was not permitted to make.
     * Section 24 requires registration of anyone selling through an operator whatever their
     * turnover, and section 10(2)(d) bars a composition taxpayer outright.
     *
     * Unanswered is reported separately from barred. One is a seller to remove; the other is a
     * question to ask, and conflating them would either overstate the problem or bury it.
     */
    public List<Map<String, Object>> sellerGaps() {
        List<Map<String, Object>> gaps = new ArrayList<>();
        for (SellerRegistration reg : sellerRegRepo.findAll()) {
            if (!"APPROVED".equalsIgnoreCase(String.valueOf(reg.getStatus()))) continue;

            if (isBlank(reg.getGstin())) {
                gaps.add(sellerGap(reg, "No GSTIN, but selling through a marketplace requires "
                        + "registration under section 24 whatever the turnover.",
                        "Suspend the account until they register, or collect the GSTIN."));
            }
            if (reg.isOnCompositionScheme()) {
                gaps.add(sellerGap(reg, "Registered under the composition scheme, which section "
                        + "10(2)(d) bars from supplying through an operator that collects TCS.",
                        "Suspend the account. They would have to move to the regular scheme."));
            } else if (reg.getCompositionScheme() == null) {
                gaps.add(sellerGap(reg, "Nobody has asked whether this seller is on the "
                        + "composition scheme. Their GSTIN does not reveal it.",
                        "Ask them. It is one question and it decides whether they may trade here."));
            }
        }
        return gaps;
    }

    private Map<String, Object> sellerGap(SellerRegistration reg, String problem, String fix) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sellerRegistrationId", reg.getId());
        row.put("businessName", reg.getBusinessName());
        row.put("gstin", reg.getGstin());
        row.put("problem", problem);
        row.put("fix", fix);
        return row;
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
