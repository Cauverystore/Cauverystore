package com.cauverystore.controller;

import com.cauverystore.dto.AuthResponse;
import com.cauverystore.dto.LoginRequest;
import com.cauverystore.entities.User;
import com.cauverystore.service.AuthService;
import com.cauverystore.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerAuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse authResponse = authService.authenticate(request);
        User user = authService.getUserByEmail(authResponse.getEmail());
        if (!authService.userHasRole(user, "SELLER")) {
            throw new RuntimeException("Access denied. Seller credentials required.");
        }
        if (!"ACTIVE".equals(user.getStatus()) || !user.isActive()) {
            throw new RuntimeException("Seller account is not active");
        }
        if (authResponse.getRefreshToken() != null) {
            cookieUtil.setRefreshTokenCookie(response, authResponse.getRefreshToken());
            authResponse.setRefreshToken(null);
        }
        return ResponseEntity.ok(authResponse);
    }
}
