package com.cauverystore.util;

import java.util.regex.Pattern;

public class GstComplianceUtil {

    private static final Pattern GSTIN_PATTERN =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");

    private static final double E_INVOICE_THRESHOLD = 5_00_00_000;
    private static final double E_WAYBILL_THRESHOLD = 1_00_000;

    public static void validateGstin(String gstin) {
        if (gstin == null || gstin.trim().isEmpty()) {
            throw new IllegalArgumentException("GSTIN is required");
        }
        if (!GSTIN_PATTERN.matcher(gstin.trim().toUpperCase()).matches()) {
            throw new IllegalArgumentException("Invalid GSTIN format: " + gstin
                    + ". Expected 15 characters: 2-digit state code + 10-digit PAN + 1 entity code + 1 blank + 1 checksum");
        }
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
