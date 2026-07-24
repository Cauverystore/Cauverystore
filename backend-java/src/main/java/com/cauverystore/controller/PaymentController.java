package com.cauverystore.controller;

import com.cauverystore.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@CrossOrigin("*")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> body,
                                                            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(paymentService.createRazorpayOrder(authHeader, body));
    }

    @PostMapping("/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> verifyPayment(@RequestBody Map<String, String> body) {
        paymentService.verifyPayment(body);
        return ResponseEntity.ok(Map.of("message", "Payment verified successfully"));
    }
}
