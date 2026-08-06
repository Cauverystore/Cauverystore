package com.cauverystore.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GstComplianceUtilTest {

    // Checksum-verified GSTINs (15 chars: 2-digit state + 10-char PAN block + entity + Z + check digit)
    private static final String VALID_TN = "33ABCDE1234F1ZA";
    private static final String VALID_MH = "27ABCDE1234F1ZW";

    @Test
    void validateGstin_shouldAcceptWellFormedGstin() {
        assertDoesNotThrow(() -> GstComplianceUtil.validateGstin(VALID_TN));
        assertDoesNotThrow(() -> GstComplianceUtil.validateGstin(VALID_MH));
    }

    @Test
    void validateGstin_shouldRejectMalformedGstin() {
        assertThrows(IllegalArgumentException.class, () -> GstComplianceUtil.validateGstin(""));
        assertThrows(IllegalArgumentException.class, () -> GstComplianceUtil.validateGstin(null));
        assertThrows(IllegalArgumentException.class, () -> GstComplianceUtil.validateGstin("33ABCDE1234F1Z"));   // 14 chars
        assertThrows(IllegalArgumentException.class, () -> GstComplianceUtil.validateGstin("33ABCDE1234F1ZAA")); // 16 chars
        assertThrows(IllegalArgumentException.class, () -> GstComplianceUtil.validateGstin("ABCDE1234F1ZA"));    // no state code
        assertThrows(IllegalArgumentException.class, () -> GstComplianceUtil.validateGstin("33ABCDE1234F0Z1"));  // entity slot not 1-9A-Z
        assertThrows(IllegalArgumentException.class, () -> GstComplianceUtil.validateGstin("33ABCDE1234F1ZA!")); // illegal trailing char
    }

    @Test
    void hasValidCheckDigit_shouldVerifyBase36Checksum() {
        assertTrue(GstComplianceUtil.hasValidCheckDigit(VALID_TN));
        assertTrue(GstComplianceUtil.hasValidCheckDigit(VALID_MH));
        // Same PAN/state block but a wrong checksum character
        assertFalse(GstComplianceUtil.hasValidCheckDigit("33ABCDE1234F1ZB"));
        assertFalse(GstComplianceUtil.hasValidCheckDigit("27ABCDE1234F1ZA"));
        assertFalse(GstComplianceUtil.hasValidCheckDigit("garbage"));
    }

    @Test
    void validateInvoiceNumber_shouldEnforceRule46Max16Chars() {
        assertDoesNotThrow(() -> GstComplianceUtil.validateInvoiceNumber("CS2608/00001"));     // 12 chars
        assertDoesNotThrow(() -> GstComplianceUtil.validateInvoiceNumber("CS2608/00001/001"));  // 16 chars exactly
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> GstComplianceUtil.validateInvoiceNumber("CS2608/00001/0001"));           // 17 chars
        assertTrue(ex.getMessage().contains("16-character"));
        assertThrows(IllegalArgumentException.class, () -> GstComplianceUtil.validateInvoiceNumber(""));
        assertThrows(IllegalArgumentException.class, () -> GstComplianceUtil.validateInvoiceNumber(null));
    }

    @Test
    void isSacCode_shouldIdentifyServiceCodes() {
        assertTrue(GstComplianceUtil.isSacCode("996511"));
        assertTrue(GstComplianceUtil.isSacCode("9983"));
        assertFalse(GstComplianceUtil.isSacCode("610910"));
        assertFalse(GstComplianceUtil.isSacCode("996"));
        assertFalse(GstComplianceUtil.isSacCode(null));
    }

    @Test
    void classifyInvoiceType_shouldDistinguishB2bAndB2c() {
        assertEquals("B2B", GstComplianceUtil.classifyInvoiceType(VALID_TN));
        assertEquals("B2B", GstComplianceUtil.classifyInvoiceType("33ABCDE1234F1ZA"));
        assertEquals("B2C", GstComplianceUtil.classifyInvoiceType("URP"));
        assertEquals("B2C", GstComplianceUtil.classifyInvoiceType(null));
        assertEquals("B2C", GstComplianceUtil.classifyInvoiceType(""));
        assertEquals("B2C", GstComplianceUtil.classifyInvoiceType("   "));
    }

    @Test
    void requiresEInvoice_shouldUseFiveCroreThreshold() {
        assertFalse(GstComplianceUtil.requiresEInvoice(5_00_00_000.0));  // exactly at threshold -> not required
        assertFalse(GstComplianceUtil.requiresEInvoice(1_00_00_000.0));
        assertFalse(GstComplianceUtil.requiresEInvoice(null));
        assertTrue(GstComplianceUtil.requiresEInvoice(5_00_00_000.01));
        assertTrue(GstComplianceUtil.requiresEInvoice(8_00_00_000.0));
    }

    @Test
    void requiresEWayBill_shouldUseOneLakhThreshold() {
        assertFalse(GstComplianceUtil.requiresEWayBill(1_00_000.0));
        assertFalse(GstComplianceUtil.requiresEWayBill(50_000.0));
        assertFalse(GstComplianceUtil.requiresEWayBill(null));
        assertTrue(GstComplianceUtil.requiresEWayBill(1_00_001.0));
        assertTrue(GstComplianceUtil.requiresEWayBill(2_50_000.0));
    }

    @Test
    void determineHsnDigits_shouldReturnSixDigitsAboveFiveCrore() {
        assertEquals(4, GstComplianceUtil.determineHsnDigits(null));
        assertEquals(4, GstComplianceUtil.determineHsnDigits(1_00_00_000.0));
        assertEquals(6, GstComplianceUtil.determineHsnDigits(8_00_00_000.0));
    }
}
