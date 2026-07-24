package com.cauverystore.controller;

import com.cauverystore.dto.AuthResponse;
import com.cauverystore.dto.LoginRequest;
import com.cauverystore.dto.PasswordResetRequest;
import com.cauverystore.dto.RefreshTokenRequest;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody User user) {
        String message = authService.register(user);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }

    @PostMapping("/user/login")
    public ResponseEntity<AuthResponse> userLogin(@RequestBody LoginRequest request) {
        AuthResponse response = authService.authenticate(request);
        authService.validateUserRole(response.getEmail(), Role.CUSTOMER);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/seller/login")
    public ResponseEntity<AuthResponse> sellerLogin(@RequestBody LoginRequest request) {
        AuthResponse response = authService.authenticate(request);
        authService.validateUserRole(response.getEmail(), Role.SELLER);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/login")
    public ResponseEntity<AuthResponse> adminLogin(@RequestBody LoginRequest request) {
        AuthResponse response = authService.authenticate(request);
        authService.validateAdminOrSuperAdmin(response.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshAccessToken(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/seller/logout")
    public ResponseEntity<Map<String, String>> sellerLogout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("message", "Seller logged out successfully"));
    }

    @PostMapping("/admin/logout")
    public ResponseEntity<Map<String, String>> adminLogout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("message", "Admin logged out successfully"));
    }

    @PostMapping("/executive/logout")
    public ResponseEntity<Map<String, String>> executiveLogout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("message", "Executive logged out successfully"));
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> logoutAll(@RequestBody Map<String, String> body) {
        String email = authService.deriveUserEmail();
        authService.logoutAll(email);
        return ResponseEntity.ok(Map.of("message", "Logged out from all devices"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@RequestBody Map<String, String> body) {
        String email = authService.getUserByEmail(
                authService.deriveUserEmail()
        ).getEmail();
        authService.changePassword(email, body.get("oldPassword"), body.get("newPassword"));
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @PostMapping("/request-password-reset")
    public ResponseEntity<Map<String, String>> requestPasswordReset(@RequestBody Map<String, String> body) {
        authService.requestPasswordReset(body.get("email"));
        return ResponseEntity.ok(Map.of("message", "OTP sent to email"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody PasswordResetRequest request) {
        authService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser() {
        Long userId = authService.deriveUserId();
        return ResponseEntity.ok(authService.getUserById(userId));
    }
}
