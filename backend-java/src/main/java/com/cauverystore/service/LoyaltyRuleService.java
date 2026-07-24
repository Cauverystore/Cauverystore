package com.cauverystore.service;

import com.cauverystore.entities.LoyaltyRule;
import com.cauverystore.repository.LoyaltyRuleRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class LoyaltyRuleService {
    private final LoyaltyRuleRepository repo;
    public LoyaltyRuleService(LoyaltyRuleRepository repo) { this.repo = repo; }

    public List<LoyaltyRule> getAll() { return repo.findAll(); }
    public LoyaltyRule getById(Long id) { return repo.findById(id).orElseThrow(() -> new RuntimeException("Rule not found")); }
    public LoyaltyRule create(LoyaltyRule r) { return repo.save(r); }
    public LoyaltyRule update(Long id, LoyaltyRule r) {
        LoyaltyRule existing = getById(id);
        if (r.getName() != null) existing.setName(r.getName());
        if (r.getDescription() != null) existing.setDescription(r.getDescription());
        if (r.getPointsPerUnit() != null) existing.setPointsPerUnit(r.getPointsPerUnit());
        if (r.getMinimumOrderAmount() != null) existing.setMinimumOrderAmount(r.getMinimumOrderAmount());
        existing.setActive(r.isActive());
        return repo.save(existing);
    }
    public void delete(Long id) { repo.deleteById(id); }
}
