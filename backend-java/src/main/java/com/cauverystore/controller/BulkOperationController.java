package com.cauverystore.controller;

import com.cauverystore.service.BulkOperationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/products/bulk")
@CrossOrigin("*")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PRODUCT_MANAGER')")
public class BulkOperationController {
    private final BulkOperationService bulkService;
    public BulkOperationController(BulkOperationService bulkService) { this.bulkService = bulkService; }

    @PutMapping("/price")
    public ResponseEntity<?> bulkPriceUpdate(@RequestBody Map<String, Object> body) {
        List<Long> ids = ((List<Integer>) body.get("ids")).stream().map(Long::valueOf).toList();
        Double price = body.get("price") != null ? ((Number) body.get("price")).doubleValue() : null;
        Double offerPrice = body.get("offerPrice") != null ? ((Number) body.get("offerPrice")).doubleValue() : null;
        return ResponseEntity.ok(bulkService.bulkPriceUpdate(ids, price, offerPrice));
    }

    @PutMapping("/stock")
    public ResponseEntity<?> bulkStockUpdate(@RequestBody Map<String, Object> body) {
        List<Long> ids = ((List<Integer>) body.get("ids")).stream().map(Long::valueOf).toList();
        Integer stock = ((Number) body.get("stock")).intValue();
        return ResponseEntity.ok(bulkService.bulkStockUpdate(ids, stock));
    }

    @PutMapping("/status")
    public ResponseEntity<?> bulkStatusUpdate(@RequestBody Map<String, Object> body) {
        List<Long> ids = ((List<Integer>) body.get("ids")).stream().map(Long::valueOf).toList();
        Boolean active = (Boolean) body.get("active");
        return ResponseEntity.ok(bulkService.bulkStatusUpdate(ids, active));
    }

    @PostMapping("/delete")
    public ResponseEntity<?> bulkDelete(@RequestBody Map<String, Object> body) {
        List<Long> ids = ((List<Integer>) body.get("ids")).stream().map(Long::valueOf).toList();
        return ResponseEntity.ok(bulkService.bulkDelete(ids));
    }
}
