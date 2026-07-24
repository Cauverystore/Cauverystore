package com.cauverystore.service;

import com.cauverystore.entities.SavedPaymentMethod;
import com.cauverystore.entities.User;
import com.cauverystore.repository.SavedPaymentMethodRepository;
import com.cauverystore.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PaymentMethodService {
    private final SavedPaymentMethodRepository repo;
    private final UserRepository userRepo;
    public PaymentMethodService(SavedPaymentMethodRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    public List<SavedPaymentMethod> getByUser(Long userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        return repo.findByUser(user);
    }

    public SavedPaymentMethod create(SavedPaymentMethod pm, Long userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        pm.setUser(user);
        if (Boolean.TRUE.equals(pm.getIsDefault())) {
            clearDefaultForUser(user);
        }
        return repo.save(pm);
    }

    public void delete(Long id, Long userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        repo.deleteByUserAndId(user, id);
    }

    public SavedPaymentMethod setDefault(Long id, Long userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        clearDefaultForUser(user);
        SavedPaymentMethod pm = repo.findById(id).orElseThrow(() -> new RuntimeException("Payment method not found"));
        pm.setIsDefault(true);
        return repo.save(pm);
    }

    private void clearDefaultForUser(User user) {
        repo.findByUser(user).stream()
            .filter(pm -> Boolean.TRUE.equals(pm.getIsDefault()))
            .forEach(pm -> { pm.setIsDefault(false); repo.save(pm); });
    }
}
