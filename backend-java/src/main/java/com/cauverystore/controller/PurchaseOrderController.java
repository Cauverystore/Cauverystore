package com.cauverystore.controller;

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

    @GetMapping public List<Map<String, Object>> getAll() { return poService.getAll(); }
    @GetMapping("/{id}") public Map<String, Object> getById(@PathVariable Long id) { return poService.getById(id); }
    @PostMapping public Map<String, Object> create(@RequestBody PurchaseOrderService.OrderRequest po) { return poService.create(po); }
    @PutMapping("/{id}") public Map<String, Object> update(@PathVariable Long id, @RequestBody PurchaseOrderService.OrderRequest po) { return poService.update(id, po); }
    @PostMapping("/{id}/status") public Map<String, Object> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body) { return poService.changeStatus(id, body.get("status")); }
    @DeleteMapping("/{id}") public ResponseEntity<?> delete(@PathVariable Long id) { poService.delete(id); return ResponseEntity.ok(Map.of("message", "Deleted")); }
    @GetMapping("/status/{status}") public List<Map<String, Object>> getByStatus(@PathVariable String status) { return poService.getByStatus(status); }
}
