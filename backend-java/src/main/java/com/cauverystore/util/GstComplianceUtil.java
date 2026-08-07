package com.cauverystore.util;

import java.util.regex.Pattern;

public class GstComplianceUtil {

    private static final Pattern GSTIN_PATTERN =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");

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
