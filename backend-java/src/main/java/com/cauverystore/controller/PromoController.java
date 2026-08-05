package com.cauverystore.controller;

import com.cauverystore.entities.Coupon;
import com.cauverystore.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/promo")
@RequiredArgsConstructor
public class PromoController {

    private final CouponService couponService;

    @PostMapping("/validate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Coupon> validate(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        Object cartTotalObj = body.get("cartTotal");
        Double cartTotal = cartTotalObj != null ? Double.valueOf(cartTotalObj.toString()) : null;
        return ResponseEntity.ok(couponService.validate(code, cartTotal));
    }
}
