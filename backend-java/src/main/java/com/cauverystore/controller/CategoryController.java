package com.cauverystore.controller;

import com.cauverystore.entities.Category;
import com.cauverystore.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<Category> getAllCategories() {
        return categoryService.getAllCategories();
    }

    /** Only categories that currently have products on sale - the storefront menu uses this so it never links to an empty page. */
    @GetMapping("/with-products")
    public List<Category> getCategoriesWithProducts() {
        return categoryService.getCategoriesWithProducts();
    }

    @GetMapping("/{id}")
    public Category getCategory(@PathVariable Long id) {
        return categoryService.getCategory(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/admin/add")
    public ResponseEntity<?> addCategory(@RequestBody Category category) {
        try {
            Category saved = categoryService.addCategory(category, null);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** The recommended list, marked with which ones the store already has. */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/admin/standard")
    public List<Map<String, Object>> standardCategories() {
        java.util.Set<String> have = new java.util.HashSet<>();
        for (Category c : categoryService.getAllCategories()) {
            if (c.getName() != null) have.add(c.getName().trim().toLowerCase(java.util.Locale.ROOT));
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (com.cauverystore.service.StandardCategories.Entry e : com.cauverystore.service.StandardCategories.ALL) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("name", e.name());
            m.put("description", e.description());
            m.put("requiredLicence", e.requiredLicence());
            m.put("exists", have.contains(e.name().toLowerCase(java.util.Locale.ROOT)));
            out.add(m);
        }
        return out;
    }

    /** Adds the recommended categories the store does not have yet. Existing ones are not touched. */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/admin/add-standard")
    public ResponseEntity<?> addStandardCategories() {
        List<String> added = categoryService.addStandardCategories();
        return ResponseEntity.ok(Map.of("added", added, "count", added.size()));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/admin/update/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody Category updated) {
        try {
            Category saved = categoryService.updateCategory(id, updated, null);
            if (saved == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/admin/delete/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        try {
            categoryService.deleteCategory(id, null);
            return ResponseEntity.ok(Map.of("message", "Category deleted successfully"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
