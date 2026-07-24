package com.cauverystore.service;

import com.cauverystore.entities.Banner;
import com.cauverystore.repository.BannerRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class BannerService {
    private final BannerRepository bannerRepo;
    public BannerService(BannerRepository bannerRepo) { this.bannerRepo = bannerRepo; }

    public List<Banner> getAll() { return bannerRepo.findAllByOrderBySortOrder(); }
    public List<Banner> getActiveByPosition(String position) { return bannerRepo.findByActiveTrueAndPositionOrderBySortOrder(position); }
    public Banner getById(Long id) { return bannerRepo.findById(id).orElseThrow(() -> new RuntimeException("Banner not found")); }
    public Banner create(Banner b) { return bannerRepo.save(b); }
    public Banner update(Long id, Banner b) {
        Banner existing = getById(id);
        if (b.getTitle() != null) existing.setTitle(b.getTitle());
        if (b.getSubtitle() != null) existing.setSubtitle(b.getSubtitle());
        if (b.getImageUrl() != null) existing.setImageUrl(b.getImageUrl());
        if (b.getLink() != null) existing.setLink(b.getLink());
        if (b.getPosition() != null) existing.setPosition(b.getPosition());
        if (b.getSortOrder() != null) existing.setSortOrder(b.getSortOrder());
        existing.setActive(b.isActive());
        return bannerRepo.save(existing);
    }
    public void delete(Long id) { bannerRepo.deleteById(id); }
}
