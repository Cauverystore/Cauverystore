package com.cauverystore.service;

import com.cauverystore.entities.PlatformSetting;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * The order-value range in which cash on delivery is offered. The super admin edits it in
 * Platform Settings (keys cod.minOrderValue / cod.maxOrderValue); until it has been saved there,
 * the cod.min-order-value / cod.max-order-value properties apply (defaults 1000 / 3000).
 * A maximum of 0 means no upper limit; a minimum of 0 means no lower limit.
 */
@Service
public class CodLimitService {

    public static final String MIN_KEY = "cod.minOrderValue";
    public static final String MAX_KEY = "cod.maxOrderValue";
    /** Payment tab switches. Absent means enabled. */
    public static final String COD_ENABLED_KEY = "payment.enableCod";
    public static final String ONLINE_ENABLED_KEY = "payment.enableOnlinePayments";

    public record Limits(double min, double max) {
        public boolean allows(double total) {
            return total >= min && (max <= 0 || total <= max);
        }
    }

    private final PlatformSettingsService settings;
    private final double defaultMin;
    private final double defaultMax;

    public CodLimitService(PlatformSettingsService settings,
                           @Value("${cod.min-order-value:1000}") double defaultMin,
                           @Value("${cod.max-order-value:3000}") double defaultMax) {
        this.settings = settings;
        this.defaultMin = defaultMin;
        this.defaultMax = defaultMax;
    }

    public Limits current() {
        return new Limits(read(MIN_KEY, defaultMin), read(MAX_KEY, defaultMax));
    }

    public boolean codEnabled() {
        return readBool(COD_ENABLED_KEY);
    }

    public boolean onlineEnabled() {
        return readBool(ONLINE_ENABLED_KEY);
    }

    private boolean readBool(String key) {
        PlatformSetting s = settings.getSetting(key);
        if (s == null || s.getSettingValue() == null || s.getSettingValue().isBlank()) return true;
        return !"false".equalsIgnoreCase(s.getSettingValue().trim());
    }

    private double read(String key, double fallback) {
        PlatformSetting s = settings.getSetting(key);
        if (s == null || s.getSettingValue() == null || s.getSettingValue().isBlank()) return fallback;
        try {
            double v = Double.parseDouble(s.getSettingValue().trim());
            return v >= 0 ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Rejects a bad COD limit before it is saved; other keys are ignored. */
    public void validate(Map<String, String> updates) {
        if (updates.containsKey(COD_ENABLED_KEY) || updates.containsKey(ONLINE_ENABLED_KEY)) {
            boolean cod = updates.containsKey(COD_ENABLED_KEY)
                    ? !"false".equalsIgnoreCase(String.valueOf(updates.get(COD_ENABLED_KEY)).trim()) : codEnabled();
            boolean online = updates.containsKey(ONLINE_ENABLED_KEY)
                    ? !"false".equalsIgnoreCase(String.valueOf(updates.get(ONLINE_ENABLED_KEY)).trim()) : onlineEnabled();
            if (!cod && !online) {
                throw new IllegalArgumentException(
                        "Cash on delivery and online payments cannot both be disabled; customers would have no way to pay.");
            }
        }
        if (!updates.containsKey(MIN_KEY) && !updates.containsKey(MAX_KEY)) return;
        Limits now = current();
        double min = parse(updates, MIN_KEY, now.min());
        double max = parse(updates, MAX_KEY, now.max());
        if (max > 0 && min > max) {
            throw new IllegalArgumentException("The cash on delivery minimum cannot be more than the maximum.");
        }
    }

    private static double parse(Map<String, String> updates, String key, double current) {
        if (!updates.containsKey(key)) return current;
        String raw = updates.get(key);
        try {
            double v = Double.parseDouble(raw == null ? "" : raw.trim());
            if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cash on delivery limits must be a number, 0 or more.");
        }
    }
}
