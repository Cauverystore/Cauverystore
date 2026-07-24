package com.cauverystore.controller;

import com.cauverystore.entities.*;
import com.cauverystore.service.ContentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/content")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@CrossOrigin("*")
public class AdminContentController {
    private final ContentService contentService;
    public AdminContentController(ContentService contentService) { this.contentService = contentService; }

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
