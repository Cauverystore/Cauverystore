package com.cauverystore.service;

import com.cauverystore.config.JwtUtil;
import com.cauverystore.dto.AuthResponse;
import com.cauverystore.dto.LoginRequest;
import com.cauverystore.dto.RefreshTokenRequest;
import com.cauverystore.dto.RegisterRequest;
import com.cauverystore.entities.EmailOtp;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.exception.AuthenticationFailedException;
import com.cauverystore.exception.TokenException;
import com.cauverystore.repository.EmailOtpRepository;
import com.cauverystore.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailOtpRepository emailOtpRepo;
    private final EmailService emailService;
    private final AuditService auditService;

    @Value("${google.client.id:}")
    private String googleClientId;

    public String register(RegisterRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new RuntimeException("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RuntimeException("Password is required");
        }
        if (userRepo.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }
        if (request.getUsername() != null && userRepo.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already taken");
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.CUSTOMER);
        user.setStatus("ACTIVE");
        user.setActive(true);
        user.setFailedLoginAttempts(0);
        user.setRefreshToken(null);
        userRepo.save(user);
        emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());
        return "Registration successful";
    }

    private static final int MAX_LOGIN_ATTEMPTS = 5;

    public AuthResponse authenticate(LoginRequest request) {
        String identifier = request.getEmail() != null ? request.getEmail() : request.getUsername();
        User user = userRepo.findByEmail(identifier);
        if (user == null) {
            user = userRepo.findByUsername(identifier);
        }
        if (user == null) {
            auditService.logLogin(identifier, "UNKNOWN", false);
            throw new AuthenticationFailedException("Invalid username or password");
        }

        if (user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
            auditService.logLogin(user.getEmail(), user.getRole() != null ? user.getRole().name() : "UNKNOWN", false);
            throw new AuthenticationFailedException("Account locked due to too many failed attempts. Try again later.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() != null
                    ? user.getFailedLoginAttempts() + 1 : 1);
            userRepo.save(user);
            int remaining = MAX_LOGIN_ATTEMPTS - user.getFailedLoginAttempts();
            auditService.logLogin(user.getEmail(), user.getRole() != null ? user.getRole().name() : "UNKNOWN", false);
            if (remaining <= 0) {
                throw new AuthenticationFailedException("Account locked due to too many failed attempts.");
            }
            throw new AuthenticationFailedException("Invalid username or password. " + remaining + " attempt(s) remaining.");
        }

        if (!"ACTIVE".equals(user.getStatus()) || !user.isActive()) {
            auditService.logLogin(user.getEmail(), user.getRole() != null ? user.getRole().name() : "UNKNOWN", false);
            if ("SUSPENDED".equals(user.getStatus()) && user.getSuspendedBy() != null) {
                User suspendedByUser = userRepo.findById(user.getSuspendedBy()).orElse(null);
                String by = suspendedByUser != null ? suspendedByUser.getEmail() : "unknown";
                String at = user.getSuspendedAt() != null ? user.getSuspendedAt().toString() : "unknown";
                throw new AuthenticationFailedException("Account has been suspended by " + by + " on " + at);
            }
            throw new AuthenticationFailedException("Account is not active");
        }

        if (request.getRole() != null && !request.getRole().isEmpty()) {
            String roleName = user.getRole() != null ? user.getRole().name() : "";
            if (!roleName.equalsIgnoreCase(request.getRole())) {
                auditService.logLogin(user.getEmail(), roleName, false);
                throw new AuthenticationFailedException("Access denied. This account does not have the required role.");
            }
        }

        user.setFailedLoginAttempts(0);
        String accessToken = jwtUtil.generateAccessToken(
                user.getEmail(), user.getId(), user.getUsername(),
                user.getRole() != null ? user.getRole().name() : "CUSTOMER");
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        user.setRefreshToken(refreshToken);
        userRepo.save(user);

        auditService.logLogin(user.getEmail(), user.getRole() != null ? user.getRole().name() : "CUSTOMER", true);

        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole() != null ? user.getRole().name() : "CUSTOMER");
        response.setUserId(user.getId());
        return response;
    }

    @Transactional
    public AuthResponse authenticateWithGoogle(String idTokenString) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new AuthenticationFailedException("Google sign-in is not configured");
        }
        GoogleIdToken.Payload payload;
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new AuthenticationFailedException("Invalid Google sign-in token");
            }
            payload = idToken.getPayload();
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException e) {
            throw new AuthenticationFailedException("Invalid Google sign-in token");
        }

        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new AuthenticationFailedException("Google account email is not verified");
        }
        String email = payload.getEmail();
        String name = (String) payload.get("name");

        User user = userRepo.findByEmail(email);
        if (user == null) {
            user = new User();
            user.setFullName(name != null ? name : email);
            user.setUsername(email);
            user.setEmail(email);
            user.setPassword(null);
            user.setRole(Role.CUSTOMER);
            user.setStatus("ACTIVE");
            user.setActive(true);
            user.setFailedLoginAttempts(0);
            userRepo.save(user);
            emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());
        }

        if (!"ACTIVE".equals(user.getStatus()) || !user.isActive()) {
            auditService.logLogin(user.getEmail(), user.getRole() != null ? user.getRole().name() : "UNKNOWN", false);
            throw new AuthenticationFailedException("Account is not active");
        }

        user.setFailedLoginAttempts(0);
        String accessToken = jwtUtil.generateAccessToken(
                user.getEmail(), user.getId(), user.getUsername(),
                user.getRole() != null ? user.getRole().name() : "CUSTOMER");
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        user.setRefreshToken(refreshToken);
        userRepo.save(user);

        auditService.logLogin(user.getEmail(), user.getRole() != null ? user.getRole().name() : "CUSTOMER", true);

        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole() != null ? user.getRole().name() : "CUSTOMER");
        response.setUserId(user.getId());
        return response;
    }

    @Transactional
    public AuthResponse refreshAccessToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();
        if (token == null || token.isEmpty()) {
            throw new TokenException("Refresh token is required");
        }
        if (!jwtUtil.validateToken(token)) {
            throw new TokenException("Invalid or expired refresh token");
        }
        String email = jwtUtil.getEmailFromToken(token);
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new TokenException("User not found for refresh token");
        }
        if (!token.equals(user.getRefreshToken())) {
            throw new TokenException("Refresh token has been revoked or does not match");
        }

        String newAccessToken = jwtUtil.generateAccessToken(
                user.getEmail(), user.getId(), user.getUsername(),
                user.getRole() != null ? user.getRole().name() : "CUSTOMER");
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getEmail());

    user.setRefreshToken(newRefreshToken);
    userRepo.saveAndFlush(user);

        AuthResponse response = new AuthResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(newRefreshToken);
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole() != null ? user.getRole().name() : "CUSTOMER");
        response.setUserId(user.getId());
        return response;
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new RuntimeException("Refresh token is required");
        }
        User user = userRepo.findByRefreshToken(refreshToken);
        if (user != null) {
            user.setRefreshToken(null);
            userRepo.save(user);
        }
    }

    public void logoutAll(String email) {
        User user = userRepo.findByEmail(email);
        if (user != null) {
            user.setRefreshToken(null);
            userRepo.save(user);
        }
    }

    public void changePassword(String email, String oldPassword, String newPassword) {
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new AuthenticationFailedException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
    }

    public void requestPasswordReset(String email) {
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found with email: " + email);
        }

        String otp = String.format("%06d", new Random().nextInt(999999));
        EmailOtp emailOtp = emailOtpRepo.findByEmail(email);
        if (emailOtp == null) {
            emailOtp = new EmailOtp();
            emailOtp.setEmail(email);
        }
        emailOtp.setOtp(otp);
        emailOtp.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        emailOtp.setUsed(false);
        emailOtpRepo.save(emailOtp);
        emailService.sendOtpEmail(user.getEmail(), otp);
    }

    public void resetPassword(String email, String otp, String newPassword) {
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        EmailOtp emailOtp = emailOtpRepo.findByEmail(email);
        if (emailOtp == null || !emailOtp.getOtp().equals(otp)) {
            throw new AuthenticationFailedException("Invalid OTP");
        }
        if (emailOtp.isUsed()) {
            throw new AuthenticationFailedException("OTP has already been used");
        }
        if (emailOtp.getExpiresAt() != null && emailOtp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthenticationFailedException("OTP has expired");
        }
        emailOtp.setUsed(true);
        emailOtpRepo.save(emailOtp);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
    }

    public User getUserById(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    public User getUserByEmail(String email) {
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found with email: " + email);
        }
        return user;
    }

    public void validateUserRole(String email, Role expectedRole) {
        User user = getUserByEmail(email);
        if (user.getRole() != expectedRole) {
            throw new AuthenticationFailedException(
                    "Access denied. " + expectedRole.name() + " credentials required.");
        }
    }

    public void validateAdminOrSuperAdmin(String email) {
        User user = getUserByEmail(email);
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.SUPER_ADMIN) {
            throw new AuthenticationFailedException("Access denied. Admin or Super Admin credentials required.");
        }
    }

    public Long deriveUserId() {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (details instanceof Long) {
            return (Long) details;
        }
        throw new RuntimeException("Unable to derive user ID from security context");
    }

    public String deriveUserRole() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            return auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        }
        throw new RuntimeException("Unable to derive user role from security context");
    }

    public String deriveUserEmail() {
        return getUserById(deriveUserId()).getEmail();
    }
}
