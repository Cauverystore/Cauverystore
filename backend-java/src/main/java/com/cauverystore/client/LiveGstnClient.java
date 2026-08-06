package com.cauverystore.client;

import com.cauverystore.entities.GstInvoice;
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
import java.util.Map;

/**
 * Registers invoices with the government e-invoice portal (IRP).
 *
 * <h2>Read this before switching it on</h2>
 *
 * <b>This code has never made a single call to the IRP.</b> It was written from the published
 * API specification, and there was no sandbox account to try it against. Treat it as a
 * starting point that needs a run against the NIC sandbox before it goes anywhere near a real
 * invoice - the auth handshake in particular is the kind of thing that is either exactly right
 * or completely wrong, with little in between.
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

    /**
     * Fails fast when the portal is only half-configured.
     *
     * Called before every request rather than at startup: a half-set environment should stop
     * the one invoice that needs registering, not prevent the whole store from booting.
     */
    private void assertConfigured() {
        if (isBlank(clientId) || isBlank(clientSecret) || isBlank(username)
                || isBlank(password) || isBlank(gstin) || isBlank(publicKeyBase64)) {
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
     */
    private Map<String, Object> buildInvoicePayload(GstInvoice inv) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("Version", "1.1");

        doc.put("TranDtls", Map.of(
                "TaxSch", "GST",
                "SupTyp", isBlank(inv.getBuyerGstin()) ? "B2C" : "B2B",
                "RegRev", "N",
                "IgstOnIntra", "N"));

        doc.put("DocDtls", Map.of(
                "Typ", "INV",
                "No", inv.getInvoiceNumber(),
                "Dt", inv.getInvoiceDate() == null ? "" : inv.getInvoiceDate().format(IRP_DATE)));

        doc.put("SellerDtls", Map.of("Gstin", inv.getSellerGstin()));

        Map<String, Object> buyer = new LinkedHashMap<>();
        buyer.put("Gstin", isBlank(inv.getBuyerGstin()) ? "URP" : inv.getBuyerGstin());
        buyer.put("LglNm", inv.getBuyerName());
        buyer.put("Pos", posCode(inv));
        buyer.put("Addr1", inv.getBuyerAddress());
        doc.put("BuyerDtls", buyer);

        doc.put("ValDtls", Map.of(
                "AssVal", nz(inv.getTaxableAmount()),
                "CgstVal", nz(inv.getCgstAmount()),
                "SgstVal", nz(inv.getSgstAmount()),
                "IgstVal", nz(inv.getIgstAmount()),
                "TotInvVal", nz(inv.getTotalAmount())));

        return doc;
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
