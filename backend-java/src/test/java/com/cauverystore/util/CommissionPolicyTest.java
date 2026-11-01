package com.cauverystore.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The commission figures a seller is promised. These are the numbers in the seller terms, so a
 * change here is a change to what sellers were told.
 */
class CommissionPolicyTest {

    @Test
    void essentialsPayNoCommission() {
        assertEquals(0.0, CommissionPolicy.percentFor(CommissionPolicy.Tier.ESSENTIALS, 300), 0.0);
    }

    @Test
    void standardPaysTenPercent() {
        assertEquals(10.0, CommissionPolicy.percentFor(CommissionPolicy.Tier.STANDARD, 800), 0.0);
    }

    @Test
    void highValuePaysTwentyOnlyFromTheFloorUp() {
        assertEquals(20.0, CommissionPolicy.percentFor(CommissionPolicy.Tier.HIGH_VALUE, 1000), 0.0);
        assertEquals(20.0, CommissionPolicy.percentFor(CommissionPolicy.Tier.HIGH_VALUE, 5000), 0.0);
        assertEquals(10.0, CommissionPolicy.percentFor(CommissionPolicy.Tier.HIGH_VALUE, 999.99), 0.0);
    }

    @Test
    void mixedOrderIsWeightedByItemValue() {
        // 1,000 at 20% and 1,000 at 0% -> 10%
        assertEquals(10.0, CommissionPolicy.blendedPercent(
                new double[]{1000, 1000}, new double[]{20, 0}, 5), 0.0);
        // 300 at 10% and 900 at 20% -> 17.5%
        assertEquals(17.5, CommissionPolicy.blendedPercent(
                new double[]{300, 900}, new double[]{10, 20}, 5), 0.0);
    }

    @Test
    void blendFallsBackWhenThereIsNothingToWeigh() {
        assertEquals(7.0, CommissionPolicy.blendedPercent(new double[]{}, new double[]{}, 7), 0.0);
        assertEquals(7.0, CommissionPolicy.blendedPercent(new double[]{0}, new double[]{20}, 7), 0.0);
        assertEquals(7.0, CommissionPolicy.blendedPercent(new double[]{100}, new double[]{}, 7), 0.0);
    }

    @Test
    void paymentFeesMatchRazorpaysOwnWorkedExample() {
        // Razorpay's docs: 1,000 payment, 900 sent to the seller -> gateway 23.60, transfer 2.66
        assertEquals(23.60, CommissionPolicy.gatewayFee(1000), 0.001);
        assertEquals(2.66, CommissionPolicy.transferFee(900), 0.001);
    }

    @Test
    void sellerKeepsWhatThePlanPromisesOnAThousandRupeeItem() {
        // Essentials, 15% margin (150): keeps about 113
        var essentials = CommissionPolicy.settle(1000, 0, 10);
        assertEquals(26.52, essentials.gatewayFee() + essentials.transferFee(), 0.011);

        // The seller's own cost is 850 on a 150 margin, so what they keep of the margin is
        // the net payment less that cost.
        assertEquals(113.48, essentials.sellerNet() - 850, 0.02);

        // Standard, 30% margin (300): commission 100 -> keeps about 164
        var standard = CommissionPolicy.settle(1000, 100, 10);
        assertEquals(163.77, standard.sellerNet() - 700, 0.02);

        // High value, 60% margin (600): commission 200 -> keeps about 364
        var high = CommissionPolicy.settle(1000, 200, 10);
        assertEquals(364.07, high.sellerNet() - 400, 0.02);
    }

    @Test
    void sellerIsPaidEightDaysAfterDelivery() {
        assertEquals(LocalDate.of(2026, 11, 9), CommissionPolicy.payableFrom(LocalDate.of(2026, 11, 1)));
        assertNull(CommissionPolicy.payableFrom(null));
    }

    @Test
    void aLedgerLineTakesGstOnFeesAndTcsBeforeThePayout() {
        CommissionPolicy.LedgerLine l = CommissionPolicy.ledgerLine(10_000.0, 1_000.0, 10.0, 18.0, 50.0);
        assertEquals(181.80, l.gstOnFees(), 0.001);
        assertEquals(8_496.36, l.payable(), 0.001);
        double back = l.payable() + l.commission() + l.handlingFee() + l.gstOnFees()
                + l.gatewayFee() + l.transferFee() + l.tcs();
        assertEquals(l.orderValue(), back, 0.011, "every rupee of the sale is accounted for");
    }
}
