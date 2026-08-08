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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private EmailOtpRepository emailOtpRepo;
    @Mock private com.cauverystore.repository.PasswordResetTokenRepository passwordResetTokenRepo;
    @Mock private EmailService emailService;
    @Mock private AuditService auditService;
    @Mock private SessionService sessionService;
    @Mock private PasswordPolicyService passwordPolicyService;
    @Mock private TotpService totpService;
    @Mock private OtpService otpService;
    @Mock private com.cauverystore.util.CryptoUtil cryptoUtil;

    @InjectMocks
    private AuthService authService;

    private User user;
    private LoginRequest loginReq;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("customer");
        user.setEmail("customer@test.com");
        user.setPassword("$2a$encodedPassword");
        user.setFullName("Test Customer");
        user.setRole(Role.CUSTOMER);
        user.setStatus("ACTIVE");
        user.setActive(true);

        loginReq = new LoginRequest();
        loginReq.setEmail("customer@test.com");
        loginReq.setPassword("admin123");
    }

    @Test
    void authenticate_shouldReturnTokens_whenValidCredentials() {
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(passwordEncoder.matches("admin123", user.getPassword())).thenReturn(true);
        when(jwtUtil.generateAccessToken(anyString(), anyLong(), anyString(), anyList(), any()))
                .thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh-token");

        AuthResponse response = authService.authenticate(loginReq);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("customer", response.getUsername());
        assertEquals("customer@test.com", response.getEmail());
        assertEquals("CUSTOMER", response.getRole());
        assertEquals(1L, response.getUserId());
        verify(userRepo).save(user);
    }

    @Test
    void authenticate_shouldSucceedAsCustomer_whenSellerAccountSelectsCustomerRole() {
        user.setRole(Role.SELLER);
        loginReq.setRole("customer");
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(passwordEncoder.matches("admin123", user.getPassword())).thenReturn(true);
        when(jwtUtil.generateAccessToken(anyString(), anyLong(), anyString(), anyList(), any()))
                .thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh-token");

        AuthResponse response = authService.authenticate(loginReq);

        assertEquals("CUSTOMER", response.getRole());
        assertTrue(response.getRoles().contains("CUSTOMER"));
        assertTrue(response.getRoles().contains("SELLER"));
    }

    @Test
    void authenticate_shouldReject_whenPlainCustomerAccountSelectsSellerRole() {
        loginReq.setRole("seller");
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(passwordEncoder.matches("admin123", user.getPassword())).thenReturn(true);

        assertThrows(AuthenticationFailedException.class, () -> authService.authenticate(loginReq));
    }

    @Test
    void authenticate_shouldThrow401_whenInvalidPassword() {
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(passwordEncoder.matches("admin123", user.getPassword())).thenReturn(false);

        assertThrows(AuthenticationFailedException.class, () -> authService.authenticate(loginReq));
    }

    @Test
    void authenticate_shouldThrow401_whenUserNotFound() {
        when(userRepo.findByEmail("customer@test.com")).thenReturn(null);
        when(userRepo.findByUsername("customer@test.com")).thenReturn(null);

        assertThrows(AuthenticationFailedException.class, () -> authService.authenticate(loginReq));
    }

    @Test
    void authenticate_shouldThrow401_whenAccountInactive() {
        user.setActive(false);
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(passwordEncoder.matches("admin123", user.getPassword())).thenReturn(true);

        assertThrows(AuthenticationFailedException.class, () -> authService.authenticate(loginReq));
    }

    @Test
    void authenticate_shouldFallbackToUsername_whenEmailIsNull() {
        loginReq.setEmail(null);
        loginReq.setUsername("customer");
        when(userRepo.findByEmail("customer")).thenReturn(null);
        when(userRepo.findByUsername("customer")).thenReturn(user);
        when(passwordEncoder.matches("admin123", user.getPassword())).thenReturn(true);
        when(jwtUtil.generateAccessToken(anyString(), anyLong(), anyString(), anyList(), any()))
                .thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh-token");

        AuthResponse response = authService.authenticate(loginReq);

        assertEquals("access-token", response.getAccessToken());
    }

    @Test
    void register_shouldSucceed_whenEmailAndUsernameAreUnique() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@test.com");
        req.setUsername("newuser");
        req.setPassword("password");
        req.setFullName("New User");

        when(userRepo.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepo.existsByUsername("newuser")).thenReturn(false);

        String result = authService.register(req);

        assertEquals("Registration successful", result);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        // register() saves twice: once to persist the user, then again after
        // passwordPolicyService.recordNewPassword() sets the hashed password on it.
        verify(userRepo, times(2)).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(Role.CUSTOMER, saved.getRole());
        assertEquals("ACTIVE", saved.getStatus());
        assertTrue(saved.isActive());
        assertEquals("new@test.com", saved.getEmail());
        verify(emailService).sendWelcomeEmail("new@test.com", "New User");
    }

    @Test
    void register_shouldThrow_whenEmailExists() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("existing@test.com");
        req.setUsername("newuser");
        req.setPassword("password");

        when(userRepo.existsByEmail("existing@test.com")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> authService.register(req));
        verify(userRepo, never()).save(any());
    }

    @Test
    void register_shouldAutoSuffixUsername_whenUsernameTaken() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@test.com");
        req.setUsername("existing");
        req.setPassword("password");

        when(userRepo.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepo.existsByUsername("existing")).thenReturn(true);
        when(userRepo.existsByUsername("existing1")).thenReturn(false);

        authService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo, times(2)).save(captor.capture());
        assertEquals("existing1", captor.getValue().getUsername());
    }

    @Test
    void refreshToken_shouldReturnNewTokens_whenValidRefreshToken() {
        user.setRefreshToken("old-refresh-token");
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("old-refresh-token");

        when(jwtUtil.validateToken("old-refresh-token")).thenReturn(true);
        when(jwtUtil.getEmailFromToken("old-refresh-token")).thenReturn("customer@test.com");
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(jwtUtil.generateAccessToken(anyString(), anyLong(), anyString(), anyList(), any()))
                .thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("new-refresh-token");

        AuthResponse response = authService.refreshAccessToken(req);

        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        verify(userRepo).saveAndFlush(user);
    }

    @Test
    void refreshToken_shouldThrowTokenException_whenNullToken() {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(null);

        assertThrows(TokenException.class, () -> authService.refreshAccessToken(req));
    }

    @Test
    void refreshToken_shouldThrowTokenException_whenTokenInvalid() {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("invalid-token");

        when(jwtUtil.validateToken("invalid-token")).thenReturn(false);

        assertThrows(TokenException.class, () -> authService.refreshAccessToken(req));
    }

    @Test
    void refreshToken_shouldThrowTokenException_whenTokenMismatch() {
        user.setRefreshToken("different-token");
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("old-refresh-token");

        when(jwtUtil.validateToken("old-refresh-token")).thenReturn(true);
        when(jwtUtil.getEmailFromToken("old-refresh-token")).thenReturn("customer@test.com");
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);

        assertThrows(TokenException.class, () -> authService.refreshAccessToken(req));
    }

    @Test
    void logout_shouldClearStoredToken() {
        user.setRefreshToken("active-refresh-token");
        when(userRepo.findByRefreshToken("active-refresh-token")).thenReturn(user);

        authService.logout("active-refresh-token");

        assertNull(user.getRefreshToken());
        verify(userRepo).save(user);
    }

    @Test
    void logout_shouldThrow_whenTokenIsNull() {
        assertThrows(RuntimeException.class, () -> authService.logout(null));
    }

    @Test
    void changePassword_shouldSucceed_whenOldPasswordMatches() {
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(passwordEncoder.matches("oldPass", user.getPassword())).thenReturn(true);

        authService.changePassword("customer@test.com", "oldPass", "newPass");

        // Hashing/history are owned by PasswordPolicyService (mocked here), so assert the
        // delegation rather than the resulting hash.
        verify(passwordPolicyService).validateComplexity("newPass");
        verify(passwordPolicyService).checkHistory(user, "newPass");
        verify(passwordPolicyService).recordNewPassword(user, "newPass");
        verify(userRepo).save(user);
    }

    @Test
    void changePassword_shouldThrow401_whenOldPasswordWrong() {
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(passwordEncoder.matches("wrongPass", user.getPassword())).thenReturn(false);

        assertThrows(AuthenticationFailedException.class,
                () -> authService.changePassword("customer@test.com", "wrongPass", "newPass"));
    }

    @Test
    void requestPasswordReset_shouldCreateOtp_whenUserExists() {
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);

        authService.requestPasswordReset("customer@test.com");

        ArgumentCaptor<EmailOtp> captor = ArgumentCaptor.forClass(EmailOtp.class);
        verify(emailOtpRepo).save(captor.capture());
        EmailOtp saved = captor.getValue();
        assertEquals("customer@test.com", saved.getEmail());
        assertNotNull(saved.getOtp());
        assertEquals(6, saved.getOtp().length());
        assertFalse(saved.isUsed());
    }

    @Test
    void resetPassword_shouldSucceed_whenValidOtp() {
        EmailOtp otp = new EmailOtp();
        otp.setEmail("customer@test.com");
        otp.setOtp("123456");
        otp.setUsed(false);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(emailOtpRepo.findByEmailAndPurpose("customer@test.com", "PASSWORD_RESET")).thenReturn(otp);

        authService.resetPassword("customer@test.com", "123456", "newPass");

        verify(passwordPolicyService).recordNewPassword(user, "newPass");
        assertTrue(otp.isUsed());
        verify(emailOtpRepo).save(otp);
        verify(userRepo).save(user);
    }

    @Test
    void resetPassword_shouldThrow401_whenInvalidOtp() {
        EmailOtp otp = new EmailOtp();
        otp.setEmail("customer@test.com");
        otp.setOtp("999999");
        otp.setUsed(false);

        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(emailOtpRepo.findByEmailAndPurpose("customer@test.com", "PASSWORD_RESET")).thenReturn(otp);

        assertThrows(AuthenticationFailedException.class,
                () -> authService.resetPassword("customer@test.com", "123456", "newPass"));
    }

    @Test
    void resetPassword_shouldThrow401_whenOtpExpired() {
        EmailOtp otp = new EmailOtp();
        otp.setEmail("customer@test.com");
        otp.setOtp("123456");
        otp.setUsed(false);
        otp.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(emailOtpRepo.findByEmailAndPurpose("customer@test.com", "PASSWORD_RESET")).thenReturn(otp);

        assertThrows(AuthenticationFailedException.class,
                () -> authService.resetPassword("customer@test.com", "123456", "newPass"));
    }

    @Test
    void authenticate_shouldLiftALockoutOnceItsTimeHasPassed() {
        // The bug this covers: the failure counter only ever reset on a successful login, which
        // a locked-out person cannot reach. Five wrong attempts shut the account permanently
        // while the message said "try again later", and waiting did nothing.
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(java.time.LocalDateTime.now().minusMinutes(1));
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(passwordEncoder.matches("admin123", user.getPassword())).thenReturn(true);
        when(jwtUtil.generateAccessToken(anyString(), anyLong(), anyString(), anyList(), any()))
                .thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh-token");

        AuthResponse response = authService.authenticate(loginReq);

        assertEquals("access-token", response.getAccessToken());
        assertNull(user.getLockedUntil());
        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    void authenticate_shouldStillRefuseWhileTheLockoutIsRunning() {
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(10));
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);

        AuthenticationFailedException ex = assertThrows(AuthenticationFailedException.class,
                () -> authService.authenticate(loginReq));
        assertTrue(ex.getMessage().contains("minute"), "must say how long is left");
        assertTrue(ex.getMessage().contains("reset your password"),
                "must offer the way out that works immediately");
        // The password is never even checked while locked.
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void authenticate_shouldSetAnExpiryWhenTheLockoutIsApplied() {
        // Without an expiry the lockout is permanent, which is what caused this.
        user.setFailedLoginAttempts(4);
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(AuthenticationFailedException.class, () -> authService.authenticate(loginReq));

        assertNotNull(user.getLockedUntil(), "a lockout with no end is a closed account");
        assertTrue(user.getLockedUntil().isAfter(java.time.LocalDateTime.now()));
    }

    @Test
    void authenticate_shouldNotLockBeforeTheLimit() {
        user.setFailedLoginAttempts(1);
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        AuthenticationFailedException ex = assertThrows(AuthenticationFailedException.class,
                () -> authService.authenticate(loginReq));
        assertNull(user.getLockedUntil());
        assertTrue(ex.getMessage().contains("attempt(s) remaining"));
    }

    @Test
    void authenticate_shouldClearAnyLockOnSuccess() {
        user.setFailedLoginAttempts(3);
        when(userRepo.findByEmail("customer@test.com")).thenReturn(user);
        when(passwordEncoder.matches("admin123", user.getPassword())).thenReturn(true);
        when(jwtUtil.generateAccessToken(anyString(), anyLong(), anyString(), anyList(), any()))
                .thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh-token");

        authService.authenticate(loginReq);

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }
}
