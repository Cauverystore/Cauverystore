package com.cauverystore.controller;

import com.cauverystore.entities.Brand;
import com.cauverystore.service.BrandService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/brands")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@CrossOrigin("*")
public class AdminBrandController {
    private final BrandService brandService;
    public AdminBrandController(BrandService brandService) { this.brandService = brandService; }

    @GetMapping
    public ResponseEntity<List<Brand>> getAll() { return ResponseEntity.ok(brandService.getAll()); }
    @GetMapping("/{id}")
    public ResponseEntity<Brand> getById(@PathVariable Long id) { return ResponseEntity.ok(brandService.getById(id)); }
    @PostMapping
    public ResponseEntity<Brand> create(@RequestBody Brand b) { return ResponseEntity.ok(brandService.create(b)); }
    @PutMapping("/{id}")
    public ResponseEntity<Brand> update(@PathVariable Long id, @RequestBody Brand b) { return ResponseEntity.ok(brandService.update(id, b)); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { brandService.delete(id); return ResponseEntity.noContent().build(); }
}
