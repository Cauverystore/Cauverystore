package com.cauverystore.controller;

import com.cauverystore.entities.Supplier;
import com.cauverystore.service.SupplierService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/suppliers")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE')")
public class SupplierController {
    private final SupplierService supplierService;
    public SupplierController(SupplierService supplierService) { this.supplierService = supplierService; }

    @GetMapping public List<Supplier> getAll() { return supplierService.getAll(); }
    @GetMapping("/{id}") public Supplier getById(@PathVariable Long id) { return supplierService.getById(id); }
    @PostMapping public Supplier create(@RequestBody Supplier s) { return supplierService.create(s); }
    @PutMapping("/{id}") public Supplier update(@PathVariable Long id, @RequestBody Supplier s) { return supplierService.update(id, s); }
    @DeleteMapping("/{id}") public ResponseEntity<?> delete(@PathVariable Long id) { supplierService.delete(id); return ResponseEntity.ok(Map.of("message", "Deleted")); }
}
