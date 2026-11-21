package com.cauverystore.controller;

import com.cauverystore.service.SupplierOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.function.Supplier;

/** The list of items to buy from suppliers. Nothing here pays anyone; a person pays on the supplier's site. */
@RestController
@RequestMapping("/api/admin/supplier-orders")
@PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'SUPER_ADMIN')")
public class AdminSupplierOrderController {

    private final SupplierOrderService service;

    public AdminSupplierOrderController(SupplierOrderService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "todo") String view) {
        service.prepare();
        return service.list(view);
    }

    @PostMapping("/{id}/ordered")
    public ResponseEntity<?> ordered(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication auth) {
        Double amount = null;
        try {
            Object a = body.get("amountPaid");
            if (a != null && !String.valueOf(a).isBlank()) amount = Double.valueOf(String.valueOf(a).trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter the amount as a number."));
        }
        Double paid = amount;
        return wrap(() -> service.markOrdered(id, body.get("supplierOrderRef") == null ? null : String.valueOf(body.get("supplierOrderRef")),
                paid, auth != null ? auth.getName() : null));
    }

    @PostMapping("/{id}/handled")
    public ResponseEntity<?> handled(@PathVariable Long id, Authentication auth) {
        return wrap(() -> service.markHandled(id, auth != null ? auth.getName() : null));
    }

    private ResponseEntity<?> wrap(Supplier<Map<String, Object>> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (SupplierOrderService.BadRequest e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
