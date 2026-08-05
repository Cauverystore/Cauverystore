package com.cauverystore.controller;

import com.cauverystore.entities.*;
import com.cauverystore.service.ContentService;
import com.cauverystore.service.PlatformSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/content")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminContentController {
    private final ContentService contentService;
    private final PlatformSettingsService platformSettingsService;
    public AdminContentController(ContentService contentService, PlatformSettingsService platformSettingsService) {
        this.contentService = contentService;
        this.platformSettingsService = platformSettingsService;
    }

    @GetMapping("/homepage-settings")
    public ResponseEntity<Map<String, Boolean>> getHomepageSettings() {
        var setting = platformSettingsService.getSetting("homepage.brandStoresEnabled");
        boolean enabled = setting != null && Boolean.parseBoolean(setting.getSettingValue());
        return ResponseEntity.ok(Map.of("brandStoresEnabled", enabled));
    }

    @PutMapping("/homepage-settings")
    public ResponseEntity<Map<String, Boolean>> updateHomepageSettings(@RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("brandStoresEnabled"));
        platformSettingsService.updateSetting("homepage.brandStoresEnabled", String.valueOf(enabled));
        return ResponseEntity.ok(Map.of("brandStoresEnabled", enabled));
    }

    @GetMapping("/banners")
    public ResponseEntity<List<Banner>> getBanners() { return ResponseEntity.ok(contentService.getAllBanners()); }
    @PostMapping("/banners")
    public ResponseEntity<Banner> createBanner(@RequestBody Banner b) { return ResponseEntity.ok(contentService.createBanner(b)); }
    @PutMapping("/banners/{id}")
    public ResponseEntity<Banner> updateBanner(@PathVariable Long id, @RequestBody Banner b) { return ResponseEntity.ok(contentService.updateBanner(id, b)); }
    @DeleteMapping("/banners/{id}")
    public ResponseEntity<Void> deleteBanner(@PathVariable Long id) { contentService.deleteBanner(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/pages")
    public ResponseEntity<List<PageContent>> getPages() { return ResponseEntity.ok(contentService.getAllPages()); }
    @PostMapping("/pages")
    public ResponseEntity<PageContent> createPage(@RequestBody PageContent p) { return ResponseEntity.ok(contentService.createPage(p)); }
    @PutMapping("/pages/{id}")
    public ResponseEntity<PageContent> updatePage(@PathVariable Long id, @RequestBody PageContent p) { return ResponseEntity.ok(contentService.updatePage(id, p)); }
    @DeleteMapping("/pages/{id}")
    public ResponseEntity<Void> deletePage(@PathVariable Long id) { contentService.deletePage(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/faqs")
    public ResponseEntity<List<Faq>> getFaqs() { return ResponseEntity.ok(contentService.getAllFaqs()); }
    @PostMapping("/faqs")
    public ResponseEntity<Faq> createFaq(@RequestBody Faq f) { return ResponseEntity.ok(contentService.createFaq(f)); }
    @PutMapping("/faqs/{id}")
    public ResponseEntity<Faq> updateFaq(@PathVariable Long id, @RequestBody Faq f) { return ResponseEntity.ok(contentService.updateFaq(id, f)); }
    @DeleteMapping("/faqs/{id}")
    public ResponseEntity<Void> deleteFaq(@PathVariable Long id) { contentService.deleteFaq(id); return ResponseEntity.noContent().build(); }
}
