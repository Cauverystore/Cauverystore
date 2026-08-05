package com.cauverystore.controller;

import com.cauverystore.entities.Faq;
import com.cauverystore.service.FaqService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/faqs")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminFaqController {
    private final FaqService faqService;
    public AdminFaqController(FaqService faqService) { this.faqService = faqService; }

    @GetMapping
    public ResponseEntity<List<Faq>> getAll() { return ResponseEntity.ok(faqService.getAll()); }
    @GetMapping("/{id}")
    public ResponseEntity<Faq> getById(@PathVariable Long id) { return ResponseEntity.ok(faqService.getById(id)); }
    @PostMapping
    public ResponseEntity<Faq> create(@RequestBody Faq f) { return ResponseEntity.ok(faqService.create(f)); }
    @PutMapping("/{id}")
    public ResponseEntity<Faq> update(@PathVariable Long id, @RequestBody Faq f) { return ResponseEntity.ok(faqService.update(id, f)); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { faqService.delete(id); return ResponseEntity.noContent().build(); }
}
