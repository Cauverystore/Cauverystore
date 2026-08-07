package com.cauverystore.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
