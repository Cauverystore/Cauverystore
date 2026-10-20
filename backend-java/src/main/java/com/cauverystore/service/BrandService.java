package com.cauverystore.service;

import com.cauverystore.entities.Brand;
import com.cauverystore.repository.BrandRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class BrandService {
    private final BrandRepository brandRepo;
    public BrandService(BrandRepository brandRepo) { this.brandRepo = brandRepo; }

    public List<Brand> getAll() { return brandRepo.findAllByOrderBySortOrder(); }
    public Brand getById(Long id) { return brandRepo.findById(id).orElseThrow(() -> new RuntimeException("Brand not found")); }
    public Brand create(Brand b) { return brandRepo.save(b); }
    public Brand update(Long id, Brand b) {
        Brand existing = getById(id);
        existing.setName(b.getName());
        existing.setDescription(b.getDescription());
        existing.setLogo(b.getLogo());
        existing.setActive(b.isActive());
        existing.setSortOrder(b.getSortOrder());
        // Left as it was when the request does not say (an older admin page, say).
        if (b.getGated() != null) existing.setGated(b.getGated());
        return brandRepo.save(existing);
    }
    public void delete(Long id) { brandRepo.deleteById(id); }
}
