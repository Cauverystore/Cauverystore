package com.cauverystore.service;

import com.cauverystore.client.LiveGstnClient;
import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.GstInvoiceItem;
import com.cauverystore.util.StatutoryRates;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * E-way bills (Rule 138): when one is needed, how long it lasts, and the bulk-generation JSON
 * the seller uploads at ewaybillgst.gov.in.
 *
 * The store does not generate e-way bills itself. The e-way bill API needs its own GSP
 * registration and credentials, which the store does not have; and the simulator used to put a
 * made-up "EWB..." number on real invoices, which is a false statement on a tax document. So an
 * invoice that needs one is marked REQUIRED, the seller generates it on the portal (one at a
 * time or in bulk from this file), and records the number back against the invoice.
 */
@Service
public class EwayBillService {

    public static final String NOT_REQUIRED = "NOT_REQUIRED";
    public static final String REQUIRED = "REQUIRED";
    public static final String GENERATED = "GENERATED";

    /** An e-way bill number is 12 digits. */
    private static final Pattern EWB_NUMBER = Pattern.compile("\\d{12}");
    private static final Pattern PINCODE = Pattern.compile("(?<!\\d)([1-9]\\d{5})(?!\\d)");
    private static final DateTimeFormatter PORTAL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ------------------------------------------------------------------ when one is needed

    /**
     * Value of the consignment for Rule 138: taxable value plus the tax on it, for goods only -
     * exempt (nil-rate) goods are left out, and so are service lines such as the delivery charge,
     * which are not goods moving.
     */
    public static double consignmentValue(GstInvoice inv) {
        if (inv.getItems() == null || inv.getItems().isEmpty()) {
            return nz(inv.getTaxableAmount()) + nz(inv.getTotalTax()) + nz(inv.getTotalCess());
        }
        double v = 0;
        for (GstInvoiceItem i : inv.getItems()) {
            if (!isGoods(i) || totalRate(i) <= 0) continue;
            v += nz(i.getTaxableValue()) + nz(i.getCgstAmount()) + nz(i.getSgstAmount())
                    + nz(i.getIgstAmount()) + nz(i.getCessAmount());
        }
        return round2(v);
    }

    public static boolean isRequired(GstInvoice inv) {
        LocalDate on = inv.getInvoiceDate() != null ? inv.getInvoiceDate() : LocalDate.now();
        return consignmentValue(inv) > StatutoryRates.rateOn(StatutoryRates.EWB_CONSIGNMENT_THRESHOLD, on);
    }

    // ------------------------------------------------------------------ validity

    /**
     * Last day an e-way bill is valid, Rule 138(10): one day for the first 200 km and one more
     * for each further 200 km or part of it (20 km for over-dimensional cargo). Each day runs
     * to midnight of the day after generation, so a one-day bill made at 10:00 on the 1st is
     * good until the end of the 2nd.
     */
    public static LocalDate validUpto(LocalDateTime generatedAt, double distanceKm, boolean overDimensional) {
        double per = overDimensional ? 20.0 : 200.0;
        long days = Math.max(1, (long) Math.ceil(Math.max(distanceKm, 0) / per));
        return generatedAt.toLocalDate().plusDays(days);
    }

    // ------------------------------------------------------------------ recording a number

    /** Applies the number the seller got from the portal. Throws on anything malformed. */
    public static void record(GstInvoice inv, String number, LocalDateTime generatedAt, LocalDate validUpto) {
        String n = number == null ? "" : number.replaceAll("\\s", "");
        if (!EWB_NUMBER.matcher(n).matches()) {
            throw new IllegalArgumentException("An e-way bill number is 12 digits, as printed on the portal.");
        }
        if (generatedAt == null) generatedAt = LocalDateTime.now();
        if (inv.getInvoiceDate() != null && generatedAt.toLocalDate().isBefore(inv.getInvoiceDate())) {
            throw new IllegalArgumentException("The e-way bill cannot predate the invoice it covers.");
        }
        if (validUpto != null && validUpto.isBefore(generatedAt.toLocalDate())) {
            throw new IllegalArgumentException("Valid-upto date is before the generation date.");
        }
        inv.setEwayBillNumber(n);
        inv.setEwayBillGeneratedAt(generatedAt);
        inv.setEwayBillExpiry(validUpto != null ? validUpto
                : validUpto(generatedAt, inv.getDistanceKm() != null ? inv.getDistanceKm() : 0, false));
        inv.setEwayBillStatus(GENERATED);
    }

