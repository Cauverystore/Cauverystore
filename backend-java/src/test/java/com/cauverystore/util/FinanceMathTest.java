package com.cauverystore.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The worked example in the finance design: a Rs.1,000 order at 10% commission with a Rs.10 handling fee. */
class FinanceMathTest {

    @Test
    void worksTheThousandRupeeExample() {
        long tcs = FinanceMath.estimateTcs(100_000);
        assertEquals(424, tcs);
        FinanceMath.Breakdown b = FinanceMath.breakdown(100_000, 10_000, 1_000, tcs);
        assertEquals(2_623, b.paymentFees());   // 23.60 gateway + 2.63 transfer
        assertEquals(1_980, b.gstOnFees());     // 18% of 110.00
        assertEquals(83_973, b.net());          // Rs.839.73
    }

    @Test
    void everyPaiseIsAccountedFor() {
        FinanceMath.Breakdown b = FinanceMath.breakdown(123_457, 12_346, 1_000, 500);
        assertEquals(b.sale() - b.commission() - b.handling() - b.paymentFees() - b.gstOnFees() - b.tcs(), b.net());
    }

    @Test
    void proratesARefundAndNeverReversesMoreThanTheOrder() {
        assertEquals(5_000, FinanceMath.prorate(10_000, 50_000, 100_000));
        assertEquals(10_000, FinanceMath.prorate(10_000, 250_000, 100_000));
        assertEquals(0, FinanceMath.prorate(10_000, 5_000, 0));
    }

    @Test
    void convertsRupeesToPaiseWithoutFloatingPointDrift() {
        assertEquals(1_999, FinanceMath.paise(19.99));
        assertEquals(29, FinanceMath.paise(0.29));
        assertEquals(1_005, FinanceMath.paise(10.05));
        assertEquals(10.05, FinanceMath.rupees(1_005), 0.0);
    }
}
