package com.cauverystore.controller;

import com.cauverystore.service.StockMovementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

// Read-only and staff-only: the history covers every seller's products.
@RestController
@RequestMapping("/api/stock-movements")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class StockMovementController {
    private final StockMovementService svc;
    public StockMovementController(StockMovementService svc) { this.svc = svc; }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() { return ResponseEntity.ok(svc.recent()); }
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) { return ResponseEntity.ok(svc.getById(id)); }
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<Map<String, Object>>> getByProduct(@PathVariable Long productId) { return ResponseEntity.ok(svc.getByProduct(productId)); }
    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<Map<String, Object>>> getByWarehouse(@PathVariable Long warehouseId) { return ResponseEntity.ok(svc.getByWarehouse(warehouseId)); }
    @GetMapping("/type/{type}")
    public ResponseEntity<List<Map<String, Object>>> getByType(@PathVariable String type) { return ResponseEntity.ok(svc.getByType(type)); }
}
