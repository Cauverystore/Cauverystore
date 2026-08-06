package com.cauverystore.util;

import java.util.regex.Pattern;

public class GstComplianceUtil {

    private static final Pattern GSTIN_PATTERN =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");

    private static final String BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final double E_INVOICE_THRESHOLD = 5_00_00_000;
    private static final double E_WAYBILL_THRESHOLD = 1_00_000;

    /** Maximum invoice number length permitted under Rule 46 (16 characters). */
    public static final int MAX_INVOICE_NUMBER_LENGTH = 16;

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
