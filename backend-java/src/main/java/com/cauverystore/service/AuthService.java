package com.cauverystore.service;

import com.cauverystore.config.JwtUtil;
import com.cauverystore.dto.AuthResponse;
import com.cauverystore.dto.LoginRequest;
import com.cauverystore.dto.RefreshTokenRequest;
import com.cauverystore.dto.RegisterRequest;
import com.cauverystore.entities.EmailOtp;
import com.cauverystore.entities.PasswordResetToken;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.Session;
import com.cauverystore.entities.User;
import com.cauverystore.exception.AuthenticationFailedException;
import com.cauverystore.exception.PasswordResetRequiredException;
import com.cauverystore.exception.TokenException;
import com.cauverystore.repository.EmailOtpRepository;
import com.cauverystore.repository.PasswordResetTokenRepository;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.util.CryptoUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailOtpRepository emailOtpRepo;
    private final PasswordResetTokenRepository passwordResetTokenRepo;
    private final EmailService emailService;
    private final AuditService auditService;
    private final SessionService sessionService;
    private final PasswordPolicyService passwordPolicyService;
    private final TotpService totpService;
    private final OtpService otpService;
    private final CryptoUtil cryptoUtil;

    @Value("${google.client.id:}")
    private String googleClientId;
    @Value("${frontend.url:https://cauverystore.in}")
    private String frontendUrl;

    // Email lookups are case-sensitive at the DB level, but users don't treat email
    // case as meaningful - registering as "John@Gmail.com" and logging in as
    // "john@gmail.com" must be the same account, not a lookup miss.
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public String register(RegisterRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new RuntimeException("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RuntimeException("Password is required");
        }
        passwordPolicyService.validateComplexity(request.getPassword());
        String email = normalizeEmail(request.getEmail());
        if (userRepo.existsByEmail(email)) {
            throw new RuntimeException("Email already registered");
        }

        // The frontend derives username from the email's local part (e.g. "alice" from
        // alice@gmail.com), which different real people can share across providers. That's
        // an implementation detail invisible to the user, so a collision must never block
        // registration - just make it unique instead of rejecting a genuinely new signup.
        String username = request.getUsername();
        if (username != null && !username.isBlank() && userRepo.existsByUsername(username)) {
            String base = username;
            int suffix = 1;
            do {
                username = base + suffix;
                suffix++;
            } while (userRepo.existsByUsername(username));
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(request.getPhone());
        user.setRole(Role.CUSTOMER);
        user.setStatus("ACTIVE");
        user.setActive(true);
        user.setFailedLoginAttempts(0);
        user.setRefreshToken(null);
        userRepo.save(user);
        passwordPolicyService.recordNewPassword(user, request.getPassword());
        userRepo.save(user);
        emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());
        return "Registration successful";
    }

    private static final int MAX_LOGIN_ATTEMPTS = 5;

    /**
     * How long a lockout lasts. Long enough to make guessing passwords pointless, short enough
     * that a customer who mistyped theirs is not shut out of the shop for the evening.
     */
    private static final int LOCKOUT_MINUTES = 15;

    public AuthResponse authenticate(LoginRequest request) {
        return authenticate(request, null, null);
    }

    public AuthResponse authenticate(LoginRequest request, String ipAddress, String userAgent) {
        String identifier = request.getEmail() != null ? request.getEmail() : request.getUsername();
        User user = userRepo.findByEmail(normalizeEmail(identifier));
        if (user == null) {
            user = userRepo.findByUsername(identifier);
        }
        if (user == null) {
            auditService.logLogin(identifier, "UNKNOWN", false);
            throw new AuthenticationFailedException("Invalid username or password");
        }
        log.info("Login attempt: identifier={}, resolved id={}, email={}, failedLoginAttempts={}", identifier, user.getId(), user.getEmail(), user.getFailedLoginAttempts());

        // A lockout that never lifts is not a lockout, it is a closed account. The counter used
        // to reset only on a successful login, which a locked-out person cannot reach, so five
        // wrong attempts locked someone out for good while the message said "try again later".
        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(LocalDateTime.now())) {
                long minutes = Math.max(1, java.time.Duration.between(
                        LocalDateTime.now(), user.getLockedUntil()).toMinutes() + 1);
                log.info("Login lockout: id={}, email={}, until={}", user.getId(), user.getEmail(), user.getLockedUntil());
                auditService.logLogin(user.getEmail(), user.getRole() != null ? user.getRole().name() : "UNKNOWN", false);
                throw new AuthenticationFailedException("Account locked due to too many failed "
                        + "attempts. Try again in " + minutes + " minute(s), or reset your password "
                        + "to unlock it now.");
            }
            // Served its time - start the count again rather than leaving them one attempt from
            // being locked straight back out.
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepo.save(user);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() != null
                    ? user.getFailedLoginAttempts() + 1 : 1);
            int remaining = MAX_LOGIN_ATTEMPTS - user.getFailedLoginAttempts();
            if (remaining <= 0) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
            }
            userRepo.save(user);
            auditService.logLogin(user.getEmail(), user.getRole() != null ? user.getRole().name() : "UNKNOWN", false);
            if (remaining <= 0) {
                throw new AuthenticationFailedException("Account locked due to too many failed "
                        + "attempts. Try again in " + LOCKOUT_MINUTES + " minutes, or reset your "
                        + "password to unlock it now.");
            }
            throw new AuthenticationFailedException("Invalid username or password. " + remaining + " attempt(s) remaining.");
        }

        // A suspension is a wind-down, not a lockout. The person still owes or is owed something -
        // a seller has orders to ship, a buyer has a right to return what arrives - and locking
        // them out would strand exactly those orders. They sign in and find they cannot start
        // anything new. BLOCKED is the hard stop and does refuse entry here.
        boolean windingDown = "SUSPENDED".equals(user.getStatus());
        if ((!"ACTIVE".equals(user.getStatus()) && !windingDown) || (!user.isActive() && !windingDown)) {
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
            java.util.List<String> capabilities = computeRoles(user);
            if (capabilities.stream().noneMatch(r -> r.equalsIgnoreCase(request.getRole()))) {
                auditService.logLogin(user.getEmail(), capabilities.get(0), false);
                throw new AuthenticationFailedException("Access denied. This account does not have the required role.");
            }
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());

        passwordPolicyService.applyExpiry(user);
        if (Boolean.TRUE.equals(user.getMustResetPassword())) {
            userRepo.save(user);
            auditService.logLogin(user.getEmail(), user.getRole() != null ? user.getRole().name() : "CUSTOMER", true);
            throw new PasswordResetRequiredException(user.getEmail());
        }

        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            userRepo.save(user);
            return mfaRequiredResponse(user, request.getRole());
        }

        return issueTokens(user, request.getRole(), ipAddress, userAgent);
    }

    private AuthResponse mfaRequiredResponse(User user, String preferredRole) {
        List<String> roles = computeRoles(user);
        if (preferredRole != null) {
            String match = roles.stream().filter(r -> r.equalsIgnoreCase(preferredRole)).findFirst().orElse(null);
            if (match != null) {
                roles.remove(match);
                roles.add(0, match);
            }
        }
        AuthResponse response = new AuthResponse();
        response.setMfaRequired(true);
        response.setMfaMethod(user.getMfaMethod() != null ? user.getMfaMethod() : "TOTP");
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(roles.get(0));
        response.setRoles(roles);
        response.setUserId(user.getId());
        response.setMessage("Two-factor verification required. Enter the code from your authenticator app.");
        return response;
    }

    // A user's stored role already reliably means "has this capability" - it's only ever
    // treated as EXCLUSIVE (instead of additive) that's wrong. Everyone who isn't staff is
    // implicitly always a CUSTOMER too, so a seller can log in and shop under the same
    // account instead of losing customer access the moment they become a seller.
    private java.util.List<String> computeRoles(User user) {
        Role r = user.getRole();
        if (r == Role.ADMIN || r == Role.SUPER_ADMIN || r == Role.EXECUTIVE) {
            return java.util.List.of(r.name());
        }
        if (r == Role.SELLER) {
            return new java.util.ArrayList<>(java.util.List.of(Role.SELLER.name(), Role.CUSTOMER.name()));
        }
        return java.util.List.of(Role.CUSTOMER.name());
    }

    public boolean userHasRole(User user, String role) {
        return computeRoles(user).stream().anyMatch(r -> r.equalsIgnoreCase(role));
    }

    private AuthResponse issueTokens(User user) {
        return issueTokens(user, null, null, null);
    }

    private AuthResponse issueTokens(User user, String preferredRole, String ipAddress, String userAgent) {
        java.util.List<String> roles = new java.util.ArrayList<>(computeRoles(user));
        if (preferredRole != null) {
            String match = roles.stream().filter(r -> r.equalsIgnoreCase(preferredRole)).findFirst().orElse(null);
            if (match != null) {
                roles.remove(match);
                roles.add(0, match);
            }
        }

        String accessToken = jwtUtil.generateAccessToken(
                user.getEmail(), user.getId(), user.getUsername(), roles, user.getTokenVersion());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        user.setRefreshToken(refreshToken);
        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(LocalDateTime.now());
        userRepo.save(user);

        sessionService.createSession(user.getId(), refreshToken, ipAddress, userAgent, deriveDeviceLabel(userAgent));

        auditService.logLogin(user.getEmail(), roles.get(0), true);

        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(roles.get(0));
        response.setRoles(roles);
        response.setUserId(user.getId());
        response.setMfaRequired(false);
        // Google sign-in only ever supplies name + email - a customer account created that way
        // has no phone on file unless they fill it in afterward, unlike the manual registration
        // form where phone is a required field from the start.
        response.setProfileIncomplete(user.getPhone() == null || user.getPhone().isBlank());
        return response;
    }

    private String deriveDeviceLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        if (userAgent.contains("Mobile") || userAgent.contains("Android") || userAgent.contains("iPhone")) {
            return "Mobile";
        }
        if (userAgent.contains("Edg")) {
            return "Edge browser";
        }
        if (userAgent.contains("Chrome")) {
            return "Chrome browser";
        }
        if (userAgent.contains("Firefox")) {
            return "Firefox browser";
        }
        if (userAgent.contains("Safari")) {
            return "Safari browser";
        }
        return "Browser";
    }

    @Transactional
    public AuthResponse completeForcedPasswordReset(String email, String oldPassword, String newPassword) {
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new AuthenticationFailedException("Invalid request");
        }
        if (!Boolean.TRUE.equals(user.getMustResetPassword())) {
            throw new AuthenticationFailedException("Password reset is not required for this account");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new AuthenticationFailedException("Current password is incorrect");
        }
        passwordPolicyService.validateComplexity(newPassword);
        passwordPolicyService.checkHistory(user, newPassword);
        passwordPolicyService.recordNewPassword(user, newPassword);
        user.setMustResetPassword(false);
        user.setFailedLoginAttempts(0);
        user.invalidateSessions();
        sessionService.revokeAllForUser(user.getId());
        userRepo.save(user);
        auditService.logAccountAction("PASSWORD_RESET_COMPLETED", email, user.getId());
        emailService.sendPasswordResetConfirmation(user.getEmail());
        return issueTokens(user);
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
        log.info("Google sign-in: email={}, existingUserFound={}", email, user != null);
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
            user = userRepo.save(user);
            log.info("Google sign-in: created new user id={}, status={}, active={}", user.getId(), user.getStatus(), user.isActive());
            emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());
        }

        log.info("Google sign-in: pre-check id={}, status={}, active={}", user.getId(), user.getStatus(), user.isActive());
        if (!"ACTIVE".equals(user.getStatus()) || !user.isActive()) {
            auditService.logLogin(user.getEmail(), user.getRole() != null ? user.getRole().name() : "UNKNOWN", false);
            throw new AuthenticationFailedException("Account is not active");
        }

        user.setFailedLoginAttempts(0);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refreshAccessToken(RefreshTokenRequest request) {
        return refreshAccessToken(request, null, null);
    }

    @Transactional
    public AuthResponse refreshAccessToken(RefreshTokenRequest request, String ipAddress, String userAgent) {
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

        // Authoritative check: the token must belong to an ACTIVE, unexpired session.
        Session session = sessionService.findActiveByRefreshToken(token);
        if (session == null) {
            // Backwards compatibility: sessions created before this feature have no row yet.
            if (token.equals(user.getRefreshToken())) {
                session = null;
            } else {
                throw new TokenException("Refresh token has been revoked or does not match");
            }
        }

        String newAccessToken = jwtUtil.generateAccessToken(
                user.getEmail(), user.getId(), user.getUsername(),
                computeRoles(user), user.getTokenVersion());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        if (session != null) {
            sessionService.rotateSession(session, newRefreshToken);
        }
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
        Session session = sessionService.findActiveByRefreshToken(refreshToken);
        if (session != null) {
            sessionService.revokeSession(session);
            User user = userRepo.findById(session.getUserId()).orElse(null);
            if (user != null) {
                if (refreshToken.equals(user.getRefreshToken())) {
                    user.setRefreshToken(null);
                    userRepo.save(user);
                }
                auditService.logLogout(user.getEmail());
            }
            return;
        }
        User user = userRepo.findByRefreshToken(refreshToken);
        if (user != null) {
            user.setRefreshToken(null);
            userRepo.save(user);
            auditService.logLogout(user.getEmail());
        }
    }

    public void logoutAll(String email) {
        User user = userRepo.findByEmail(email);
        if (user != null) {
            sessionService.revokeAllForUser(user.getId());
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
        passwordPolicyService.validateComplexity(newPassword);
        passwordPolicyService.checkHistory(user, newPassword);
        passwordPolicyService.recordNewPassword(user, newPassword);
        userRepo.save(user);
        auditService.logAccountAction("PASSWORD_CHANGED", email, user.getId());
        emailService.sendPasswordResetConfirmation(user.getEmail());
    }

    private static final long OTP_REQUEST_COOLDOWN_SECONDS = 60;
    private static final int MAX_OTP_VERIFY_ATTEMPTS = 5;

    public void requestPasswordReset(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found with email: " + email);
        }

        EmailOtp emailOtp = emailOtpRepo.findByEmailAndPurpose(email, "PASSWORD_RESET");
        if (emailOtp != null && emailOtp.getLastRequestedAt() != null
                && emailOtp.getLastRequestedAt().isAfter(LocalDateTime.now().minusSeconds(OTP_REQUEST_COOLDOWN_SECONDS))) {
            throw new AuthenticationFailedException("Please wait a minute before requesting another code.");
        }

        String otp = String.format("%06d", new Random().nextInt(999999));
        if (emailOtp == null) {
            emailOtp = new EmailOtp();
            emailOtp.setEmail(email);
            emailOtp.setPurpose("PASSWORD_RESET");
        }
        emailOtp.setOtp(otp);
        emailOtp.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        emailOtp.setUsed(false);
        emailOtp.setVerifyAttempts(0);
        emailOtp.setLastRequestedAt(LocalDateTime.now());
        emailOtpRepo.save(emailOtp);
        auditService.logAccountAction("PASSWORD_RESET_REQUESTED", email, user.getId());
        emailService.sendOtpEmail(user.getEmail(), otp);
    }

    public void resetPassword(String rawEmail, String otp, String newPassword) {
        String email = normalizeEmail(rawEmail);
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        log.info("Password reset: id={}, email={}, failedLoginAttemptsBefore={}", user.getId(), user.getEmail(), user.getFailedLoginAttempts());
        EmailOtp emailOtp = emailOtpRepo.findByEmailAndPurpose(email, "PASSWORD_RESET");
        if (emailOtp == null) {
            throw new AuthenticationFailedException("Invalid OTP");
        }
        if (emailOtp.isUsed()) {
            throw new AuthenticationFailedException("OTP has already been used");
        }
        if (emailOtp.getExpiresAt() != null && emailOtp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthenticationFailedException("OTP has expired");
        }
        int attempts = emailOtp.getVerifyAttempts() != null ? emailOtp.getVerifyAttempts() : 0;
        if (attempts >= MAX_OTP_VERIFY_ATTEMPTS) {
            throw new AuthenticationFailedException("Too many incorrect attempts. Please request a new code.");
        }
        if (!emailOtp.getOtp().equals(otp)) {
            emailOtp.setVerifyAttempts(attempts + 1);
            emailOtpRepo.save(emailOtp);
            throw new AuthenticationFailedException("Invalid OTP");
        }
        emailOtp.setUsed(true);
        emailOtpRepo.save(emailOtp);

        passwordPolicyService.validateComplexity(newPassword);
        passwordPolicyService.checkHistory(user, newPassword);
        passwordPolicyService.recordNewPassword(user, newPassword);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setMustResetPassword(false);
        user.invalidateSessions();
        sessionService.revokeAllForUser(user.getId());
        userRepo.save(user);
        log.info("Password reset: id={}, email={}, failedLoginAttemptsAfterSave={}", user.getId(), user.getEmail(), user.getFailedLoginAttempts());
        auditService.logAccountAction("PASSWORD_RESET_COMPLETED", email, user.getId());
        emailService.sendPasswordResetConfirmation(user.getEmail());
    }

    public void requestPasswordResetLink(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found with email: " + email);
        }

        PasswordResetToken resetToken = passwordResetTokenRepo.findByEmail(email);
        if (resetToken != null && resetToken.getLastRequestedAt() != null
                && resetToken.getLastRequestedAt().isAfter(LocalDateTime.now().minusSeconds(OTP_REQUEST_COOLDOWN_SECONDS))) {
            throw new AuthenticationFailedException("Please wait a minute before requesting another link.");
        }

        String token = java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "");
        if (resetToken == null) {
            resetToken = new PasswordResetToken();
            resetToken.setEmail(email);
        }
        resetToken.setToken(token);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        resetToken.setUsed(false);
        resetToken.setLastRequestedAt(LocalDateTime.now());
        passwordResetTokenRepo.save(resetToken);

        auditService.logAccountAction("PASSWORD_RESET_LINK_REQUESTED", email, user.getId());
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        emailService.sendPasswordResetLink(user.getEmail(), resetUrl);
    }

    public void resetPasswordWithLink(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationFailedException("Invalid or expired reset link");
        }
        PasswordResetToken resetToken = passwordResetTokenRepo.findByToken(token);
        if (resetToken == null) {
            throw new AuthenticationFailedException("Invalid or expired reset link");
        }
        if (resetToken.isUsed()) {
            throw new AuthenticationFailedException("This reset link has already been used");
        }
        if (resetToken.getExpiresAt() != null && resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthenticationFailedException("This reset link has expired");
        }
        User user = userRepo.findByEmail(resetToken.getEmail());
        if (user == null) {
            throw new AuthenticationFailedException("Invalid or expired reset link");
        }

        resetToken.setUsed(true);
        passwordResetTokenRepo.save(resetToken);

        passwordPolicyService.validateComplexity(newPassword);
        passwordPolicyService.checkHistory(user, newPassword);
        passwordPolicyService.recordNewPassword(user, newPassword);
        user.setFailedLoginAttempts(0);
        user.setMustResetPassword(false);
        user.invalidateSessions();
        sessionService.revokeAllForUser(user.getId());
        userRepo.save(user);

        auditService.logAccountAction("PASSWORD_RESET_COMPLETED", resetToken.getEmail(), user.getId());
        emailService.sendPasswordResetConfirmation(user.getEmail());
    }

    // ---- Two-factor authentication ----

    public Map<String, Object> enableMfa(Long userId) {
        User user = getUserById(userId);
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new RuntimeException("Two-factor authentication is already enabled");
        }
        String secret = totpService.generateSecret();
        user.setMfaSecret(cryptoUtil.encrypt(secret));
        user.setMfaMethod("TOTP");
        userRepo.save(user);
        String account = user.getEmail() != null ? user.getEmail() : user.getUsername();
        return Map.of(
                "secret", secret,
                "otpauthUrl", totpService.generateQrUri("Cauvery Store", account, secret),
                "message", "Scan the QR code with your authenticator app, then confirm with a code");
    }

    @Transactional
    public Map<String, Object> confirmMfa(Long userId, String otp) {
        User user = getUserById(userId);
        String secret = cryptoUtil.decrypt(user.getMfaSecret());
        if (!totpService.verify(otp, secret)) {
            throw new AuthenticationFailedException("Invalid authenticator code");
        }
        user.setMfaEnabled(true);
        userRepo.save(user);
        auditService.logAccountAction("TWO_FA_ENABLED", user.getEmail(), user.getId());
        return Map.of("enabled", true, "message", "Two-factor authentication enabled");
    }

    @Transactional
    public Map<String, Object> disableMfa(Long userId, String otp) {
        User user = getUserById(userId);
        String secret = cryptoUtil.decrypt(user.getMfaSecret());
        if (!totpService.verify(otp, secret)) {
            throw new AuthenticationFailedException("Invalid authenticator code");
        }
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepo.save(user);
        auditService.logAccountAction("TWO_FA_DISABLED", user.getEmail(), user.getId());
        return Map.of("enabled", false, "message", "Two-factor authentication disabled");
    }

    public AuthResponse loginMfa(String rawEmail, String otp, String ipAddress, String userAgent) {
        String email = normalizeEmail(rawEmail);
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new AuthenticationFailedException("Invalid request");
        }
        if (!Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new AuthenticationFailedException("Two-factor authentication is not enabled for this account");
        }
        if (!totpService.verify(otp, cryptoUtil.decrypt(user.getMfaSecret()))) {
            auditService.logLogin(user.getEmail(), user.getRole() != null ? user.getRole().name() : "UNKNOWN", false);
            throw new AuthenticationFailedException("Invalid authenticator code");
        }
        return issueTokens(user, null, ipAddress, userAgent);
    }

    // ---- Sessions ----

    public List<Session> listSessions(Long userId) {
        return sessionService.listActive(userId);
    }

    public void revokeSession(Long userId, Long sessionId) {
        if (!sessionService.isOwner(userId, sessionId)) {
            throw new RuntimeException("Session does not belong to this user");
        }
        sessionService.revokeSession(sessionService.listActive(userId).stream()
                .filter(s -> s.getId().equals(sessionId)).findFirst().orElse(null));
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
        if (!userHasRole(user, expectedRole.name())) {
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
