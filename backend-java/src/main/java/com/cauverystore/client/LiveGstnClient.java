package com.cauverystore.client;

import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.GstInvoiceItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers invoices with the government e-invoice portal (IRP).
 *
 * <h2>Read this before switching it on</h2>
 *
 * <b>This code has never made a single call to the IRP.</b> It was written from the published
 * API specification, and there was no sandbox account to try it against. The payload now
 * carries the ItemList and supplier state code the schema makes mandatory (see
 * buildInvoicePayload), but the auth handshake in particular is the kind of thing that is
 * either exactly right or completely wrong, with little in between. Treat it as a starting
 * point that needs a run against the NIC sandbox before it goes anywhere near a real invoice.
 *
 * Selected only when <code>gstn.simulated=false</code>. The default is the simulated client,
 * so nothing here runs by accident.
 *
 * <h2>The one rule that matters</h2>
 *
 * If registration fails, this throws. It never invents an IRN, never falls back to the
 * simulator, and never returns a partial success. An invoice carrying a fabricated IRN is far
 * worse than an invoice with none: it looks registered to everyone downstream, the buyer may
 * claim input credit against a number the portal has never heard of, and nobody finds out
 * until a reconciliation months later. A missing IRN is a visible problem that can be retried;
 * a fake one is an invisible one that cannot.
 *
 * The caller is expected to catch that failure, leave the invoice unregistered and queue it -
 * see GstInvoiceService, which puts it on gst_sync_queue for the scheduler to retry.
 */
@Component
@ConditionalOnProperty(name = "gstn.simulated", havingValue = "false")
public class LiveGstnClient implements GstnClient {

    private static final Logger log = LoggerFactory.getLogger(LiveGstnClient.class);

    private static final DateTimeFormatter IRP_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter QR_DATE = DateTimeFormatter.ofPattern("ddMMyyyy");

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gstn.base-url:https://einvapi.gst.gov.in}")
    private String baseUrl;

    @Value("${gstn.client-id:}")
    private String clientId;

    @Value("${gstn.client-secret:}")
    private String clientSecret;

    @Value("${gstn.username:}")
    private String username;

    @Value("${gstn.password:}")
    private String password;

    @Value("${gstn.gstin:}")
    private String gstin;

    /** The IRP's RSA public key, base64 DER. Used to wrap the session key at auth. */
    @Value("${gstn.public-key:}")
    private String publicKeyBase64;

    /** Cached session, re-fetched when it expires. */
    private String authToken;
    private byte[] sek;
    private LocalDateTime tokenExpiry;

    /** Thrown when the portal cannot be reached or refuses the request. */
    public static class IrpException extends RuntimeException {
        public IrpException(String message) { super(message); }
        public IrpException(String message, Throwable cause) { super(message, cause); }
    }

    @Override
    public String getName() {
        return "GSTN_LIVE";
    }

    @Override
    public boolean isSimulated() {
        return false;
    }

    @Override
    public boolean isConfigured() {
        return !isBlank(clientId) && !isBlank(clientSecret) && !isBlank(username)
                && !isBlank(password) && !isBlank(gstin) && !isBlank(publicKeyBase64);
    }

    /**
     * Fails fast when the portal is only half-configured.
     *
     * Called before every request rather than at startup: a half-set environment should stop
     * the one invoice that needs registering, not prevent the whole store from booting.
     */
    private void assertConfigured() {
        if (!isConfigured()) {
            throw new IrpException(
                    "The live e-invoice client is enabled (gstn.simulated=false) but not fully "
                            + "configured. It needs gstn.client-id, client-secret, username, "
                            + "password, gstin and public-key. Registration is refused rather "
                            + "than falling back to the simulator, which would put a made-up IRN "
                            + "on a real invoice.");
        }
    }

    // ------------------------------------------------------------------ auth

