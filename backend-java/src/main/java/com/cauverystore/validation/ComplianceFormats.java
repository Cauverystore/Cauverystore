package com.cauverystore.validation;

import com.cauverystore.util.GstComplianceUtil;

import java.util.regex.Pattern;

/**
 * Format checks for the identifiers a product listing carries.
 *
 * These prove a number is well-formed, not that it was issued or is still valid. Only the
 * issuing body (GSTN, FSSAI, BIS, CDSCO) can say that. A blank value passes every check here:
 * whether a field is required is the compliance engine's decision, not the format's.
 */
public final class ComplianceFormats {

    private ComplianceFormats() {}

    /** HSN on a goods invoice: 4, 6 or 8 digits. */
    private static final Pattern HSN = Pattern.compile("^(\\d{4}|\\d{6}|\\d{8})$");
    /** FSSAI licence or registration number: 14 digits. */
    private static final Pattern FSSAI = Pattern.compile("^\\d{14}$");
    /**
     * BIS: a Compulsory Registration Scheme number (R- and 8 digits) or an ISI mark licence
     * (CM/L- and 7 to 10 digits).
     */
    private static final Pattern BIS = Pattern.compile("^(R-\\d{8}|CM/L-\\d{7,10})$");
    /**
     * CDSCO registration and licence numbers have no single published format (they differ by
     * licence form and by state), so this only rejects obvious junk: 4 to 40 characters of
     * letters, digits and the separators these numbers use.
     */
    private static final Pattern CDSCO = Pattern.compile("^[A-Z0-9][A-Z0-9/\\-. ]{3,39}$");

    public static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static String clean(String s) { return s.trim().toUpperCase(); }

    public static boolean isValidGstin(String gstin) {
        if (isBlank(gstin)) return true;
        try {
            GstComplianceUtil.validateGstin(gstin);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return isGstStateCode(gstin.trim().substring(0, 2));
    }

    /**
     * A GSTN state code: 01 to 38 (Census 2011 numbering, as GSTN uses), 97 Other Territory or
     * 99 Centre Jurisdiction. 25 and 28 are kept because older registrations still carry them.
     */
    public static boolean isGstStateCode(String code) {
        if (code == null || !code.matches("\\d{2}")) return false;
        int n = Integer.parseInt(code);
        return (n >= 1 && n <= 38) || n == 97 || n == 99;
    }

    public static boolean isValidHsn(String hsn) {
        return isBlank(hsn) || HSN.matcher(hsn.replaceAll("\\s", "")).matches();
    }

    public static boolean isValidFssai(String fssai) {
        return isBlank(fssai) || FSSAI.matcher(fssai.replaceAll("\\s", "")).matches();
    }

    public static boolean isValidBis(String bis) {
        return isBlank(bis) || BIS.matcher(clean(bis).replaceAll("\\s", "")).matches();
    }

    public static boolean isValidCdsco(String cdsco) {
        return isBlank(cdsco) || CDSCO.matcher(clean(cdsco)).matches();
    }
}
