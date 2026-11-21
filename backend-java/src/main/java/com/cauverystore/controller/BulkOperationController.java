package com.cauverystore.controller;

import com.cauverystore.service.BulkOperationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/products/bulk")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE')")
public class BulkOperationController {
    private static final int MAX_PRODUCTS = 200;

    private final BulkOperationService bulkService;
    public BulkOperationController(BulkOperationService bulkService) { this.bulkService = bulkService; }

    @PutMapping("/price")
    public ResponseEntity<?> bulkPriceUpdate(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(bulkService.bulkPriceUpdate(ids(body), number(body.get("price")), number(body.get("offerPrice"))));
    }

    @PutMapping("/stock")
    public ResponseEntity<?> bulkStockUpdate(@RequestBody Map<String, Object> body) {
        Double stock = number(body.get("stock"));
        if (stock == null || stock != Math.rint(stock)) throw new IllegalArgumentException("Stock must be a whole number.");
        return ResponseEntity.ok(bulkService.bulkStockUpdate(ids(body), stock.intValue()));
    }

    @PutMapping("/status")
    public ResponseEntity<?> bulkStatusUpdate(@RequestBody Map<String, Object> body) {
        Object active = body.get("active");
        return ResponseEntity.ok(bulkService.bulkStatusUpdate(ids(body), active instanceof Boolean b ? b : null));
    }

    @PostMapping("/delete")
    public ResponseEntity<?> bulkDelete(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(bulkService.bulkDelete(ids(body)));
    }

    // The page sends what was typed into a text box, so an ID can arrive as text or as a number.
    static List<Long> ids(Map<String, Object> body) {
        if (!(body.get("ids") instanceof List<?> raw) || raw.isEmpty()) {
            throw new IllegalArgumentException("Enter at least one product ID.");
        }
        if (raw.size() > MAX_PRODUCTS) {
            throw new IllegalArgumentException("Enter at most " + MAX_PRODUCTS + " product IDs at a time.");
        }
        List<Long> ids = new ArrayList<>();
        for (Object value : raw) {
            try {
                long id = value instanceof Integer || value instanceof Long
                        ? ((Number) value).longValue()
                        : Long.parseLong(String.valueOf(value).trim());
                if (id <= 0) throw new NumberFormatException();
                ids.add(id);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Product IDs must be whole numbers, like 12, 45.");
            }
        }
        return ids;
    }

    private static Double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }
}
