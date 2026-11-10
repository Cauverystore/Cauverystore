package com.cauverystore.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Tds194oServiceTest {

    @Test
    void financialYearParses() {
        assertEquals(2026, Tds194oService.parseFinancialYear("2026-27"));
        assertEquals(2026, Tds194oService.parseFinancialYear("2026"));
    }

    @Test
    void aNonConsecutiveFinancialYearIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Tds194oService.parseFinancialYear("2026-29"));
        assertThrows(IllegalArgumentException.class, () -> Tds194oService.parseFinancialYear("26-27"));
        assertThrows(IllegalArgumentException.class, () -> Tds194oService.parseFinancialYear(null));
    }

    @Test
    void proprietorsAndHufAreIndividualsForTheLimit_firmsAndCompaniesAreNot() {
        assertTrue(Tds194oService.isIndividualOrHuf("Sole Proprietorship"));
        assertTrue(Tds194oService.isIndividualOrHuf("Hindu Undivided Family"));
        assertTrue(Tds194oService.isIndividualOrHuf("HUF"));
        assertFalse(Tds194oService.isIndividualOrHuf("Partnership"));
        assertFalse(Tds194oService.isIndividualOrHuf("LLP"));
        assertFalse(Tds194oService.isIndividualOrHuf("Private Limited"));
    }
}
