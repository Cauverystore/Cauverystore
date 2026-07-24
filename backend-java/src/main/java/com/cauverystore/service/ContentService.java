package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ContentService {
    private final BannerRepository bannerRepo;
    private final PageContentRepository pageRepo;
    private final FaqRepository faqRepo;

    public ContentService(BannerRepository bannerRepo, PageContentRepository pageRepo, FaqRepository faqRepo) {
        this.bannerRepo = bannerRepo; this.pageRepo = pageRepo; this.faqRepo = faqRepo;
    }

    // Banners
    public List<Banner> getAllBanners() { return bannerRepo.findAllByOrderBySortOrder(); }
    public Banner getBanner(Long id) { return bannerRepo.findById(id).orElseThrow(() -> new RuntimeException("Banner not found")); }
    public Banner createBanner(Banner b) { return bannerRepo.save(b); }
    public Banner updateBanner(Long id, Banner b) {
        Banner e = getBanner(id); e.setTitle(b.getTitle()); e.setSubtitle(b.getSubtitle());
        e.setImageUrl(b.getImageUrl()); e.setLink(b.getLink()); e.setPosition(b.getPosition());
        e.setSortOrder(b.getSortOrder()); e.setActive(b.isActive()); return bannerRepo.save(e);
    }
    public void deleteBanner(Long id) { bannerRepo.deleteById(id); }

    // Pages
    public List<PageContent> getAllPages() { return pageRepo.findAll(); }
    public PageContent getPage(Long id) { return pageRepo.findById(id).orElseThrow(() -> new RuntimeException("Page not found")); }
    public PageContent createPage(PageContent p) { return pageRepo.save(p); }
    public PageContent updatePage(Long id, PageContent p) {
        PageContent e = getPage(id); e.setSlug(p.getSlug()); e.setTitle(p.getTitle());
        e.setContent(p.getContent()); e.setType(p.getType()); e.setActive(p.isActive());
        e.setMetaTitle(p.getMetaTitle()); e.setMetaDescription(p.getMetaDescription());
        return pageRepo.save(e);
    }
    public void deletePage(Long id) { pageRepo.deleteById(id); }

    // FAQs
    public List<Faq> getAllFaqs() { return faqRepo.findAllByOrderBySortOrder(); }
    public Faq getFaq(Long id) { return faqRepo.findById(id).orElseThrow(() -> new RuntimeException("FAQ not found")); }
    public Faq createFaq(Faq f) { return faqRepo.save(f); }
    public Faq updateFaq(Long id, Faq f) {
        Faq e = getFaq(id); e.setQuestion(f.getQuestion()); e.setAnswer(f.getAnswer());
        e.setCategory(f.getCategory()); e.setSortOrder(f.getSortOrder()); e.setActive(f.isActive());
        return faqRepo.save(e);
    }
    public void deleteFaq(Long id) { faqRepo.deleteById(id); }
}
