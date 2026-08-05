package com.cauverystore.controller;

import com.cauverystore.dto.AuthResponse;
import com.cauverystore.dto.LoginRequest;
import com.cauverystore.dto.RefreshTokenRequest;
import com.cauverystore.dto.RegisterRequest;
import com.cauverystore.entities.User;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.service.AuthService;
import com.cauverystore.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final UserRepository userRepo;
    private final CookieUtil cookieUtil;

    private void applyRefreshCookie(HttpServletResponse response, AuthResponse authResponse) {
        if (authResponse != null && authResponse.getRefreshToken() != null) {
            cookieUtil.setRefreshTokenCookie(response, authResponse.getRefreshToken());
            authResponse.setRefreshToken(null);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse authResponse = authService.authenticate(request);
        applyRefreshCookie(response, authResponse);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody(required = false) RefreshTokenRequest request, HttpServletRequest http, HttpServletResponse response) {
        String cookieToken = cookieUtil.readRefreshToken(http);
        String bodyToken = request != null ? request.getRefreshToken() : null;
        RefreshTokenRequest effective = new RefreshTokenRequest();
        effective.setRefreshToken(cookieToken != null ? cookieToken : bodyToken);
        AuthResponse authResponse = authService.refreshAccessToken(effective);
        applyRefreshCookie(response, authResponse);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody(required = false) RefreshTokenRequest request, HttpServletRequest http, HttpServletResponse response) {
        String cookieToken = cookieUtil.readRefreshToken(http);
        String token = cookieToken != null ? cookieToken : (request != null ? request.getRefreshToken() : null);
        if (token != null) {
            authService.logout(token);
        }
        cookieUtil.clearRefreshTokenCookie(response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<User> getProfile() {
        Long userId = authService.deriveUserId();
        return ResponseEntity.ok(authService.getUserById(userId));
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<User> updateProfile(@RequestBody User updatedUser) {
        Long userId = authService.deriveUserId();
        User existing = authService.getUserById(userId);
        if (updatedUser.getFullName() != null) existing.setFullName(updatedUser.getFullName());
        if (updatedUser.getPhone() != null) existing.setPhone(updatedUser.getPhone());
        if (updatedUser.getEmail() != null) existing.setEmail(updatedUser.getEmail());
        if (updatedUser.getAddress() != null) existing.setAddress(updatedUser.getAddress());
        userRepo.save(existing);
        return ResponseEntity.ok(existing);
    }

    @GetMapping("/secure")
    public ResponseEntity<Map<String, String>> secure() {
        return ResponseEntity.ok(Map.of("message", "Secure endpoint working"));
    }
}
