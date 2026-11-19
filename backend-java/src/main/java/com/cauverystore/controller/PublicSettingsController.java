package com.cauverystore.controller;

import com.cauverystore.service.CodLimitService;
import com.cauverystore.service.PlatformSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class PublicSettingsController {

    private final PlatformSettingsService platformSettingsService;
    private final CodLimitService codLimitService;

    public PublicSettingsController(PlatformSettingsService platformSettingsService, CodLimitService codLimitService) {
        this.platformSettingsService = platformSettingsService;
        this.codLimitService = codLimitService;
    }

    /** The COD order-value range (max 0 = no upper limit) and whether COD / online payment are switched on. */
    @GetMapping("/cod")
    public ResponseEntity<Map<String, Object>> getCodLimits() {
        var l = codLimitService.current();
        return ResponseEntity.ok(Map.of(
                "minOrderValue", l.min(), "maxOrderValue", l.max(),
                "codEnabled", codLimitService.codEnabled(),
                "onlineEnabled", codLimitService.onlineEnabled()));
    }

    @GetMapping("/homepage")
    public ResponseEntity<Map<String, Boolean>> getHomepageSettings() {
        var setting = platformSettingsService.getSetting("homepage.brandStoresEnabled");
        boolean enabled = setting != null && Boolean.parseBoolean(setting.getSettingValue());
        return ResponseEntity.ok(Map.of("brandStoresEnabled", enabled));
    }
}
