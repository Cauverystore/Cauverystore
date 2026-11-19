package com.cauverystore.service;

import com.cauverystore.entities.GstConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommissionInvoiceSchedulerTest {

    private final CommissionInvoiceService service = mock(CommissionInvoiceService.class);

    private CommissionInvoiceScheduler at(String instant) {
        return new CommissionInvoiceScheduler(service,
                Clock.fixed(Instant.parse(instant), CommissionInvoiceScheduler.INDIA));
    }

    private void storeGstin(String gstin) {
        GstConfiguration c = new GstConfiguration();
        c.setGstin(gstin);
        when(service.marketplaceConfig()).thenReturn(java.util.Optional.of(c));
    }

    @Test
    void billsThePreviousMonth() {
        storeGstin("33AABCC1234D1Z5");
        when(service.generateForPeriod(anyString(), anyString()))
                .thenReturn(Map.of("issued", List.of("CI-1"), "skipped", List.of()));

        at("2026-12-15T22:30:00Z").billLastMonth();   // 16 Dec, 4am in India

        verify(service).generateForPeriod("112026", "system");
    }

    @Test
    void januaryBillsDecemberOfThePreviousYear() {
        storeGstin("33AABCC1234D1Z5");
        when(service.generateForPeriod(anyString(), anyString())).thenReturn(Map.of());

        at("2027-01-15T22:30:00Z").billLastMonth();

        verify(service).generateForPeriod("122026", "system");
    }

    @Test
    void usesIndiaTimeNotServerTime() {
        storeGstin("33AABCC1234D1Z5");
        when(service.generateForPeriod(anyString(), anyString())).thenReturn(Map.of());

        // 1 Dec 00:30 in India is still 30 Nov in UTC; November's bill would be wrong here.
        at("2026-11-30T19:00:00Z").billLastMonth();

        verify(service).generateForPeriod("112026", "system");
    }

    @Test
    void doesNothingWithoutAStoreGstin() {
        storeGstin(null);

        Map<String, Object> result = at("2026-12-15T22:30:00Z").billMonth(YearMonth.of(2026, 11));

        assertEquals("Store has no GSTIN", result.get("skipped"));
        verify(service, never()).generateForPeriod(anyString(), anyString());
    }

    @Test
    void doesNothingWithBlankGstinOrNoConfiguration() {
        storeGstin("  ");
        at("2026-12-15T22:30:00Z").billLastMonth();

        when(service.marketplaceConfig()).thenReturn(java.util.Optional.empty());
        at("2026-12-15T22:30:00Z").billLastMonth();

        verify(service, never()).generateForPeriod(anyString(), anyString());
    }

    @Test
    void anErrorIsLoggedNotThrown() {
        storeGstin("33AABCC1234D1Z5");
        when(service.generateForPeriod(anyString(), anyString())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> at("2026-12-15T22:30:00Z").billLastMonth());
    }
}
