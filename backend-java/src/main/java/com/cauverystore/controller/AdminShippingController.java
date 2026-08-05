package com.cauverystore.controller;

import com.cauverystore.entities.ShippingZone;
import com.cauverystore.service.ShippingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/shipping")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminShippingController {
    private final ShippingService shippingService;
    public AdminShippingController(ShippingService shippingService) { this.shippingService = shippingService; }

    @GetMapping
    public ResponseEntity<List<ShippingZone>> getAll() { return ResponseEntity.ok(shippingService.getAll()); }
    @PostMapping
    public ResponseEntity<ShippingZone> create(@RequestBody ShippingZone z) { return ResponseEntity.ok(shippingService.create(z)); }
    @PutMapping("/{id}")
    public ResponseEntity<ShippingZone> update(@PathVariable Long id, @RequestBody ShippingZone z) { return ResponseEntity.ok(shippingService.update(id, z)); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { shippingService.delete(id); return ResponseEntity.noContent().build(); }
}
