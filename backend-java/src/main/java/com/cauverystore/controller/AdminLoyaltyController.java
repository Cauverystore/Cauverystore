package com.cauverystore.controller;

import com.cauverystore.entities.LoyaltyRule;
import com.cauverystore.service.LoyaltyRuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/loyalty")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@CrossOrigin("*")
public class AdminLoyaltyController {
    private final LoyaltyRuleService svc;
    public AdminLoyaltyController(LoyaltyRuleService svc) { this.svc = svc; }

    @GetMapping("/rules")
    public ResponseEntity<List<LoyaltyRule>> getAllRules() { return ResponseEntity.ok(svc.getAll()); }
    @GetMapping("/rules/{id}")
    public ResponseEntity<LoyaltyRule> getRule(@PathVariable Long id) { return ResponseEntity.ok(svc.getById(id)); }
    @PostMapping("/rules")
    public ResponseEntity<LoyaltyRule> createRule(@RequestBody LoyaltyRule r) { return ResponseEntity.ok(svc.create(r)); }
    @PutMapping("/rules/{id}")
    public ResponseEntity<LoyaltyRule> updateRule(@PathVariable Long id, @RequestBody LoyaltyRule r) { return ResponseEntity.ok(svc.update(id, r)); }
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) { svc.delete(id); return ResponseEntity.noContent().build(); }
}