    // ------------------------------------------------------------------ bulk JSON

    /**
     * The e-way bill portal's bulk-generation file for these invoices (Generate New EWB ->
     * Bulk). Part A only, unless a vehicle number is already on the invoice: a courier fills
     * Part B with its own transporter ID. A distance of 0 asks the portal to work it out from
     * the two PIN codes.
     */
    public String bulkJson(List<GstInvoice> invoices) {
        List<Map<String, Object>> bills = new ArrayList<>();
        for (GstInvoice inv : invoices) bills.add(bill(inv));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", "1.0.0621");
        root.put("billLists", bills);
        try {
            return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    Map<String, Object> bill(GstInvoice inv) {
        List<String> problems = new ArrayList<>();
        Integer fromPin = pincode(inv.getSellerAddress());
        String toAddress = inv.getDeliveryAddress() != null ? inv.getDeliveryAddress() : inv.getBuyerAddress();
        Integer toPin = pincode(toAddress);
        if (fromPin == null) problems.add("seller address has no PIN code");
        if (toPin == null) problems.add("delivery address has no PIN code");
        if (inv.getSellerGstin() == null) problems.add("no seller GSTIN");
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Invoice " + inv.getInvoiceNumber() + ": " + String.join(", ", problems));
        }

        int fromState = Integer.parseInt(inv.getSellerGstin().substring(0, 2));
        int billToState = stateNo(inv.getPlaceOfSupply(), inv.getBuyerStateCode(), fromState);
        int shipToState = stateNo(inv.getDeliveryStateCode(), null, billToState);
        boolean b2b = inv.getBuyerGstin() != null && !inv.getBuyerGstin().isBlank()
                && !"URP".equalsIgnoreCase(inv.getBuyerGstin());

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("userGstin", inv.getSellerGstin());
        b.put("supplyType", "O");
        b.put("subSupplyType", 1);
        b.put("subSupplyDesc", "");
        b.put("docType", "INV");
        b.put("docNo", inv.getInvoiceNumber());
        b.put("docDate", inv.getInvoiceDate() != null ? inv.getInvoiceDate().format(PORTAL_DATE) : "");
        b.put("fromGstin", inv.getSellerGstin());
        b.put("fromTrdName", nn(inv.getSellerLegalName()));
        String[] from = addressLines(inv.getSellerAddress());
        b.put("fromAddr1", from[0]);
        b.put("fromAddr2", from[1]);
        b.put("fromPlace", from[2]);
        b.put("fromPincode", fromPin);
        b.put("fromStateCode", fromState);
        b.put("actFromStateCode", fromState);
        b.put("toGstin", b2b ? inv.getBuyerGstin() : "URP");
        b.put("toTrdName", nn(inv.getBuyerName()));
        String[] to = addressLines(toAddress);
        b.put("toAddr1", to[0]);
        b.put("toAddr2", to[1]);
        b.put("toPlace", to[2]);
        b.put("toPincode", toPin);
        b.put("toStateCode", billToState);
        b.put("actToStateCode", shipToState);
        // 1 regular, 2 bill-to ship-to
        b.put("transactionType", Boolean.TRUE.equals(inv.getBillToShipTo()) ? 2 : 1);

        double taxable = 0, cgst = 0, sgst = 0, igst = 0, cess = 0, other = 0;
        List<Map<String, Object>> items = new ArrayList<>();
        String mainHsn = null;
        double mainValue = -1;
        int n = 0;
        for (GstInvoiceItem i : inv.getItems() == null ? List.<GstInvoiceItem>of() : inv.getItems()) {
            if (!isGoods(i)) {
                // Delivery charge and other services travel on the invoice, not as goods.
                other += nz(i.getTaxableValue()) + nz(i.getCgstAmount()) + nz(i.getSgstAmount())
                        + nz(i.getIgstAmount()) + nz(i.getCessAmount());
                continue;
            }
            taxable += nz(i.getTaxableValue());
            cgst += nz(i.getCgstAmount());
            sgst += nz(i.getSgstAmount());
            igst += nz(i.getIgstAmount());
            cess += nz(i.getCessAmount());
            if (nz(i.getTaxableValue()) > mainValue) {
                mainValue = nz(i.getTaxableValue());
                mainHsn = i.getHsnCode();
            }
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("itemNo", ++n);
            it.put("productName", nn(i.getProductName()));
            it.put("productDesc", nn(i.getBillDescription() != null ? i.getBillDescription() : i.getProductName()));
            it.put("hsnCode", number(i.getHsnCode()));
            it.put("quantity", i.getQuantity() != null ? i.getQuantity() : 0);
            it.put("qtyUnit", LiveGstnClient.uqc(i.getUnitOfMeasure()));
            it.put("taxableAmount", round2(nz(i.getTaxableValue())));
            it.put("cgstRate", nz(i.getCgstRate()));
            it.put("sgstRate", nz(i.getSgstRate()));
            it.put("igstRate", nz(i.getIgstRate()));
            it.put("cessRate", nz(i.getCessRate()));
            it.put("cessNonadvol", 0);
            items.add(it);
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Invoice " + inv.getInvoiceNumber() + " has no goods lines.");
        }
        b.put("totalValue", round2(taxable));
        b.put("cgstValue", round2(cgst));
        b.put("sgstValue", round2(sgst));
        b.put("igstValue", round2(igst));
        b.put("cessValue", round2(cess));
        b.put("cessNonAdvolValue", 0);
        b.put("otherValue", round2(other));
        b.put("totInvValue", round2(nz(inv.getTotalAmount())));
        b.put("transMode", "1");   // road
        b.put("transDistance", inv.getDistanceKm() != null ? String.valueOf(Math.round(inv.getDistanceKm())) : "0");
        b.put("transporterName", nn(inv.getTransporterName()));
        b.put("transporterId", nn(inv.getTransporterId()));
        b.put("transDocNo", "");
        b.put("transDocDate", "");
        b.put("vehicleNo", nn(inv.getVehicleNumber()).replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT));
        b.put("vehicleType", inv.getVehicleNumber() != null && !inv.getVehicleNumber().isBlank()
                ? nn(inv.getVehicleType() != null ? inv.getVehicleType() : "R") : "");
        b.put("mainHsnCode", number(mainHsn));
        b.put("itemList", items);
        return b;
    }

