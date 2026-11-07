package com.cauverystore.service;

import com.cauverystore.entities.PlatformSetting;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodLimitServiceTest {

    private final PlatformSettingsService settings = mock(PlatformSettingsService.class);
    private final CodLimitService service = new CodLimitService(settings, 1000, 3000);

    private static PlatformSetting setting(String key, String value) {
        PlatformSetting s = new PlatformSetting();
        s.setSettingKey(key);
        s.setSettingValue(value);
        return s;
    }

    @Test
    void usesPropertyDefaultsUntilSaved() {
        var l = service.current();
        assertEquals(1000, l.min());
        assertEquals(3000, l.max());
    }

    @Test
    void savedSettingsOverrideDefaults() {
        when(settings.getSetting(CodLimitService.MIN_KEY)).thenReturn(setting(CodLimitService.MIN_KEY, "800"));
        when(settings.getSetting(CodLimitService.MAX_KEY)).thenReturn(setting(CodLimitService.MAX_KEY, "2500"));
        var l = service.current();
        assertEquals(800, l.min());
        assertEquals(2500, l.max());
    }

    @Test
    void unreadableValueFallsBackToDefault() {
        when(settings.getSetting(CodLimitService.MAX_KEY)).thenReturn(setting(CodLimitService.MAX_KEY, "abc"));
        assertEquals(3000, service.current().max());
    }

    @Test
    void rangeIsInclusiveAndZeroMaxMeansNoUpperLimit() {
        var l = new CodLimitService.Limits(1000, 3000);
        assertFalse(l.allows(999.99));
        assertTrue(l.allows(1000));
        assertTrue(l.allows(3000));
        assertFalse(l.allows(3000.01));
        assertTrue(new CodLimitService.Limits(1000, 0).allows(1_000_000));
    }

    @Test
    void validateRejectsMinAboveMax() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validate(Map.of(CodLimitService.MIN_KEY, "4000")));
        assertThrows(IllegalArgumentException.class,
                () -> service.validate(Map.of(CodLimitService.MIN_KEY, "500", CodLimitService.MAX_KEY, "400")));
    }

    @Test
    void validateRejectsNonNumbersAndNegatives() {
        assertThrows(IllegalArgumentException.class, () -> service.validate(Map.of(CodLimitService.MAX_KEY, "lots")));
        assertThrows(IllegalArgumentException.class, () -> service.validate(Map.of(CodLimitService.MIN_KEY, "-1")));
    }

    @Test
    void validateAcceptsGoodValuesAndIgnoresOtherKeys() {
        assertDoesNotThrow(() -> service.validate(Map.of(CodLimitService.MIN_KEY, "1000", CodLimitService.MAX_KEY, "3000")));
        assertDoesNotThrow(() -> service.validate(Map.of("store.name", "x")));
    }
}
