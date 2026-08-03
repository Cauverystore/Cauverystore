package com.cauverystore.service;

import com.cauverystore.entities.Coupon;
import com.cauverystore.repository.CouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CouponService {
    private final CouponRepository couponRepo;
    public CouponService(CouponRepository couponRepo) { this.couponRepo = couponRepo; }

    public List<Coupon> getAll() { return couponRepo.findAll(); }

    // Read-only check used by the "Apply" preview button and, authoritatively, again at
    // checkout - never trust a client-computed discount for what actually gets charged.
    public Coupon validate(String code, Double cartTotal) {
        if (code == null || code.isBlank()) {
            throw new RuntimeException("Coupon code is required");
        }
        Coupon c = couponRepo.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new RuntimeException("Invalid or expired coupon"));
        if (!c.isActive()) {
            throw new RuntimeException("This coupon is no longer active");
        }
        LocalDateTime now = LocalDateTime.now();
        if (c.getValidFrom() != null && now.isBefore(c.getValidFrom())) {
            throw new RuntimeException("This coupon is not yet valid");
        }
        if (c.getValidUntil() != null && now.isAfter(c.getValidUntil())) {
            throw new RuntimeException("This coupon has expired");
        }
        if (c.getMaxUses() != null && c.getUsedCount() != null && c.getUsedCount() >= c.getMaxUses()) {
            throw new RuntimeException("This coupon has reached its usage limit");
        }
        if (c.getMinOrderAmount() != null && cartTotal != null && cartTotal < c.getMinOrderAmount()) {
            throw new RuntimeException("Minimum order amount of ₹" + c.getMinOrderAmount() + " required for this coupon");
        }
        return c;
    }

    public double computeDiscount(Coupon c, double subTotal) {
        double value = c.getValue() == null ? 0 : c.getValue();
        double discount = "PERCENTAGE".equalsIgnoreCase(c.getType())
                ? subTotal * (value / 100.0)
                : value;
        if (c.getMaxDiscountAmount() != null) {
            discount = Math.min(discount, c.getMaxDiscountAmount());
        }
        return Math.min(discount, subTotal);
    }

    @Transactional
    public void recordUsage(Coupon c) {
        c.setUsedCount((c.getUsedCount() == null ? 0 : c.getUsedCount()) + 1);
        couponRepo.save(c);
    }
    public Coupon getById(Long id) { return couponRepo.findById(id).orElseThrow(() -> new RuntimeException("Coupon not found")); }
    public Coupon create(Coupon c) { return couponRepo.save(c); }
    public Coupon update(Long id, Coupon c) {
        Coupon existing = getById(id);
        existing.setCode(c.getCode()); existing.setType(c.getType()); existing.setValue(c.getValue());
        existing.setMinOrderAmount(c.getMinOrderAmount()); existing.setMaxUses(c.getMaxUses());
        existing.setMaxDiscountAmount(c.getMaxDiscountAmount()); existing.setValidFrom(c.getValidFrom());
        existing.setValidUntil(c.getValidUntil()); existing.setActive(c.isActive());
        existing.setAppliesTo(c.getAppliesTo()); existing.setAppliesToId(c.getAppliesToId());
        existing.setFreeShipping(c.isFreeShipping());
        return couponRepo.save(existing);
    }
    public void delete(Long id) { couponRepo.deleteById(id); }
    public Coupon toggleActive(Long id) {
        Coupon c = getById(id); c.setActive(!c.isActive()); return couponRepo.save(c);
    }
}
