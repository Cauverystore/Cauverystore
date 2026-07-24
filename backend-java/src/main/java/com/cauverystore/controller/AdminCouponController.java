package com.cauverystore.controller;

import com.cauverystore.entities.Coupon;
import com.cauverystore.service.CouponService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/coupons")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@CrossOrigin("*")
public class AdminCouponController {
    private final CouponService couponService;
    public AdminCouponController(CouponService couponService) { this.couponService = couponService; }

    @GetMapping
    public ResponseEntity<List<Coupon>> getAll() { return ResponseEntity.ok(couponService.getAll()); }
    @GetMapping("/{id}")
    public ResponseEntity<Coupon> getById(@PathVariable Long id) { return ResponseEntity.ok(couponService.getById(id)); }
    @PostMapping
    public ResponseEntity<Coupon> create(@RequestBody Coupon c) { return ResponseEntity.ok(couponService.create(c)); }
    @PutMapping("/{id}")
    public ResponseEntity<Coupon> update(@PathVariable Long id, @RequestBody Coupon c) { return ResponseEntity.ok(couponService.update(id, c)); }
    @PutMapping("/{id}/toggle")
    public ResponseEntity<Coupon> toggle(@PathVariable Long id) { return ResponseEntity.ok(couponService.toggleActive(id)); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { couponService.delete(id); return ResponseEntity.noContent().build(); }
}
