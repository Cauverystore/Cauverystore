package com.cauverystore.service;

import com.cauverystore.service.AgentWatchService.Decision;
import com.cauverystore.service.AgentWatchService.Facts;
import org.junit.jupiter.api.Test;

import static com.cauverystore.service.AgentWatchService.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentWatchRulesTest {

    private static Facts on(Double baseline, double price, Double mrp, Double cost) {
        return new Facts(true, null, baseline, price, mrp, false, true, cost);
    }

    @Test void outOfStockHides() {
        Decision d = decide(new Facts(true, null, 100.0, 209, 299.0, false, false, null));
        assertEquals("HIDE", d.action());
        assertEquals(false, d.active());
        assertEquals(HOLD_STOCK, d.hold());
    }

    @Test void backInStockRelists() {
        Decision d = decide(new Facts(false, HOLD_STOCK, 100.0, 209, 299.0, false, true, 100.0));
        assertEquals("RELIST", d.action());
        assertEquals(true, d.active());
        assertNull(d.hold());
    }

    @Test void backInStockWithBigCostJumpStaysHidden() {
        Decision d = decide(new Facts(false, HOLD_STOCK, 100.0, 209, 499.0, false, true, 130.0));
        assertEquals("REVIEW", d.action());
        assertEquals(false, d.active());
    }

    @Test void firstReadingNeverChangesPrice() {
        Decision d = decide(on(null, 250, 299.0, 100.0));
        assertEquals("BASELINE", d.action());
        assertNull(d.price());
        assertEquals(100.0, d.baseline());
    }

    @Test void sameCostLeavesManualPriceAlone() {
        Decision d = decide(on(100.0, 250, 299.0, 100.0));
        assertEquals("NONE", d.action());
        assertNull(d.price());
    }

    @Test void smallCostRiseReprices() {
        Decision d = decide(on(100.0, 209, 299.0, 110.0));
        assertEquals("PRICE", d.action());
        assertEquals(229.0, d.price());
        assertEquals(110.0, d.baseline());
    }

    @Test void costJumpOver15PercentNeedsReview() {
        assertEquals("REVIEW", decide(on(100.0, 209, 499.0, 116.0)).action());
    }

    @Test void neverPricesAboveMrp() {
        Decision d = decide(on(100.0, 209, 220.0, 110.0));
        assertEquals("REVIEW", d.action());
        assertNull(d.price());
    }

    @Test void priceMoveOver15PercentNeedsReview() {
        assertEquals("REVIEW", decide(on(100.0, 150, 499.0, 105.0)).action());
    }

    @Test void offersAndVariantsNeedReview() {
        assertEquals("REVIEW", decide(new Facts(true, null, 100.0, 209, 299.0, true, true, 110.0)).action());
    }

    @Test void reviewHoldNeverRelistsItself() {
        Decision d = decide(new Facts(false, HOLD_REVIEW, 100.0, 209, 299.0, false, true, 100.0));
        assertEquals("NONE", d.action());
        assertNull(d.active());
        assertEquals(HOLD_REVIEW, d.hold());
    }

    @Test void adminSwitchingOnAcceptsNewCost() {
        Decision d = decide(new Facts(true, HOLD_REVIEW, 100.0, 269, 299.0, false, true, 130.0));
        assertEquals("BASELINE", d.action());
        assertNull(d.hold());
        assertEquals(130.0, d.baseline());
    }
}
