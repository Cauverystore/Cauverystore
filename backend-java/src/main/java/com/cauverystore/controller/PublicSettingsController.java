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

    /** The order-value range in which cash on delivery is offered (max 0 = no upper limit). */
    @GetMapping("/cod")
    public ResponseEntity<Map<String, Double>> getCodLimits() {
        var l = codLimitService.current();
        return ResponseEntity.ok(Map.of("minOrderValue", l.min(), "maxOrderValue", l.max()));
    }

    @GetMapping("/homepage")
    public ResponseEntity<Map<String, Boolean>> getHomepageSettings() {
        var setting = platformSettingsService.getSetting("homepage.brandStoresEnabled");
        boolean enabled = setting != null && Boolean.parseBoolean(setting.getSettingValue());
        return ResponseEntity.ok(Map.of("brandStoresEnabled", enabled));
    }
}
