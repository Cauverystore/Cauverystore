package com.cauverystore.controller;

import com.cauverystore.entities.Warehouse;
import com.cauverystore.service.WarehouseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/warehouses")
@CrossOrigin("*")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE')")
public class WarehouseController {
    private final WarehouseService warehouseService;
    public WarehouseController(WarehouseService warehouseService) { this.warehouseService = warehouseService; }

    @GetMapping public List<Warehouse> getAll() { return warehouseService.getAll(); }
    @GetMapping("/{id}") public Warehouse getById(@PathVariable Long id) { return warehouseService.getById(id); }
    @PostMapping public Warehouse create(@RequestBody Warehouse w) { return warehouseService.create(w); }
    @PutMapping("/{id}") public Warehouse update(@PathVariable Long id, @RequestBody Warehouse w) { return warehouseService.update(id, w); }
    @DeleteMapping("/{id}") public ResponseEntity<?> delete(@PathVariable Long id) { warehouseService.delete(id); return ResponseEntity.ok(Map.of("message", "Deleted")); }
}
