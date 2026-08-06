package com.cauverystore.client;

import com.cauverystore.entities.GstInvoice;
import com.cauverystore.util.GstComplianceUtil;
import com.cauverystore.util.QrCodeUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
// The default. LiveGstnClient takes over only when gstn.simulated is explicitly false, so a
// missing or malformed setting leaves the store simulating rather than half-talking to the
// real portal.
@ConditionalOnProperty(name = "gstn.simulated", havingValue = "true", matchIfMissing = true)
public class SimulatedGstnClient implements GstnClient {

    @Override
    public String getName() {
        return "GSTN_SIMULATED";
    }

    @Override
    public boolean isSimulated() {
        return true;
    }

    @Override
    public Map<String, Object> generateIrn(GstInvoice invoice) {
        String irn = "SIM" + sha16(invoice.getSellerGstin() + "|" + invoice.getOrderId() + "|" + invoice.getInvoiceNumber() + "|" + invoice.getInvoiceDate());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("irn", irn);
        resp.put("ackNo", "ACK-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + String.format("%06d", invoice.getOrderId() % 1000000));
        resp.put("ackDate", LocalDate.now().toString());
        resp.put("signedInvoice", irn + ".json");
        // QR content follows the GSTN e-invoice payload structure:
        // SellerGSTIN | InvoiceNumber | InvoiceDate(DDMMYYYY) | InvoiceValue | IRN
        double invoiceValue = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : 0;
        String qrText = String.join("|",
                invoice.getSellerGstin(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().format(DateTimeFormatter.ofPattern("ddMMyyyy")) : "",
                String.format("%.2f", invoiceValue),
                irn);
        resp.put("qrText", qrText);
        resp.put("qrCode", QrCodeUtil.generatePngDataUrl(qrText));
        resp.put("simulated", true);
        resp.put("status", "IRN_GENERATED");
        return resp;
    }

    @Override
    public Map<String, Object> generateEwayBill(GstInvoice invoice) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ewayBillNumber", "EWB" + String.format("%012d", (invoice.getOrderId() * 100 + 1) % 1000000000000L));
        resp.put("validUpto", LocalDate.now().plusDays(15).toString());
        resp.put("distanceKm", invoice.getDistanceKm() != null ? invoice.getDistanceKm() : 0);
        resp.put("vehicleNumber", invoice.getVehicleNumber());
        resp.put("simulated", true);
        resp.put("status", "EWB_GENERATED");
        return resp;
    }

    @Override
    public Map<String, Object> validateGstin(String gstin) {
        boolean valid = false;
        try {
            GstComplianceUtil.validateGstin(gstin);
            valid = true;
        } catch (IllegalArgumentException e) {
            // fall through
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("gstin", gstin);
        resp.put("valid", valid);
        if (valid) {
            String stateCode = gstin.substring(0, 2);
            resp.put("stateCode", stateCode);
            resp.put("legalName", "SIMULATED LEGAL NAME");
            resp.put("tradeName", "Simulated Entity");
            resp.put("address", "Simulated Address");
            resp.put("status", "ACTIVE");
        } else {
            resp.put("status", "INVALID");
            resp.put("error", "Invalid GSTIN format");
        }
        resp.put("simulated", true);
        return resp;
    }

    @Override
    public Map<String, Object> fetchGstr2bData(String gstin, String period) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("gstin", gstin);
        resp.put("period", period);
        resp.put("totalDocuments", 0);
        resp.put("totalItcAvailable", 0.0);
        resp.put("totalCgst", 0.0);
        resp.put("totalSgst", 0.0);
        resp.put("totalIgst", 0.0);
        resp.put("simulated", true);
        resp.put("message", "GSTR-2B data fetched (simulated). Configure gstn.simulated=false + credentials for live data.");
        return resp;
    }

    @Override
    public Map<String, Object> fetchGstr9Data(String gstin, String period) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("gstin", gstin);
        resp.put("fiscalYear", period);
        resp.put("simulated", true);
        resp.put("message", "GSTR-9 reference data fetched (simulated).");
        return resp;
    }

    @Override
    public Map<String, Object> fetchGstr8Data(String gstin, String period) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("gstin", gstin);
        resp.put("period", period);
        resp.put("simulated", true);
        resp.put("message", "GSTR-8 data fetched (simulated).");
        return resp;
    }

    @Override
    public Map<String, Object> fileReturn(String gstin, String form, String period) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("gstin", gstin);
        resp.put("form", form);
        resp.put("period", period);
        resp.put("status", "FILED");
        resp.put("arn", "ARN-SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        resp.put("filedOn", LocalDate.now().toString());
        resp.put("simulated", true);
        resp.put("message", "Return " + form + " for " + period + " filed (simulated).");
        return resp;
    }

    @Override
    public LocalDate getFilingDueDate(String form, String period) {
        YearMonth ym = YearMonth.parse(period, DateTimeFormatter.ofPattern("MMyyyy"));
        LocalDate first = ym.atDay(1);
        switch (form) {
            case "GSTR-1":
                return first.plusMonths(1).withDayOfMonth(11);
            case "GSTR-3B":
                return first.plusMonths(1).withDayOfMonth(20);
            case "GSTR-8":
                return first.plusMonths(1).withDayOfMonth(10);
            case "GSTR-9":
                return LocalDate.of(first.getYear() + 1, 12, 31);
            case "GSTR-9C":
                return LocalDate.of(first.getYear() + 1, 12, 31);
            default:
                return first.plusMonths(1).withDayOfMonth(15);
        }
    }

    private String sha16(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString().toUpperCase();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 32).toUpperCase();
        }
    }
}