    private synchronized void ensureSession() {
        if (authToken != null && sek != null
                && tokenExpiry != null && tokenExpiry.isAfter(LocalDateTime.now().plusMinutes(2))) {
            return;
        }
        assertConfigured();
        try {
            byte[] appKey = new byte[32];
            new SecureRandom().nextBytes(appKey);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("UserName", username);
            payload.put("Password", password);
            payload.put("AppKey", Base64.getEncoder().encodeToString(appKey));
            payload.put("ForceRefreshAccessToken", true);

            Map<String, Object> body = Map.of(
                    "Data", rsaEncrypt(objectMapper.writeValueAsBytes(payload)));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("client_id", clientId);
            headers.set("client_secret", clientSecret);
            headers.set("Gstin", gstin);

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/eivital/v1.04/auth", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            Map<?, ?> data = decodeAuthResponse(response.getBody(), appKey);
            this.authToken = str(data.get("AuthToken"));
            this.sek = Base64.getDecoder().decode(str(data.get("Sek")));
            this.tokenExpiry = parseExpiry(str(data.get("TokenExpiry")));

            if (isBlank(authToken) || sek == null) {
                throw new IrpException("The IRP returned no usable session token.");
            }
            log.info("IRP session established, valid until {}", tokenExpiry);
        } catch (IrpException e) {
            throw e;
        } catch (Exception e) {
            throw new IrpException("Could not authenticate with the IRP: " + e.getMessage(), e);
        }
    }

    private Map<?, ?> decodeAuthResponse(Map<?, ?> body, byte[] appKey) throws Exception {
        if (body == null) throw new IrpException("Empty response from the IRP auth endpoint.");
        if (!isSuccess(body)) {
            throw new IrpException("The IRP rejected the credentials: " + describeError(body));
        }
        Object data = body.get("Data");
        if (data == null) throw new IrpException("The IRP auth response carried no Data.");
        byte[] decrypted = aesDecrypt(Base64.getDecoder().decode(str(data)), appKey);
        return objectMapper.readValue(decrypted, Map.class);
    }

    // ------------------------------------------------------------------ IRN

    @Override
    public Map<String, Object> generateIrn(GstInvoice invoice) {
        // Missing credentials are reported first: that is the problem to fix before any other.
        assertConfigured();
        // Checked before any network call: a payload the IRP would refuse is refused here, with
        // a message that says what to fix, instead of as a numbered portal error.
        List<String> problems = problemsWith(invoice);
        if (!problems.isEmpty()) {
            throw new IrpException("Invoice " + invoice.getInvoiceNumber()
                    + " cannot be e-invoiced yet: " + String.join("; ", problems));
        }
        ensureSession();
        try {
            byte[] payload = objectMapper.writeValueAsBytes(buildInvoicePayload(invoice));
            Map<String, Object> body = Map.of("Data", aesEncrypt(payload, sek));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("client_id", clientId);
            headers.set("client_secret", clientSecret);
            headers.set("Gstin", gstin);
            headers.set("user_name", username);
            headers.set("AuthToken", authToken);

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/eicore/v1.03/Invoice", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null || !isSuccess(responseBody)) {
                throw new IrpException("The IRP refused invoice " + invoice.getInvoiceNumber()
                        + ": " + describeError(responseBody));
            }
            byte[] decrypted = aesDecrypt(
                    Base64.getDecoder().decode(str(responseBody.get("Data"))), sek);
            Map<?, ?> data = objectMapper.readValue(decrypted, Map.class);

            String irn = str(data.get("Irn"));
            if (isBlank(irn)) {
                throw new IrpException("The IRP accepted invoice " + invoice.getInvoiceNumber()
                        + " but returned no IRN.");
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("irn", irn);
            out.put("ackNo", str(data.get("AckNo")));
            out.put("ackDate", str(data.get("AckDt")));
            out.put("signedInvoice", str(data.get("SignedInvoice")));
            // The portal returns its own signed QR. Never substitute a locally drawn one -
            // only the signed payload proves the invoice was registered.
            out.put("qrText", str(data.get("SignedQRCode")));
            out.put("qrCode", str(data.get("SignedQRCode")));
            out.put("simulated", false);
            out.put("status", "IRN_GENERATED");
            return out;
        } catch (IrpException e) {
            throw e;
        } catch (Exception e) {
            throw new IrpException("Could not register invoice " + invoice.getInvoiceNumber()
                    + " with the IRP: " + e.getMessage(), e);
        }
    }

