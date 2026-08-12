package com.cauverystore.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GstComplianceUtilTest {

    @Test
    void unionTerritoriesWithoutLegislatureAreUtgst() {
        assertTrue(GstComplianceUtil.isUtgstState("04")); // Chandigarh
        assertTrue(GstComplianceUtil.isUtgstState("26")); // DNH & Daman-Diu
        assertTrue(GstComplianceUtil.isUtgstState("31")); // Lakshadweep
        assertTrue(GstComplianceUtil.isUtgstState("35")); // Andaman & Nicobar
        assertTrue(GstComplianceUtil.isUtgstState("38")); // Ladakh
    }

    @Test
    void statesAndUnionTerritoriesWithLegislaturesAreNotUtgst() {
        assertFalse(GstComplianceUtil.isUtgstState("01")); // Jammu & Kashmir
        assertFalse(GstComplianceUtil.isUtgstState("07")); // Delhi
        assertFalse(GstComplianceUtil.isUtgstState("34")); // Puducherry
        assertFalse(GstComplianceUtil.isUtgstState("33")); // Tamil Nadu
        assertFalse(GstComplianceUtil.isUtgstState("09")); // Uttar Pradesh
    }

    @Test
    void blankAndNullCodesAreNotUtgst() {
        assertFalse(GstComplianceUtil.isUtgstState(null));
        assertFalse(GstComplianceUtil.isUtgstState(""));
        assertFalse(GstComplianceUtil.isUtgstState("  "));
        assertFalse(GstComplianceUtil.isUtgstState("abc"));
    }

    @Test
    void utgstDetectionIgnoresSurroundingWhitespace() {
        assertTrue(GstComplianceUtil.isUtgstState(" 38 "));
    }

    @org.junit.jupiter.api.Test
    void aRealGstinPassesItsCheckDigit() {
        // 27AAPFU0939F1ZV is the example used throughout the GSTN documentation. Checking against
        // a number somebody else published is the point: an algorithm verified only against
        // GSTINs it generated itself proves nothing except that it is self-consistent.
        assertTrue(GstComplianceUtil.isGstinChecksumValid("27AAPFU0939F1ZV"));
        assertDoesNotThrow(() -> GstComplianceUtil.validateGstin("27AAPFU0939F1ZV"));
    }

    @org.junit.jupiter.api.Test
    void aNumberThatMerelyLooksLikeAGstinIsRejected() {
        // This is the shape of thing that used to pass: correct length, correct character classes,
        // entirely invented. Seller approval gates activation on a verified GSTIN, and with no
        // GSP contract the simulator behind that gate says yes to whatever validateGstin accepts.
        assertFalse(GstComplianceUtil.isGstinChecksumValid("33ABCDE1234F1Z5"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GstComplianceUtil.validateGstin("33ABCDE1234F1Z5"));
        assertTrue(e.getMessage().contains("check digit"), e.getMessage());
    }

    @org.junit.jupiter.api.Test
    void aSingleMistypedCharacterIsCaught() {
        // The everyday case, and the one this is really for.
        assertFalse(GstComplianceUtil.isGstinChecksumValid("27AAPFU0939F1ZW"));
        assertFalse(GstComplianceUtil.isGstinChecksumValid("27AAPFU0939F1XV"));
    }

    @org.junit.jupiter.api.Test
    void transposingTwoCharactersIsCaught() {
        // Why the weights alternate and the quotient is added as well as the remainder. A plain
        // sum would give the same total for a swapped pair and wave this through.
        assertTrue(GstComplianceUtil.isGstinChecksumValid("29AAGCB7383J1Z4"));
        assertFalse(GstComplianceUtil.isGstinChecksumValid("29AAGCB3783J1Z4"));
    }

    @org.junit.jupiter.api.Test
    void theSameNumberInAnotherStateIsADifferentCheckDigit() {
        // The state code is part of what is signed, so a GSTIN cannot be reused across states by
        // changing the first two digits and leaving the rest alone.
        assertTrue(GstComplianceUtil.isGstinChecksumValid("33AAGCB7383J1ZF"));
        assertFalse(GstComplianceUtil.isGstinChecksumValid("33AAGCB7383J1Z4"));
    }

    @org.junit.jupiter.api.Test
    void structureOnlyValidationStillAcceptsAWellFormedButImpossibleNumber() {
        // Used when raising an invoice for an order already paid for. Withholding the document a
        // customer is owed over somebody else's typo would be the wrong trade there.
        assertDoesNotThrow(() -> GstComplianceUtil.validateGstinFormat("33ABCDE1234F1Z5"));
        assertThrows(IllegalArgumentException.class,
                () -> GstComplianceUtil.validateGstinFormat("NOT-A-GSTIN"));
    }

    @org.junit.jupiter.api.Test
    void lowercaseAndSurroundingSpaceAreAccepted() {
        // People paste these out of emails and certificates.
        assertTrue(GstComplianceUtil.isGstinChecksumValid("  27aapfu0939f1zv  "));
    }

    @org.junit.jupiter.api.Test
    void nonsenseDoesNotThrowFromTheChecksumItself() {
        // isGstinChecksumValid answers a question; it is validateGstin that refuses. A null or a
        // stray character must come back false rather than blow up a caller that only asked.
        assertFalse(GstComplianceUtil.isGstinChecksumValid(null));
        assertFalse(GstComplianceUtil.isGstinChecksumValid("short"));
        assertFalse(GstComplianceUtil.isGstinChecksumValid("27AAPFU0939F1Z*"));
    }
}