    // ------------------------------------------------------------------ helpers

    static boolean isGoods(GstInvoiceItem i) {
        return i.getHsnCode() != null && !i.getHsnCode().isBlank()
                && !i.getHsnCode().startsWith("99");   // chapter 99 is services (SAC)
    }

    private static double totalRate(GstInvoiceItem i) {
        return nz(i.getCgstRate()) + nz(i.getSgstRate()) + nz(i.getIgstRate());
    }

    /** The last PIN code in an address, which is where an Indian address puts it. */
    static Integer pincode(String address) {
        if (address == null) return null;
        Matcher m = PINCODE.matcher(address);
        Integer last = null;
        while (m.find()) last = Integer.parseInt(m.group(1));
        return last;
    }

    /** Line 1, line 2 and place, within the portal's 120/120/50 characters. */
    static String[] addressLines(String address) {
        String a = address == null ? "" : address.replaceAll("\\s+", " ").trim();
        a = PINCODE.matcher(a).replaceAll("").replaceAll("[,\\s]+$", "").trim();
        String[] parts = a.split("\\s*,\\s*");
        String place = parts.length > 1 ? parts[parts.length - 2] : parts[0];
        String line1 = cut(a, 120);
        String line2 = a.length() > 120 ? cut(a.substring(120), 120) : "";
        return new String[] { line1, line2, cut(place, 50) };
    }

    private static int stateNo(String preferred, String fallback, int otherwise) {
        for (String s : new String[] { preferred, fallback }) {
            if (s != null && s.length() >= 2 && Character.isDigit(s.charAt(0)) && Character.isDigit(s.charAt(1))) {
                return Integer.parseInt(s.substring(0, 2));
            }
        }
        return otherwise;
    }

    private static Object number(String code) {
        if (code == null) return "";
        String digits = code.replaceAll("\\D", "");
        return digits.isEmpty() ? "" : Long.parseLong(digits);
    }

    private static String cut(String s, int max) { return s.length() <= max ? s : s.substring(0, max); }
    private static String nn(String s) { return s == null ? "" : s; }
    private static double nz(Double d) { return d == null ? 0.0 : d; }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
