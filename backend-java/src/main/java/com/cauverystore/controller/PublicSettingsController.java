package com.cauverystore.controller;

import com.cauverystore.service.PlatformSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class PublicSettingsController {

    private final PlatformSettingsService platformSettingsService;
    public PublicSettingsController(PlatformSettingsService platformSettingsService) {
        this.platformSettingsService = platformSettingsService;
    }

    @GetMapping("/homepage")
    public ResponseEntity<Map<String, Boolean>> getHomepageSettings() {
        var setting = platformSettingsService.getSetting("homepage.brandStoresEnabled");
        boolean enabled = setting != null && Boolean.parseBoolean(setting.getSettingValue());
        return ResponseEntity.ok(Map.of("brandStoresEnabled", enabled));
    }
}
