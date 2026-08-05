package com.cauverystore.controller;

import com.cauverystore.entities.StockMovement;
import com.cauverystore.service.StockMovementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/stock-movements")
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'SUPER_ADMIN')")
public class StockMovementController {
    private final StockMovementService svc;
    public StockMovementController(StockMovementService svc) { this.svc = svc; }

    @GetMapping
    public ResponseEntity<List<StockMovement>> getAll() { return ResponseEntity.ok(svc.getAll()); }
    @GetMapping("/{id}")
    public ResponseEntity<StockMovement> getById(@PathVariable Long id) { return ResponseEntity.ok(svc.getById(id)); }
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<StockMovement>> getByProduct(@PathVariable Long productId) { return ResponseEntity.ok(svc.getByProduct(productId)); }
    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<StockMovement>> getByWarehouse(@PathVariable Long warehouseId) { return ResponseEntity.ok(svc.getByWarehouse(warehouseId)); }
    @GetMapping("/type/{type}")
    public ResponseEntity<List<StockMovement>> getByType(@PathVariable String type) { return ResponseEntity.ok(svc.getByType(type)); }
    @PostMapping
    public ResponseEntity<StockMovement> create(@RequestBody StockMovement m) { return ResponseEntity.ok(svc.create(m)); }
}
