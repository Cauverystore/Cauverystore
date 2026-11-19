package com.cauverystore.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class StatutoryRatesTest {

    @Test
    void tcsWasOnePercentUntilNinthJuly2024() {
        assertEquals(1.0, StatutoryRates.tcsRateOn(LocalDate.of(2024, 7, 9)), 0.0001);
        assertEquals(1.0, StatutoryRates.tcsRateOn(LocalDate.of(2018, 10, 1)), 0.0001);
    }

    @Test
    void tcsIsHalfAPercentFromTenthJuly2024() {
        assertEquals(0.5, StatutoryRates.tcsRateOn(LocalDate.of(2024, 7, 10)), 0.0001);
        assertEquals(0.5, StatutoryRates.tcsRateOn(LocalDate.of(2026, 11, 1)), 0.0001);
    }

    @Test
    void everyPeriodNamesItsSource() {
        assertTrue(StatutoryRates.periodOn(StatutoryRates.TCS_S52, LocalDate.of(2026, 11, 1))
                .orElseThrow().source().contains("15/2024"));
    }

    @Test
    void aDateBeforeTcsExistedHasNoRate_ratherThanAGuess() {
        assertThrows(IllegalStateException.class,
                () -> StatutoryRates.tcsRateOn(LocalDate.of(2018, 9, 30)));
    }

    @Test
    void tds194oIsOnePercentUntilSeptember2024_thenPointOnePercent() {
        assertEquals(1.0, StatutoryRates.tds194oRateOn(LocalDate.of(2024, 9, 30), true), 0.0001);
        assertEquals(0.1, StatutoryRates.tds194oRateOn(LocalDate.of(2024, 10, 1), true), 0.0001);
        assertEquals(0.1, StatutoryRates.tds194oRateOn(LocalDate.of(2026, 11, 1), true), 0.0001);
    }

    @Test
    void tds194oWithoutPanIsFivePercent() {
        assertEquals(5.0, StatutoryRates.tds194oRateOn(LocalDate.of(2026, 11, 1), false), 0.0001);
    }

    @Test
    void tds194oExemptLimitIsFiveLakh() {
        assertEquals(500_000.0, StatutoryRates.tds194oExemptLimitOn(LocalDate.of(2026, 11, 1)), 0.0);
    }
}
