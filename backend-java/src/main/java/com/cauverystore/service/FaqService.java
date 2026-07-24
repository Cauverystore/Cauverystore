package com.cauverystore.service;

import com.cauverystore.entities.Faq;
import com.cauverystore.repository.FaqRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FaqService {
    private final FaqRepository faqRepo;
    public FaqService(FaqRepository faqRepo) { this.faqRepo = faqRepo; }

    public List<Faq> getAll() { return faqRepo.findAllByOrderBySortOrder(); }
    public List<Faq> getActive() { return faqRepo.findByActiveTrueOrderBySortOrder(); }
    public Faq getById(Long id) { return faqRepo.findById(id).orElseThrow(() -> new RuntimeException("FAQ not found")); }
    public Faq create(Faq f) { return faqRepo.save(f); }
    public Faq update(Long id, Faq f) {
        Faq existing = getById(id);
        if (f.getQuestion() != null) existing.setQuestion(f.getQuestion());
        if (f.getAnswer() != null) existing.setAnswer(f.getAnswer());
        if (f.getCategory() != null) existing.setCategory(f.getCategory());
        if (f.getSortOrder() != null) existing.setSortOrder(f.getSortOrder());
        existing.setActive(f.isActive());
        return faqRepo.save(existing);
    }
    public void delete(Long id) { faqRepo.deleteById(id); }
}
