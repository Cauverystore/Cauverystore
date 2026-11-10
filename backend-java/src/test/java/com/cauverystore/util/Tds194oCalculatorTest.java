package com.cauverystore.util;

import com.cauverystore.util.Tds194oCalculator.Entry;
import com.cauverystore.util.Tds194oCalculator.Participant;
import com.cauverystore.util.Tds194oCalculator.Result;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Section 194-O / 393(1) Sl. 8(v): 0.1% from 1-10-2024, 5% with no PAN, Rs 5 lakh limit for individuals. */
class Tds194oCalculatorTest {

    private static final Participant INDIVIDUAL_WITH_PAN = new Participant(true, true);
    private static final Participant COMPANY_WITH_PAN = new Participant(false, true);
    private static final Participant INDIVIDUAL_NO_PAN = new Participant(true, false);

    private static Entry sale(long id, String date, double taxable) {
        return new Entry(id, null, LocalDate.parse(date), taxable, false);
    }

    private static Entry ret(long id, long of, String date, double taxable) {
        return new Entry(id, of, LocalDate.parse(date), taxable, true);
    }

    @Test
    void individualWithPanUnderTheLimitPaysNothing() {
        Result r = Tds194oCalculator.compute(INDIVIDUAL_WITH_PAN, List.of(
                sale(1, "2026-05-10", 200_000), sale(2, "2026-06-10", 300_000)));   // exactly 5,00,000
        assertEquals(0.0, r.tds(), 0.001);
        assertEquals(500_000.0, r.grossSales(), 0.001);
    }

    @Test
    void theSaleThatCrossesTheLimitIsTaxedInFull_andEarlierOnesAreNotReopened() {
        Result r = Tds194oCalculator.compute(INDIVIDUAL_WITH_PAN, List.of(
                sale(1, "2026-05-10", 450_000),
                sale(2, "2026-06-10", 100_000)));          // cumulative 5,50,000 > 5,00,000
        assertEquals(0.0, r.lines().get(0).tds(), 0.001);
        assertEquals(100.0, r.lines().get(1).tds(), 0.001);   // 0.1% of 1,00,000
        assertNotNull(r.lines().get(1).note());
        assertEquals(100.0, r.tds(), 0.001);
    }

    @Test
    void salesAfterTheLimitAreTaxedAtPointOnePercent() {
        Result r = Tds194oCalculator.compute(INDIVIDUAL_WITH_PAN, List.of(
                sale(1, "2026-05-10", 600_000), sale(2, "2026-06-10", 50_000)));
        assertEquals(600.0, r.lines().get(0).tds(), 0.001);
        assertEquals(50.0, r.lines().get(1).tds(), 0.001);
    }

    @Test
    void theLimitResetsEachFinancialYear() {
        Result r = Tds194oCalculator.compute(INDIVIDUAL_WITH_PAN, List.of(
                sale(1, "2027-03-31", 500_000),     // FY 2026-27, exactly at the limit
                sale(2, "2027-04-01", 500_000)));   // FY 2027-28, a fresh limit
        assertEquals(0.0, r.tds(), 0.001);
    }

    @Test
    void aCompanyGetsNoExemption() {
        Result r = Tds194oCalculator.compute(COMPANY_WITH_PAN, List.of(sale(1, "2026-05-10", 10_000)));
        assertEquals(10.0, r.tds(), 0.001);   // 0.1% of 10,000 from the first rupee
    }

    @Test
    void noPanMeansFivePercentFromTheFirstRupee_evenForAnIndividual() {
        Result r = Tds194oCalculator.compute(INDIVIDUAL_NO_PAN, List.of(sale(1, "2026-05-10", 10_000)));
        assertEquals(500.0, r.tds(), 0.001);
        assertEquals(5.0, r.lines().get(0).ratePercent(), 0.0);
    }

    @Test
    void theOldOnePercentRateAppliesToSalesBeforeOctober2024() {
        Result r = Tds194oCalculator.compute(COMPANY_WITH_PAN, List.of(
                sale(1, "2024-09-30", 100_000), sale(2, "2024-10-01", 100_000)));
        assertEquals(1000.0, r.lines().get(0).tds(), 0.001);   // 1%
        assertEquals(100.0, r.lines().get(1).tds(), 0.001);    // 0.1%
    }

    @Test
    void aReturnGivesBackTaxInProportion() {
        Result r = Tds194oCalculator.compute(COMPANY_WITH_PAN, List.of(
                sale(1, "2026-05-10", 100_000),                 // tax 100
                ret(2, 1, "2026-05-20", 25_000)));              // a quarter returned
        assertEquals(-25.0, r.lines().get(1).tds(), 0.001);
        assertEquals(75.0, r.tds(), 0.001);
        assertEquals(75_000.0, r.netSales(), 0.001);
    }

    @Test
    void returningAnExemptSaleGivesBackNothing() {
        Result r = Tds194oCalculator.compute(INDIVIDUAL_WITH_PAN, List.of(
                sale(1, "2026-05-10", 100_000), ret(2, 1, "2026-05-11", 100_000)));
        assertEquals(0.0, r.tds(), 0.001);
    }

    @Test
    void aReturnWhoseSaleIsOutsideThePeriodIsFlaggedNotGuessed() {
        Result r = Tds194oCalculator.compute(COMPANY_WITH_PAN, List.of(ret(9, 1, "2026-05-20", 5_000)));
        assertEquals(0.0, r.tds(), 0.001);
        assertTrue(r.lines().get(0).note().contains("not in this period"));
    }

    @Test
    void aSameDayReturnStillFindsItsSale() {
        Result r = Tds194oCalculator.compute(COMPANY_WITH_PAN, List.of(
                ret(2, 1, "2026-05-10", 100_000), sale(1, "2026-05-10", 100_000)));   // given out of order
        assertEquals(0.0, r.tds(), 0.001);
    }

    @Test
    void financialYearStartsInApril() {
        assertEquals(2026, Tds194oCalculator.financialYearStart(LocalDate.of(2026, 4, 1)));
        assertEquals(2025, Tds194oCalculator.financialYearStart(LocalDate.of(2026, 3, 31)));
    }

    @Test
    void aSaleBeforeTheLawExistedHasNoRate_ratherThanAGuess() {
        assertThrows(IllegalStateException.class, () -> Tds194oCalculator.compute(COMPANY_WITH_PAN,
                List.of(sale(1, "2020-09-30", 1_000))));
    }
}
