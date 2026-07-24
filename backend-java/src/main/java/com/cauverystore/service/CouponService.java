package com.cauverystore.service;

import com.cauverystore.entities.Coupon;
import com.cauverystore.repository.CouponRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CouponService {
    private final CouponRepository couponRepo;
    public CouponService(CouponRepository couponRepo) { this.couponRepo = couponRepo; }

    public List<Coupon> getAll() { return couponRepo.findAll(); }
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
