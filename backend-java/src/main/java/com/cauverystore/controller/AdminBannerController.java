package com.cauverystore.controller;

import com.cauverystore.entities.Banner;
import com.cauverystore.service.BannerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/banners")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminBannerController {
    private final BannerService bannerService;
    public AdminBannerController(BannerService bannerService) { this.bannerService = bannerService; }

    @GetMapping
    public ResponseEntity<List<Banner>> getAll() { return ResponseEntity.ok(bannerService.getAll()); }
    @GetMapping("/{id}")
    public ResponseEntity<Banner> getById(@PathVariable Long id) { return ResponseEntity.ok(bannerService.getById(id)); }
    @PostMapping
    public ResponseEntity<Banner> create(@RequestBody Banner b) { return ResponseEntity.ok(bannerService.create(b)); }
    @PutMapping("/{id}")
    public ResponseEntity<Banner> update(@PathVariable Long id, @RequestBody Banner b) { return ResponseEntity.ok(bannerService.update(id, b)); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { bannerService.delete(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/position/{position}")
    public ResponseEntity<List<Banner>> getByPosition(@PathVariable String position) {
        return ResponseEntity.ok(bannerService.getActiveByPosition(position));
    }
}
