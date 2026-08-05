package com.cauverystore.service;

import com.cauverystore.entities.PasswordHistory;
import com.cauverystore.entities.User;
import com.cauverystore.repository.PasswordHistoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Enforces the password policy: minimum length, complexity, expiration and a
 * reuse-history. On every password creation/reset the newest hash is recorded
 * and the history is trimmed to the configured depth.
 */
@Service
public class PasswordPolicyService {

    private final PasswordHistoryRepository historyRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${auth.password.min-length:10}")
    private int minLength;

    @Value("${auth.password.history:5}")
    private int historyDepth;

    @Value("${auth.password.expiry-days:90}")
    private int expiryDays;

    public PasswordPolicyService(PasswordHistoryRepository historyRepo, PasswordEncoder passwordEncoder) {
        this.historyRepo = historyRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public void validateComplexity(String password) {
        if (password == null || password.length() < minLength) {
            throw new RuntimeException("Password must be at least " + minLength + " characters long");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new RuntimeException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new RuntimeException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new RuntimeException("Password must contain at least one number");
        }
        if (!password.matches(".*[^a-zA-Z0-9].*")) {
            throw new RuntimeException("Password must contain at least one special character");
        }
    }

    /** Throws if the new password matches any of the user's last N passwords. */
    public void checkHistory(User user, String newPassword) {
        List<PasswordHistory> history = historyRepo.findByUserIdOrderByCreatedAtDesc(user.getId());
        for (PasswordHistory h : history) {
            if (passwordEncoder.matches(newPassword, h.getPasswordHash())) {
                throw new RuntimeException("Password has been used before. Choose a new password.");
            }
        }
    }

    /**
     * Encodes and stores the hash in history (trimmed), and stamps the changed/
     * expiry timestamps on the user so future logins know when it must change.
     */
    public void recordNewPassword(User user, String rawPassword) {
        String encoded = passwordEncoder.encode(rawPassword);
        user.setPassword(encoded);
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setPasswordExpiresAt(LocalDateTime.now().plusDays(expiryDays));

        PasswordHistory entry = new PasswordHistory();
        entry.setUserId(user.getId());
        entry.setPasswordHash(encoded);
        historyRepo.save(entry);

        List<PasswordHistory> history = historyRepo.findByUserIdOrderByCreatedAtDesc(user.getId());
        if (history.size() > historyDepth) {
            for (int i = historyDepth; i < history.size(); i++) {
                historyRepo.delete(history.get(i));
            }
        }
    }

    /** Marks the password expired so the next login is forced to reset. */
    public void applyExpiry(User user) {
        if (user.getPasswordExpiresAt() != null && user.getPasswordExpiresAt().isBefore(LocalDateTime.now())) {
            user.setMustResetPassword(true);
        }
    }
}
