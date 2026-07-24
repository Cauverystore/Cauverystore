package com.cauverystore.service;

import com.cauverystore.entities.ShippingZone;
import com.cauverystore.repository.ShippingZoneRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ShippingService {
    private final ShippingZoneRepository shippingRepo;
    public ShippingService(ShippingZoneRepository shippingRepo) { this.shippingRepo = shippingRepo; }

    public List<ShippingZone> getAll() { return shippingRepo.findAll(); }
    public ShippingZone getById(Long id) { return shippingRepo.findById(id).orElseThrow(() -> new RuntimeException("Shipping zone not found")); }
    public ShippingZone create(ShippingZone z) { return shippingRepo.save(z); }
    public ShippingZone update(Long id, ShippingZone z) {
        ShippingZone e = getById(id); e.setName(z.getName()); e.setRegions(z.getRegions());
        e.setCharge(z.getCharge()); e.setFreeShippingThreshold(z.getFreeShippingThreshold());
        e.setEstimatedMinDays(z.getEstimatedMinDays()); e.setEstimatedMaxDays(z.getEstimatedMaxDays());
        e.setActive(z.isActive()); e.setCourierPartner(z.getCourierPartner()); e.setCodAvailable(z.isCodAvailable());
        return shippingRepo.save(e);
    }
    public void delete(Long id) { shippingRepo.deleteById(id); }
}
