package com.cauverystore.controller;

import com.cauverystore.entities.Refund;
import com.cauverystore.repository.RefundRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/refunds")
@PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'SUPER_ADMIN')")
public class AdminRefundController {

    private final RefundRepository refundRepo;

    public AdminRefundController(RefundRepository refundRepo) {
        this.refundRepo = refundRepo;
    }

    @GetMapping
    public ResponseEntity<?> getAllRefunds(@RequestParam(required = false) String status) {
        var refunds = refundRepo.findAll();
        if (status != null && !status.isBlank()) {
            refunds = refunds.stream().filter(r -> status.equalsIgnoreCase(r.getStatus())).toList();
        }
        return ResponseEntity.ok(refunds);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateRefundStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Refund refund = refundRepo.findById(id).orElse(null);
        if (refund == null) return ResponseEntity.badRequest().body(Map.of("error", "Refund not found"));
        refund.setStatus(body.get("status"));
        refundRepo.save(refund);
        return ResponseEntity.ok(Map.of("message", "Refund status updated"));
    }
}
