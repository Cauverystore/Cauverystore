package com.cauverystore.controller;

import com.cauverystore.entities.LoyaltyPoint;
import com.cauverystore.repository.LoyaltyPointRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/users")
public class LoyaltyController {

    private final LoyaltyPointRepository pointRepo;

    public LoyaltyController(LoyaltyPointRepository pointRepo) {
        this.pointRepo = pointRepo;
    }

    @GetMapping("/loyalty")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getLoyaltyPoints() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object details = auth.getDetails();
        Long userId = details instanceof Long ? (Long) details : 0L;
        List<LoyaltyPoint> points = pointRepo.findByUserId(userId);
        int credits = pointRepo.getTotalCredits(userId);
        int debits = pointRepo.getTotalDebits(userId);
        List<Map<String,Object>> history = new ArrayList<>();
        for (LoyaltyPoint lp : points) {
            Map<String,Object> entry = new LinkedHashMap<>();
            entry.put("points", lp.getPoints());
            entry.put("type", lp.getType());
            entry.put("description", lp.getReason());
            entry.put("date", lp.getCreatedAt() != null ? lp.getCreatedAt().toString() : "");
            history.add(entry);
        }
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("balance", credits - debits);
        r.put("points", credits - debits);
        r.put("totalCredits", credits);
        r.put("totalDebits", debits);
        r.put("transactions", history);
        return ResponseEntity.ok(r);
    }
}
