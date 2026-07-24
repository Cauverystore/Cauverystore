package com.cauverystore.controller;

import com.cauverystore.dto.AuthResponse;
import com.cauverystore.dto.LoginRequest;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/seller")
@CrossOrigin("*")
@RequiredArgsConstructor
public class SellerAuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.authenticate(request);
        User user = authService.getUserByEmail(response.getEmail());
        if (user.getRole() != Role.SELLER) {
            throw new RuntimeException("Access denied. Seller credentials required.");
        }
        if (!"ACTIVE".equals(user.getStatus()) || !user.isActive()) {
            throw new RuntimeException("Seller account is not active");
        }
        return ResponseEntity.ok(response);
    }
}
