package com.cauverystore.controller;

import com.cauverystore.dto.AuthResponse;
import com.cauverystore.dto.LoginRequest;
import com.cauverystore.dto.PasswordResetRequest;
import com.cauverystore.dto.RefreshTokenRequest;
import com.cauverystore.dto.RegisterRequest;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.exception.RateLimitExceededException;
import com.cauverystore.service.AuthService;
import com.cauverystore.service.OtpService;
import com.cauverystore.service.RateLimiterService;
import com.cauverystore.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;
    private final CookieUtil cookieUtil;
    private final RateLimiterService rateLimiterService;

    @Value("${auth.rate-limit.login-ip-max:20}")
    private int loginIpMax;
    @Value("${auth.rate-limit.register-ip-max:5}")
    private int registerIpMax;
    @Value("${auth.rate-limit.otp-email-max:5}")
    private int otpEmailMax;
    @Value("${auth.rate-limit.otp-ip-max:10}")
    private int otpIpMax;
    @Value("${auth.rate-limit.verify-otp-email-max:10}")
    private int verifyOtpEmailMax;
    @Value("${auth.rate-limit.reset-email-max:5}")
    private int resetEmailMax;

    private void enforceRateLimit(String key, int max, long windowMillis, String message) {
        if (!rateLimiterService.allow(key, max, windowMillis)) {
            throw new RateLimitExceededException(message);
        }
    }

    private void applyRefreshCookie(HttpServletResponse response, AuthResponse authResponse) {
        if (authResponse != null && authResponse.getRefreshToken() != null) {
            cookieUtil.setRefreshTokenCookie(response, authResponse.getRefreshToken());
            authResponse.setRefreshToken(null);
        }
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        cookieUtil.clearRefreshTokenCookie(response);
    }

    private String resolveRefreshToken(HttpServletRequest request, String bodyToken) {
        String cookieToken = cookieUtil.readRefreshToken(request);
        if (cookieToken != null && !cookieToken.isEmpty()) {
            return cookieToken;
        }
        return bodyToken;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        enforceRateLimit("register:ip:" + http.getRemoteAddr(), registerIpMax, 600_000L, "Too many registration attempts. Try again later.");
        String message = authService.register(request);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        enforceRateLimit("login:ip:" + http.getRemoteAddr(), loginIpMax, 60_000L, "Too many login attempts. Try again later.");
        AuthResponse authResponse = authService.authenticate(request, http.getRemoteAddr(), http.getHeader("User-Agent"));
        applyRefreshCookie(response, authResponse);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/user/login")
    public ResponseEntity<AuthResponse> userLogin(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        enforceRateLimit("login:ip:" + http.getRemoteAddr(), loginIpMax, 60_000L, "Too many login attempts. Try again later.");
        AuthResponse authResponse = authService.authenticate(request, http.getRemoteAddr(), http.getHeader("User-Agent"));
        authService.validateUserRole(authResponse.getEmail(), Role.CUSTOMER);
        applyRefreshCookie(response, authResponse);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/seller/login")
    public ResponseEntity<AuthResponse> sellerLogin(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        enforceRateLimit("login:ip:" + http.getRemoteAddr(), loginIpMax, 60_000L, "Too many login attempts. Try again later.");
        AuthResponse authResponse = authService.authenticate(request, http.getRemoteAddr(), http.getHeader("User-Agent"));
        authService.validateUserRole(authResponse.getEmail(), Role.SELLER);
        applyRefreshCookie(response, authResponse);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/admin/login")
    public ResponseEntity<AuthResponse> adminLogin(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        enforceRateLimit("login:ip:" + http.getRemoteAddr(), loginIpMax, 60_000L, "Too many login attempts. Try again later.");
        AuthResponse authResponse = authService.authenticate(request, http.getRemoteAddr(), http.getHeader("User-Agent"));
        authService.validateAdminOrSuperAdmin(authResponse.getEmail());
        applyRefreshCookie(response, authResponse);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@RequestBody Map<String, String> body, HttpServletResponse response) {
        AuthResponse authResponse = authService.authenticateWithGoogle(body.get("credential"));
        applyRefreshCookie(response, authResponse);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody(required = false) RefreshTokenRequest request, HttpServletRequest http, HttpServletResponse response) {
        String bodyToken = request != null ? request.getRefreshToken() : null;
        RefreshTokenRequest effective = new RefreshTokenRequest();
        effective.setRefreshToken(resolveRefreshToken(http, bodyToken));
        AuthResponse authResponse = authService.refreshAccessToken(effective, http.getRemoteAddr(), http.getHeader("User-Agent"));
        applyRefreshCookie(response, authResponse);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody(required = false) RefreshTokenRequest request, HttpServletRequest http, HttpServletResponse response) {
        String token = resolveRefreshToken(http, request != null ? request.getRefreshToken() : null);
        if (token != null) {
            authService.logout(token);
        }
        clearRefreshCookie(response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/seller/logout")
    public ResponseEntity<Map<String, String>> sellerLogout(@RequestBody(required = false) RefreshTokenRequest request, HttpServletRequest http, HttpServletResponse response) {
        String token = resolveRefreshToken(http, request != null ? request.getRefreshToken() : null);
        if (token != null) {
            authService.logout(token);
        }
        clearRefreshCookie(response);
        return ResponseEntity.ok(Map.of("message", "Seller logged out successfully"));
    }

    @PostMapping("/admin/logout")
    public ResponseEntity<Map<String, String>> adminLogout(@RequestBody(required = false) RefreshTokenRequest request, HttpServletRequest http, HttpServletResponse response) {
        String token = resolveRefreshToken(http, request != null ? request.getRefreshToken() : null);
        if (token != null) {
            authService.logout(token);
        }
        clearRefreshCookie(response);
        return ResponseEntity.ok(Map.of("message", "Admin logged out successfully"));
    }

    @PostMapping("/executive/logout")
    public ResponseEntity<Map<String, String>> executiveLogout(@RequestBody(required = false) RefreshTokenRequest request, HttpServletRequest http, HttpServletResponse response) {
        String token = resolveRefreshToken(http, request != null ? request.getRefreshToken() : null);
        if (token != null) {
            authService.logout(token);
        }
        clearRefreshCookie(response);
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
        enforceRateLimit("reset:email:" + body.get("email"), resetEmailMax, 600_000L, "Too many password reset requests. Try again later.");
        authService.requestPasswordReset(body.get("email"));
        return ResponseEntity.ok(Map.of("message", "OTP sent to email"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @PostMapping("/complete-forced-reset")
    public ResponseEntity<AuthResponse> completeForcedReset(@RequestBody Map<String, String> body, HttpServletResponse response) {
        AuthResponse authResponse = authService.completeForcedPasswordReset(
                body.get("email"), body.get("oldPassword"), body.get("newPassword"));
        applyRefreshCookie(response, authResponse);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/request-password-reset-link")
    public ResponseEntity<Map<String, String>> requestPasswordResetLink(@RequestBody Map<String, String> body) {
        enforceRateLimit("resetlink:email:" + body.get("email"), resetEmailMax, 600_000L, "Too many password reset requests. Try again later.");
        authService.requestPasswordResetLink(body.get("email"));
        return ResponseEntity.ok(Map.of("message", "Reset link sent to email"));
    }

    @PostMapping("/reset-password-link")
    public ResponseEntity<Map<String, String>> resetPasswordWithLink(@RequestBody Map<String, String> body) {
        authService.resetPasswordWithLink(body.get("token"), body.get("newPassword"));
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    // ---- Email/mobile OTP (used for onboarding email verification) ----

    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(@RequestBody Map<String, String> body, HttpServletRequest http) {
        String email = body.get("email");
        String purpose = body.getOrDefault("purpose", "EMAIL_VERIFICATION");
        enforceRateLimit("otp:email:" + email, otpEmailMax, 600_000L, "Too many OTP requests. Try again later.");
        enforceRateLimit("otp:ip:" + http.getRemoteAddr(), otpIpMax, 60_000L, "Too many OTP requests. Try again later.");
        otpService.sendEmailOtp(email, purpose);
        return ResponseEntity.ok(Map.of("message", "OTP sent to email"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(@RequestBody Map<String, String> body) {
        String purpose = body.getOrDefault("purpose", "EMAIL_VERIFICATION");
        enforceRateLimit("otpverify:email:" + body.get("email"), verifyOtpEmailMax, 600_000L, "Too many OTP verification attempts. Try again later.");
        boolean verified = otpService.verifyEmailOtp(body.get("email"), purpose, body.get("otp"));
        return ResponseEntity.ok(Map.of("verified", String.valueOf(verified), "message", "OTP verified successfully"));
    }

    // ---- Two-factor authentication ----

    @PostMapping("/enable-2fa")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> enable2fa() {
        Long userId = authService.deriveUserId();
        return ResponseEntity.ok(authService.enableMfa(userId));
    }

    @PostMapping("/confirm-2fa")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> confirm2fa(@RequestBody Map<String, String> body) {
        Long userId = authService.deriveUserId();
        return ResponseEntity.ok(authService.confirmMfa(userId, body.get("otp")));
    }

    @PostMapping("/disable-2fa")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> disable2fa(@RequestBody Map<String, String> body) {
        Long userId = authService.deriveUserId();
        return ResponseEntity.ok(authService.disableMfa(userId, body.get("otp")));
    }

    @PostMapping("/login-2fa")
    public ResponseEntity<AuthResponse> login2fa(@RequestBody Map<String, String> body, HttpServletRequest http, HttpServletResponse response) {
        AuthResponse authResponse = authService.loginMfa(body.get("email"), body.get("otp"), http.getRemoteAddr(), http.getHeader("User-Agent"));
        applyRefreshCookie(response, authResponse);
        return ResponseEntity.ok(authResponse);
    }

    // ---- Sessions ----

    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listSessions() {
        Long userId = authService.deriveUserId();
        return ResponseEntity.ok(authService.listSessions(userId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> revokeSession(@PathVariable Long sessionId) {
        Long userId = authService.deriveUserId();
        authService.revokeSession(userId, sessionId);
        return ResponseEntity.ok(Map.of("message", "Session revoked"));
    }

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser() {
        Long userId = authService.deriveUserId();
        return ResponseEntity.ok(authService.getUserById(userId));
    }
}