    /**
     * The invoice in the IRP's schema.
     *
     * Only the fields the portal requires are sent. Anything it does not ask for is left out
     * rather than guessed at, because a schema violation is rejected outright.
     *
     * The two things that changed since this was first written from the spec:
     *
     * - ItemList is mandatory. An invoice with header amounts but no lines is rejected - the
     *   IRP re-derives the header totals from the lines and refuses a mismatch. Every line the
     *   store charged (goods at their HSN, delivery at its SAC) goes up as its own entry with
     *   its own GstRt, cess and TotItemVal.
     *
     * - SellerDtls must carry the supplier's state. Error 2258 rejects a supplier whose state
     *   code does not match the state inside the GSTIN, so it is derived from the GSTIN rather
     *   than trusted to the seller's address text, which a typo could split.
     */
    Map<String, Object> buildInvoicePayload(GstInvoice inv) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("Version", "1.1");

        // B2B is the only supply type the store makes that the IRP takes: consumer invoices are
        // not e-invoiced at all (problemsWith refuses them), and the store has no export or SEZ
        // sales.
        doc.put("TranDtls", Map.of(
                "TaxSch", "GST",
                "SupTyp", "B2B",
                "RegRev", "N",
                "IgstOnIntra", "N"));

        doc.put("DocDtls", Map.of(
                "Typ", "INV",
                "No", inv.getInvoiceNumber(),
                "Dt", inv.getInvoiceDate() == null ? "" : inv.getInvoiceDate().format(IRP_DATE)));

        Map<String, Object> seller = new LinkedHashMap<>();
        seller.put("Gstin", inv.getSellerGstin());
        // The state code is the first two characters of the GSTIN by construction - the only
        // value the IRP's state-code check will accept.
        String sellerState = stateCodeOf(inv.getSellerGstin());
        if (sellerState != null) seller.put("Stcd", sellerState);
        if (!isBlank(inv.getSellerLegalName())) seller.put("LglNm", cut(inv.getSellerLegalName(), 100));
        putAddress(seller, inv.getSellerAddress());
        doc.put("SellerDtls", seller);

        Map<String, Object> buyer = new LinkedHashMap<>();
        buyer.put("Gstin", isBlank(inv.getBuyerGstin()) ? "URP" : inv.getBuyerGstin());
        if (!isBlank(inv.getBuyerName())) buyer.put("LglNm", cut(inv.getBuyerName(), 100));
        buyer.put("Pos", posCode(inv));
        putAddress(buyer, inv.getBuyerAddress());
        String buyerState = stateCodeOf(inv.getBuyerGstin());
        if (buyerState == null) buyerState = normalizeStateCode(inv.getBuyerStateCode());
        if (buyerState != null) buyer.put("Stcd", buyerState);
        doc.put("BuyerDtls", buyer);
        if (inv.isBillToShipTo()) doc.put("ShipDtls", shipDetails(inv));

        doc.put("ItemList", buildItemList(inv));
        Map<String, Object> val = new LinkedHashMap<>();
        double ass = r2(nz(inv.getTaxableAmount()));
        double cgst = r2(nz(inv.getCgstAmount()));
        double sgst = r2(nz(inv.getSgstAmount()));
        double igst = r2(nz(inv.getIgstAmount()));
        double cess = r2(nz(inv.getTotalCess()));
        double total = r2(nz(inv.getTotalAmount()));
        val.put("AssVal", ass);
        val.put("CgstVal", cgst);
        val.put("SgstVal", sgst);
        val.put("IgstVal", igst);
        // "CesVal" is the schema's spelling. An earlier "CessVal" is not a field the IRP knows.
        val.put("CesVal", cess);
        // No state cess exists under the present law, so the field is always zero; it is sent
        // anyway because the schema validates it as part of the amount reconciliation.
        val.put("StCesVal", 0.0);
        // The IRP checks TotInvVal = AssVal + taxes + RndOffAmt. Paise lost to rounding the
        // printed total go here rather than making the invoice fail that check.
        double roundOff = r2(total - (ass + cgst + sgst + igst + cess));
        if (roundOff != 0.0) val.put("RndOffAmt", roundOff);
        val.put("TotInvVal", total);
        doc.put("ValDtls", val);

