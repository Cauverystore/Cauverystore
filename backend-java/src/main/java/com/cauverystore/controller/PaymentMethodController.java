package com.cauverystore.controller;

import com.cauverystore.entities.SavedPaymentMethod;
import com.cauverystore.service.PaymentMethodService;
import com.cauverystore.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {
    private final PaymentMethodService svc;
    private final AuthService authService;
    public PaymentMethodController(PaymentMethodService svc, AuthService authService) {
        this.svc = svc;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<List<SavedPaymentMethod>> getMyMethods() {
        return ResponseEntity.ok(svc.getByUser(authService.deriveUserId()));
    }

    @PostMapping
    public ResponseEntity<SavedPaymentMethod> create(@RequestBody SavedPaymentMethod pm) {
        return ResponseEntity.ok(svc.create(pm, authService.deriveUserId()));
    }

    @PutMapping("/{id}/default")
    public ResponseEntity<SavedPaymentMethod> setDefault(@PathVariable Long id) {
        return ResponseEntity.ok(svc.setDefault(id, authService.deriveUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id, authService.deriveUserId());
        return ResponseEntity.noContent().build();
    }
}
