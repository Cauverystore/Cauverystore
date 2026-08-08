package com.cauverystore.util;

import java.util.regex.Pattern;

public class GstComplianceUtil {

    private static final Pattern GSTIN_PATTERN =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");

    /**
     * PAN: five letters, four digits, one letter. Not "10 alphanumeric" - the positions carry
     * meaning, and a loose check would accept strings the department never issued.
     */
    private static final Pattern PAN_PATTERN =
            Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]{1}$");

    /**
     * Enrolment number issued under Notification 34/2023 to unregistered persons supplying
     * goods intra-state through an e-commerce operator.
     *
     * Deliberately loose. It is a comparatively recent identifier, the published format is a
     * state code followed by an alphanumeric string, and rejecting a real one because this
     * pattern was drawn too tightly would keep a lawful seller off the platform - a worse
     * outcome than accepting a typo that the portal will reject anyway.
     */
    private static final Pattern ENROLMENT_PATTERN =
            Pattern.compile("^[0-9]{2}[0-9A-Z]{10,18}$");

    /**
     * GST state codes of the Union Territories without a state legislature - the only places a
     * supplier charges UTGST instead of SGST on intra-state supplies. Delhi, Puducherry and
     * Jammu &amp; Kashmir have legislatures, so SGST applies there and they are deliberately absent.
     */
    private static final java.util.Set<String> UTGST_STATES =
            java.util.Set.of("04", "26", "31", "35", "38");

    private static final String BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final double E_INVOICE_THRESHOLD = 5_00_00_000;
    // E-way bill threshold (Rule 138 CGST Rules, 2017): single consignment > ₹50,000 (Notification 74/2018 – Central Tax).
    private static final double E_WAYBILL_THRESHOLD = 50_000;
    // B2C Large (B2CL) threshold for GSTR-1 table 6A: inter-state supplies to unregistered persons
    // with invoice value exceeding ₹1,00,000 (53rd GST Council / Notification 12/2024 – Central Tax, effective 01-Aug-2024).
    private static final double B2C_LARGE_THRESHOLD = 1_00_000;

    /** Maximum invoice number length permitted under Rule 46 (16 characters). */
    public static final int MAX_INVOICE_NUMBER_LENGTH = 16;

    /**
     * 21-character MCA corporate identity number.
     * Shape: L/U + 5-digit industry code + 2-letter state + 4-digit year + 3-letter ownership
     * + 6-digit registration number.
     */
    private static final Pattern CIN_PATTERN =
            Pattern.compile("^[LU]\\d{5}[A-Z]{2}\\d{4}[A-Z]{3}\\d{6}$");

    public static boolean isValidCin(String cin) {
        return cin != null && CIN_PATTERN.matcher(cin.trim().toUpperCase()).matches();
    }

    /** IFSC: 4-letter bank code, a reserved 0, then a 6-character branch code. */
    private static final Pattern IFSC_PATTERN = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");

    public static boolean isValidIfsc(String ifsc) {
        return ifsc != null && IFSC_PATTERN.matcher(ifsc.trim().toUpperCase()).matches();
    }

    public static void validateGstin(String gstin) {
        if (gstin == null || gstin.trim().isEmpty()) {
            throw new IllegalArgumentException("GSTIN is required");
        }
        if (!GSTIN_PATTERN.matcher(gstin.trim().toUpperCase()).matches()) {
            throw new IllegalArgumentException("Invalid GSTIN format: " + gstin
                    + ". Expected 15 characters: 2-digit state code + 10-digit PAN + 1 entity code + 1 blank + 1 checksum");
        }
    }

    /** Optional field: blank is fine, wrong is not. */
    public static void validatePan(String pan) {
        if (pan == null || pan.trim().isEmpty()) return;
        if (!PAN_PATTERN.matcher(pan.trim().toUpperCase()).matches()) {
            throw new IllegalArgumentException("Invalid PAN format: " + pan
                    + ". Expected 10 characters: five letters, four digits, one letter (AAAAA9999A).");
        }
    }

    public static void validateEnrolmentNumber(String enrolment) {
        if (enrolment == null || enrolment.trim().isEmpty()) return;
        if (!ENROLMENT_PATTERN.matcher(enrolment.trim().toUpperCase()).matches()) {
            throw new IllegalArgumentException("Invalid enrolment number: " + enrolment
                    + ". Expected a two-digit state code followed by the identifier issued under "
                    + "Notification 34/2023.");
        }
    }

    /**
     * Whether a PAN agrees with the PAN embedded in a GSTIN.
     *
     * A GSTIN carries its holder's PAN at characters 3 to 12 by construction, so the two can be
     * checked against each other rather than stored as unrelated facts. A mismatch means one of
     * them belongs to somebody else, and an invoice carrying the wrong PAN misreports who the
     * supply was made to.
     */
    public static boolean panMatchesGstin(String pan, String gstin) {
        if (pan == null || gstin == null) return false;
        String p = pan.trim().toUpperCase();
        String g = gstin.trim().toUpperCase();
        if (!PAN_PATTERN.matcher(p).matches() || !GSTIN_PATTERN.matcher(g).matches()) return false;
        return g.substring(2, 12).equals(p);
    }

    /**
     * Verifies the 15th (checksum) character using the standard base-36 GSTIN
     * check-digit algorithm. Strict mode throws on mismatch; otherwise a boolean
     * is returned so the compliance report can surface it without blocking.
     */
    public static boolean hasValidCheckDigit(String gstin) {
        if (gstin == null || !GSTIN_PATTERN.matcher(gstin.trim().toUpperCase()).matches()) {
            return false;
        }
        String upper = gstin.trim().toUpperCase();
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int value = BASE36.indexOf(upper.charAt(i));
            if (value < 0) return false;
            sum += value * modPow2(i + 1);
        }
        int rem = sum % 36;
        int check = (36 - rem) % 36;
        return BASE36.charAt(check) == upper.charAt(14);
    }

    private static int modPow2(int exp) {
        int result = 1;
        for (int i = 0; i < exp; i++) {
            result = (result * 2) % 36;
        }
        return result;
    }

    /** Validates that an invoice number respects the ≤16 character Rule 46 limit. */
    public static void validateInvoiceNumber(String invoiceNumber) {
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new IllegalArgumentException("Invoice number is required");
        }
        if (invoiceNumber.length() > MAX_INVOICE_NUMBER_LENGTH) {
            throw new IllegalArgumentException("Invoice number exceeds the 16-character limit (Rule 46 CGST Rules, 2017): "
                    + invoiceNumber + " (" + invoiceNumber.length() + " chars)");
        }
    }

    /** True when the given HSN/SAC belongs to a service supply (SAC starts with 99). */
    public static boolean isSacCode(String code) {
        return code != null && code.trim().matches("^99\\d{2,6}$");
    }

    public static int determineHsnDigits(Double annualTurnover) {
        if (annualTurnover == null || annualTurnover < 5_00_00_000) {
            return 4;
        }
        return 6;
    }

    public static String[] getInvoiceCopies(String supplyType) {
        if ("SERVICES".equalsIgnoreCase(supplyType)) {
            return new String[]{"ORIGINAL", "DUPLICATE"};
        }
        return new String[]{"ORIGINAL", "DUPLICATE", "TRIPLICATE"};
    }

    public static String classifyInvoiceType(String buyerGstin) {
        if (buyerGstin != null && !buyerGstin.trim().isEmpty()
                && !"URP".equalsIgnoreCase(buyerGstin.trim())) {
            return "B2B";
        }
        return "B2C";
    }

    /**
     * True when the state component of an intra-state supply from this supplier state is UTGST
     * rather than SGST.
     *
     * UTGST applies only to supplies made within a Union Territory that has no legislature -
     * Andaman &amp; Nicobar Islands (35), Chandigarh (04), Dadra &amp; Nagar Haveli and Daman &amp; Diu
     * (26), Lakshadweep (31) and Ladakh (38). GSTN reports UTGST in the same "State/UT Tax"
     * bucket as SGST in every return and in the e-invoice schema, so the amount stays in the
     * state-tax fields - only the name on the face of the invoice changes.
     */
    public static boolean isUtgstState(String stateCode) {
        return stateCode != null && UTGST_STATES.contains(stateCode.trim());
    }

    /**
     * Classifies an inter-state B2C invoice as B2C LARGE (B2CL) when its taxable value
     * exceeds ₹1,00,000, or B2C SMALL (B2CS) otherwise. Only meaningful for B2C, inter-state.
     */
    public static boolean isB2cLarge(Boolean isInterState, Double taxableAmount) {
        return Boolean.TRUE.equals(isInterState)
                && taxableAmount != null
                && taxableAmount > B2C_LARGE_THRESHOLD;
    }

    public static double getB2cLargeThreshold() {
        return B2C_LARGE_THRESHOLD;
    }

    /**
     * Nil-rated / exempt / non-GST supply: a taxable line that carries no output tax.
     * Reported under GSTR-1 table 6C.
     */
    public static boolean isNilRated(Double taxableAmount, Double cgstAmount, Double sgstAmount, Double igstAmount) {
        if (taxableAmount == null || taxableAmount <= 0) return false;
        double tax = (cgstAmount != null ? cgstAmount : 0)
                + (sgstAmount != null ? sgstAmount : 0)
                + (igstAmount != null ? igstAmount : 0);
        return tax <= 0;
    }

    public static boolean requiresEInvoice(Double annualTurnover) {
        return annualTurnover != null && annualTurnover > E_INVOICE_THRESHOLD;
    }

    public static boolean requiresEWayBill(Double invoiceValue) {
        return invoiceValue != null && invoiceValue > E_WAYBILL_THRESHOLD;
    }

    public static String determineReverseCharge(String hsnCode, Double taxableValue) {
        if (hsnCode != null) {
            String hsn = hsnCode.trim();
            if (hsn.startsWith("9961") || hsn.startsWith("9962")) {
                return "Applicable - Service under RCM";
            }
            if ("5001".equals(hsn) || "5002".equals(hsn)) {
                return "Applicable - Goods under RCM";
            }
        }
        return "Not Applicable";
    }
}