        return doc;
    }

    /**
     * What would make the IRP refuse this invoice, as plain sentences. Empty means it can go.
     * The rules are the NIC e-invoice schema v1.1's, cross-checked against Odoo's l10n_in_edi,
     * which files e-invoices in production.
     */
    List<String> problemsWith(GstInvoice inv) {
        List<String> out = new java.util.ArrayList<>();
        String buyerGstin = inv.getBuyerGstin();
        if (isBlank(buyerGstin) || "URP".equalsIgnoreCase(buyerGstin.trim())) {
            out.add("the buyer has no GSTIN; only invoices to registered buyers are e-invoiced");
        }
        String no = inv.getInvoiceNumber();
        if (no == null || !no.matches("[A-Za-z1-9][A-Za-z0-9/-]{0,15}")) {
            out.add("the invoice number must be 1-16 letters, digits, / or -, not starting with 0, /"
                    + " or -");
        }
        if (isBlank(inv.getSellerLegalName())) out.add("the seller's legal name is missing");
        addressProblems(out, "seller", inv.getSellerAddress());
        if (isBlank(inv.getBuyerName())) out.add("the buyer's legal name is missing");
        addressProblems(out, "buyer", inv.getBuyerAddress());
        if (inv.getItems() == null || inv.getItems().isEmpty()) out.add("it has no lines");
        if (inv.isBillToShipTo()) {
            if (isBlank(inv.getConsigneeName())) out.add("the consignee's name is missing");
            addressProblems(out, "consignee", shipToAddress(inv));
            if (normalizeStateCode(inv.getConsigneeStateCode()) == null) {
                out.add("the consignee's state is unknown; give their GSTIN or a PIN code");
            }
        }
        return out;
    }

    /**
     * ShipDtls for a bill-to / ship-to supply: who receives the goods and where.
     *
     * From 1 August 2026 the IRP requires the ship-to GSTIN here (GSTN advisory, 25 June
     * 2026), with "URP" for a consignee who is not registered. Sent only on bill-to / ship-to
     * invoices; otherwise the goods go to the buyer and BuyerDtls already says where.
     */
    Map<String, Object> shipDetails(GstInvoice inv) {
        Map<String, Object> ship = new LinkedHashMap<>();
        String gstin = inv.getConsigneeGstin();
        ship.put("Gstin", isBlank(gstin) ? "URP" : gstin.trim().toUpperCase());
        if (!isBlank(inv.getConsigneeName())) ship.put("LglNm", cut(inv.getConsigneeName(), 100));
        putAddress(ship, shipToAddress(inv));
        String state = normalizeStateCode(inv.getConsigneeStateCode());
        if (state != null) ship.put("Stcd", state);
        return ship;
    }

    /** The consignee's own address, or the delivery address when they gave none. */
    private static String shipToAddress(GstInvoice inv) {
        return isBlank(inv.getConsigneeAddress()) ? inv.getDeliveryAddress() : inv.getConsigneeAddress();
    }

    private static void addressProblems(List<String> out, String who, String address) {
        Address a = Address.parse(address);
        if (a.line1() == null) out.add("the " + who + "'s address is missing");
        if (a.pin() == null) out.add("the " + who + "'s address has no 6-digit PIN code");
        if (a.locality() == null) out.add("the " + who + "'s address has no town or city");
    }

    private static void putAddress(Map<String, Object> party, String address) {
        Address a = Address.parse(address);
        if (a.line1() != null) party.put("Addr1", a.line1());
        if (a.line2() != null) party.put("Addr2", a.line2());
        if (a.locality() != null) party.put("Loc", a.locality());
        if (a.pin() != null) party.put("Pin", a.pin());
    }

    /**
     * An invoice address split into the IRP's fields.
     *
     * The store writes addresses as "line 1, line 2, city, state, PIN" (formatAddress in
     * GstInvoiceService). The IRP wants the street in Addr1/Addr2 (100 characters each), the
     * town in Loc (3-50) and the PIN as a number, and rejects an address without them.
     */
    record Address(String line1, String line2, String locality, Integer pin) {
        private static final java.util.regex.Pattern PIN =
                java.util.regex.Pattern.compile("(?<!\\d)([1-9]\\d{5})(?!\\d)");

        static Address parse(String raw) {
            if (raw == null || raw.isBlank()) return new Address(null, null, null, null);
            List<String> parts = new java.util.ArrayList<>();
            for (String p : raw.replace('\n', ',').split(",")) {
                if (!p.isBlank()) parts.add(p.trim().replaceAll("\\s+", " "));
            }
            Integer pin = null;
            int pinAt = -1;
            for (int i = parts.size() - 1; i >= 0 && pin == null; i--) {
                java.util.regex.Matcher m = PIN.matcher(parts.get(i));
                if (m.find()) {
                    pin = Integer.valueOf(m.group(1));
                    pinAt = i;
                }
            }
            String locality = null;
            int streetEnd = parts.size();
            if (pinAt >= 0) {
                // "..., city, state, PIN": the town is two before the PIN; with only one part
                // before the PIN it is that part.
                int cityAt = pinAt >= 2 ? pinAt - 2 : pinAt - 1;
                if (cityAt >= 0) {
                    String c = parts.get(cityAt).replaceAll("[^\\p{L}\\p{N} .()-]", "").trim();
                    if (c.length() >= 3) locality = cut(c, 50);
                    streetEnd = cityAt;
                }
            }
            String street = String.join(", ", parts.subList(0, Math.max(0, streetEnd)));
            if (street.isBlank()) street = locality != null ? locality : String.join(", ", parts);
            String line1 = street;
            String line2 = null;
            if (street.length() > 100) {
                int cutAt = street.lastIndexOf(", ", 100);
                if (cutAt < 1) cutAt = 100;
                line1 = street.substring(0, cutAt).trim();
                line2 = cut(street.substring(cutAt).replaceFirst("^[,\\s]+", ""), 100);
                if (line2.length() < 3) line2 = null;
            }
            return new Address(line1.isBlank() ? null : line1, line2, locality, pin);
        }
    }

    /** The IRP's unit codes (UQC). Anything else is sent as OTH, as the portal asks. */
    private static final java.util.Set<String> UQC = java.util.Set.of(("BAG BAL BDL BKL BOU BOX BTL"
            + " BUN CAN CBM CCM CMS CTN DOZ DRM GGK GMS GRS GYD KGS KLR KME LTR MLS MLT MTR MTS NOS OTH"
            + " PAC PCS PRS QTL ROL SET SQF SQM SQY TBS TGM THD TON TUB UGS UNT YDS").split(" "));

    /** Common ways sellers write a unit, mapped to its UQC. */
    private static final Map<String, String> UNIT_ALIASES = Map.ofEntries(
            Map.entry("PC", "PCS"), Map.entry("PIECE", "PCS"), Map.entry("PIECES", "PCS"),
            Map.entry("NO", "NOS"), Map.entry("NUMBER", "NOS"), Map.entry("NUMBERS", "NOS"),
            Map.entry("UNIT", "UNT"), Map.entry("UNITS", "UNT"),
            Map.entry("KG", "KGS"), Map.entry("KILOGRAM", "KGS"), Map.entry("KILOGRAMS", "KGS"),
            Map.entry("G", "GMS"), Map.entry("GM", "GMS"), Map.entry("GRAM", "GMS"), Map.entry("GRAMS", "GMS"),
            Map.entry("L", "LTR"), Map.entry("LITRE", "LTR"), Map.entry("LITRES", "LTR"), Map.entry("LITER", "LTR"),
            Map.entry("ML", "MLT"), Map.entry("MILLILITRE", "MLT"),
            Map.entry("M", "MTR"), Map.entry("METRE", "MTR"), Map.entry("METER", "MTR"), Map.entry("METRES", "MTR"),
            Map.entry("PACK", "PAC"), Map.entry("PACKET", "PAC"), Map.entry("PACKETS", "PAC"),
            Map.entry("DOZEN", "DOZ"), Map.entry("PAIR", "PRS"), Map.entry("PAIRS", "PRS"),
            Map.entry("BOTTLE", "BTL"), Map.entry("BOTTLES", "BTL"), Map.entry("BOXES", "BOX"));

    public static String uqc(String unit) {
        if (unit == null || unit.isBlank()) return "NOS";
        String u = unit.trim().toUpperCase().replace(".", "");
        if (UQC.contains(u)) return u;
        String alias = UNIT_ALIASES.get(u);
        return alias != null ? alias : "OTH";
    }

    private static double r2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static double r3(double v) { return Math.round(v * 1000.0) / 1000.0; }

    private static String cut(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max).trim();
    }

    /**
     * One entry per invoice line, carrying the same figures the store charged the customer.
     *
     * TotItemVal is the assessable value plus the GST and cess on that line, which is what the
     * IRP reconciles against the header totals - a line whose figures do not add up is a
     * rejected invoice. The delivery line is a service (SAC), so IsServc is Y and its HSN cell
     * carries the SAC, exactly as the portal expects for a service entry.
     */
    List<Map<String, Object>> buildItemList(GstInvoice inv) {
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        if (inv.getItems() == null) return items;
        int slNo = 1;
        for (GstInvoiceItem line : inv.getItems()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("SlNo", String.valueOf(slNo++));
            String hsn = line.getHsnCode();
            boolean isService = isBlank(hsn) && !isBlank(line.getSacCode());
            if (isService) hsn = line.getSacCode();
            String desc = !isBlank(line.getBillDescription())
                    ? line.getBillDescription() : line.getProductName();
            if (!isBlank(desc)) item.put("PrdDesc", cut(desc.replaceAll("[\\r\\n]+", " "), 300));
            item.put("IsServc", isService ? "Y" : "N");
            // Digits only: "1006 30" or "1006.30" is refused as an HSN code.
            if (!isBlank(hsn)) item.put("HsnCd", hsn.replaceAll("\\D", ""));
            int qty = line.getQuantity() == null || line.getQuantity() <= 0 ? 1 : line.getQuantity();
            item.put("Qty", qty);
            item.put("Unit", uqc(line.getUnitOfMeasure()));
            double assAmt = r2(nz(line.getTaxableValue()));
            // The IRP checks UnitPrice x Qty against TotAmt, and AssAmt = TotAmt - Discount. The
            // taxable value is already net of any coupon, so the unit price sent is the taxable
            // value per unit - not the GST-inclusive shelf price, which would fail that check.
            item.put("UnitPrice", r3(assAmt / qty));
            item.put("TotAmt", assAmt);
            item.put("AssAmt", assAmt);
            // GstRt is the combined rate whether the supply is inter (IGST) or intra (CGST+SGST).
            item.put("GstRt", r3(nz(line.getIgstRate()) + nz(line.getCgstRate()) + nz(line.getSgstRate())));
            item.put("IgstAmt", r2(nz(line.getIgstAmount())));
            item.put("CgstAmt", r2(nz(line.getCgstAmount())));
            item.put("SgstAmt", r2(nz(line.getSgstAmount())));
            if (nz(line.getCessRate()) > 0) {
                item.put("CesRt", r3(nz(line.getCessRate())));
                item.put("CesAmt", r2(nz(line.getCessAmount())));
            }
            item.put("TotItemVal", Math.round((assAmt
                    + nz(line.getIgstAmount()) + nz(line.getCgstAmount())
                    + nz(line.getSgstAmount()) + nz(line.getCessAmount())) * 100.0) / 100.0);
            items.add(item);
        }
        return items;
    }

    /** First two characters of a GSTIN - the state code by construction. */
    private static String stateCodeOf(String gstin) {
        if (gstin == null || gstin.length() < 2) return null;
        String code = gstin.substring(0, 2);
        return code.chars().allMatch(Character::isDigit) ? code : null;
    }

    /** A stored state code is sometimes "7" instead of "07"; the IRP wants two digits. */
    private static String normalizeStateCode(String code) {
        if (code == null || code.isBlank()) return null;
        String trimmed = code.trim();
        if (trimmed.matches("\\d{2}")) return trimmed;
        if (trimmed.matches("\\d{1}")) return "0" + trimmed;
        return null;
    }

    /** Place of supply as the two-digit state code the IRP expects. */
    private String posCode(GstInvoice inv) {
        String pos = inv.getPlaceOfSupply();
        if (pos != null && pos.length() >= 2 && Character.isDigit(pos.charAt(0))) {
            return pos.substring(0, 2);
        }
        return inv.getDeliveryStateCode() != null ? inv.getDeliveryStateCode() : "";
    }

    // ------------------------------------------------- not yet implemented

    /**
     * These are separate portals with their own credentials and schemas, and writing them
     * unverified alongside an unverified IRN client would multiply the untested surface for no
     * benefit. They refuse rather than return something that looks like an answer.
     */
    private Map<String, Object> notImplemented(String what) {
        throw new IrpException(what + " is not implemented against the live portal yet. Set "
                + "gstn.simulated=true to use the simulator, or implement it before relying on it.");
    }

    @Override
    public Map<String, Object> generateEwayBill(GstInvoice invoice) { return notImplemented("E-way bill generation"); }

    @Override
    public Map<String, Object> validateGstin(String gstin) { return notImplemented("GSTIN validation"); }

    @Override
    public Map<String, Object> fetchGstr2bData(String gstin, String period) { return notImplemented("GSTR-2B download"); }

    @Override
    public Map<String, Object> fetchGstr9Data(String gstin, String period) { return notImplemented("GSTR-9 download"); }

    @Override
    public Map<String, Object> fetchGstr8Data(String gstin, String period) { return notImplemented("GSTR-8 download"); }

    @Override
    public Map<String, Object> fileReturn(String gstin, String form, String period) { return notImplemented("Return filing"); }

    @Override
    public LocalDate getFilingDueDate(String form, String period) {
        // A statutory date, not a portal call - the same answer either way.
        return new SimulatedGstnClient().getFilingDueDate(form, period);
    }

    // ------------------------------------------------------------- crypto

    private String rsaEncrypt(byte[] plain) throws Exception {
        byte[] der = Base64.getDecoder().decode(publicKeyBase64.trim());
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain));
    }

    private String aesEncrypt(byte[] plain, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain));
    }

    private byte[] aesDecrypt(byte[] cipherText, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(cipherText);
    }

    // -------------------------------------------------------------- helpers

    private boolean isSuccess(Map<?, ?> body) {
        Object status = body.get("Status");
        return "1".equals(str(status)) || Boolean.TRUE.equals(status);
    }

    private String describeError(Map<?, ?> body) {
        if (body == null) return "no response body";
        Object details = body.get("ErrorDetails");
        return details != null ? details.toString() : String.valueOf(body.get("ErrorMessage"));
    }

    private LocalDateTime parseExpiry(String raw) {
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            // Unparseable expiry means re-authenticating more often, which is harmless;
            // assuming it is valid for long would silently start failing mid-session.
            return LocalDateTime.now().plusMinutes(30);
        }
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static double nz(Double d) { return d == null ? 0.0 : d; }
}
