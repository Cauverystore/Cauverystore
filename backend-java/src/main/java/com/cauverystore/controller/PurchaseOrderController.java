package com.cauverystore.controller;

import com.cauverystore.entities.PurchaseOrder;
import com.cauverystore.service.PurchaseOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/purchase-orders")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE')")
public class PurchaseOrderController {
    private final PurchaseOrderService poService;
    public PurchaseOrderController(PurchaseOrderService poService) { this.poService = poService; }

    @GetMapping public List<PurchaseOrder> getAll() { return poService.getAll(); }
    @GetMapping("/{id}") public PurchaseOrder getById(@PathVariable Long id) { return poService.getById(id); }
    @PostMapping public PurchaseOrder create(@RequestBody PurchaseOrder po) { return poService.create(po); }
    @PutMapping("/{id}") public PurchaseOrder update(@PathVariable Long id, @RequestBody PurchaseOrder po) { return poService.update(id, po); }
    @DeleteMapping("/{id}") public ResponseEntity<?> delete(@PathVariable Long id) { poService.delete(id); return ResponseEntity.ok(Map.of("message", "Deleted")); }
    @GetMapping("/status/{status}") public List<PurchaseOrder> getByStatus(@PathVariable String status) { return poService.getByStatus(status); }
}
