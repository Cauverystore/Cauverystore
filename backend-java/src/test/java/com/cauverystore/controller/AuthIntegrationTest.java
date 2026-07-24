package com.cauverystore.controller;

import com.cauverystore.config.JwtUtil;
import com.cauverystore.dto.AuthResponse;
import com.cauverystore.dto.LoginRequest;
import com.cauverystore.dto.PasswordResetRequest;
import com.cauverystore.dto.RefreshTokenRequest;
import com.cauverystore.entities.EmailOtp;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.repository.EmailOtpRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.MethodName.class)
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private EmailOtpRepository emailOtpRepo;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String customerToken;
    private String sellerToken;
    private String adminToken;
    private String superToken;
    private String customerRefresh;
    private String sellerRefresh;
    private String adminRefresh;
    private String superRefresh;

    @BeforeEach
    void setup() {
        if (customerToken == null) {
            ResponseEntity<AuthResponse> custResp = rest.postForEntity("/api/user/login",
                    loginReq("customer", "admin123"), AuthResponse.class);
            if (custResp.getBody() != null) {
                customerToken = custResp.getBody().getAccessToken();
                customerRefresh = custResp.getBody().getRefreshToken();
            }

            ResponseEntity<AuthResponse> sellResp = rest.postForEntity("/api/seller/login",
                    loginReq("seller", "seller123"), AuthResponse.class);
            if (sellResp.getBody() != null) {
                sellerToken = sellResp.getBody().getAccessToken();
                sellerRefresh = sellResp.getBody().getRefreshToken();
            }

            ResponseEntity<AuthResponse> adminResp = rest.postForEntity("/api/admin/login",
                    loginReq("admin", "admin123"), AuthResponse.class);
            if (adminResp.getBody() != null) {
                adminToken = adminResp.getBody().getAccessToken();
                adminRefresh = adminResp.getBody().getRefreshToken();
            }

            ResponseEntity<AuthResponse> superResp = rest.postForEntity("/api/admin/login",
                    loginReq("superadmin", "superadmin123"), AuthResponse.class);
            if (superResp.getBody() != null) {
                superToken = superResp.getBody().getAccessToken();
                superRefresh = superResp.getBody().getRefreshToken();
            }
        }
    }

    private LoginRequest loginReq(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    @Test
    void test01CustomerLogin_returnsTokens() {
        LoginRequest req = loginReq("customer", "admin123");
        ResponseEntity<AuthResponse> resp = rest.postForEntity("/api/user/login", req, AuthResponse.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(Objects.requireNonNull(resp.getBody()).getAccessToken());
        assertNotNull(resp.getBody().getRefreshToken());
        assertEquals("CUSTOMER", resp.getBody().getRole());
    }

    @Test
    void test02SellerLogin_returnsTokens() {
        LoginRequest req = loginReq("seller", "seller123");
        ResponseEntity<AuthResponse> resp = rest.postForEntity("/api/seller/login", req, AuthResponse.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(Objects.requireNonNull(resp.getBody()).getAccessToken());
        assertEquals("SELLER", resp.getBody().getRole());
    }

    @Test
    void test03AdminLogin_returnsTokens() {
        LoginRequest req = loginReq("admin", "admin123");
        ResponseEntity<AuthResponse> resp = rest.postForEntity("/api/admin/login", req, AuthResponse.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(Objects.requireNonNull(resp.getBody()).getAccessToken());
        assertEquals("ADMIN", resp.getBody().getRole());
    }

    @Test
    void test04SuperAdminLogin_returnsTokens() {
        LoginRequest req = loginReq("superadmin", "superadmin123");
        ResponseEntity<AuthResponse> resp = rest.postForEntity("/api/admin/login", req, AuthResponse.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(Objects.requireNonNull(resp.getBody()).getAccessToken());
        assertEquals("SUPER_ADMIN", resp.getBody().getRole());
    }

    @Test
    void test05Login_invalidPassword_returns401() {
        LoginRequest req = loginReq("customer", "wrongpassword");
        ResponseEntity<Map> resp = rest.postForEntity("/api/user/login", req, Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void test06Login_inactiveUser_returnsError() {
        User customer = userRepo.findByUsername("customer");
        if (customer != null) {
            customer.setActive(false);
            customer.setStatus("BLOCKED");
            userRepo.save(customer);

            LoginRequest req = loginReq("customer", "admin123");
            ResponseEntity<Map> resp = rest.postForEntity("/api/user/login", req, Map.class);

            assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
            String error = Objects.requireNonNull(resp.getBody()).get("error").toString().toLowerCase();
            assertTrue(error.contains("not active") || error.contains("blocked"));

            customer.setActive(true);
            customer.setStatus("ACTIVE");
            userRepo.save(customer);
        }
    }

    @Test
    void test07AccessWithoutToken_returns401() {
        ResponseEntity<Map> resp = rest.getForEntity("/api/user/profile", Map.class);
        assertTrue(resp.getStatusCode() == HttpStatus.UNAUTHORIZED || resp.getStatusCode() == HttpStatus.FORBIDDEN);
    }

    @Test
    void test08AccessWithExpiredToken_returns401() {
        var key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .setSubject("expired@test.com")
                .claim("userId", 999L)
                .claim("username", "expired")
                .claim("role", "CUSTOMER")
                .setIssuedAt(new Date(System.currentTimeMillis() - 86400000L * 2))
                .setExpiration(new Date(System.currentTimeMillis() - 86400000L))
                .signWith(key)
                .compact();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(expiredToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = rest.exchange("/api/user/profile", HttpMethod.GET, entity, Map.class);
        assertTrue(resp.getStatusCode() == HttpStatus.UNAUTHORIZED || resp.getStatusCode() == HttpStatus.FORBIDDEN);
    }

    @Test
    void test09CustomerCannotAccessAdminApi() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(customerToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = rest.exchange("/api/admin/users", HttpMethod.GET, entity, Map.class);
        assertTrue(resp.getStatusCode() == HttpStatus.FORBIDDEN || resp.getStatusCode() == HttpStatus.UNAUTHORIZED);
    }

    @Test
    void test10CustomerCannotAccessSellerApi() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(customerToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = rest.exchange("/api/seller/dashboard", HttpMethod.GET, entity, Map.class);
        assertTrue(resp.getStatusCode() == HttpStatus.FORBIDDEN || resp.getStatusCode() == HttpStatus.UNAUTHORIZED);
    }

    @Test
    void test11SellerCannotAccessAdminApi() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(sellerToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = rest.exchange("/api/admin/users", HttpMethod.GET, entity, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void test12SellerCannotAccessOtherSellerProduct() {
        User seller1 = userRepo.findByUsername("seller");
        assertNotNull(seller1);

        User seller2 = userRepo.findByUsername("seller2");
        if (seller2 == null) {
            seller2 = new User();
            seller2.setFullName("Seller Two");
            seller2.setUsername("seller2");
            seller2.setEmail("seller2@test.com");
            seller2.setPassword(passwordEncoder.encode("seller123"));
            seller2.setRole(Role.SELLER);
            seller2.setStatus("ACTIVE");
            seller2.setActive(true);
            seller2 = userRepo.save(seller2);
        }

        com.cauverystore.entities.Product p = new com.cauverystore.entities.Product();
        p.setName("Seller1 Product");
        p.setPrice(100.0);
        p.setStock(10);
        p.setActive(true);
        p.setSellerId(seller1.getId());
        p = productRepo.save(p);

        LoginRequest req = loginReq("seller2", "seller123");
        ResponseEntity<AuthResponse> loginResp = rest.postForEntity("/api/seller/login", req, AuthResponse.class);
        assertNotNull(loginResp.getBody());
        String seller2Token = loginResp.getBody().getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(seller2Token);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = rest.exchange("/api/products/" + p.getId(), HttpMethod.GET, entity, Map.class);
        assertTrue(resp.getStatusCode() == HttpStatus.FORBIDDEN || resp.getStatusCode() == HttpStatus.BAD_REQUEST);
    }

    @Test
    void test13AdminCanAccessAdminApi() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<List> resp = rest.exchange("/api/admin/users", HttpMethod.GET, entity, List.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void test14AdminCannotChangeSuperAdminRole() {
        User superAdmin = userRepo.findByUsername("superadmin");
        assertNotNull(superAdmin);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.set("Content-Type", "application/json");

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("role", "ADMIN"), headers);

        ResponseEntity<Map> resp = rest.exchange(
                "/api/admin/users/" + superAdmin.getId() + "/role",
                HttpMethod.PUT, entity, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void test15SuperAdminCanAccessAll() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(superToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<List> adminResp = rest.exchange("/api/admin/users", HttpMethod.GET, entity, List.class);
        assertEquals(HttpStatus.OK, adminResp.getStatusCode());

        ResponseEntity<Map> superResp = rest.exchange("/api/super-admin/dashboard", HttpMethod.GET, entity, Map.class);
        assertEquals(HttpStatus.OK, superResp.getStatusCode());
    }

    @Test
    void test16RefreshTokenRotation() {
        ResponseEntity<AuthResponse> loginResp = rest.postForEntity("/api/admin/login",
                loginReq("admin", "admin123"), AuthResponse.class);
        assertNotNull(loginResp.getBody());
        String currentRefresh = loginResp.getBody().getRefreshToken();

        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(currentRefresh);

        ResponseEntity<AuthResponse> resp = rest.postForEntity("/api/auth/refresh", req, AuthResponse.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(Objects.requireNonNull(resp.getBody()).getAccessToken());
        String newRefresh = resp.getBody().getRefreshToken();

        // Old refresh token should be invalidated after rotation
        RefreshTokenRequest oldReq = new RefreshTokenRequest();
        oldReq.setRefreshToken(currentRefresh);
        ResponseEntity<Map> oldResp = rest.postForEntity("/api/auth/refresh", oldReq, Map.class);
        assertTrue(oldResp.getStatusCode() == HttpStatus.BAD_REQUEST || oldResp.getStatusCode() == HttpStatus.UNAUTHORIZED,
                "Old refresh token should be invalid after rotation");

        // New refresh token should still work
        RefreshTokenRequest newReq = new RefreshTokenRequest();
        newReq.setRefreshToken(newRefresh);
        ResponseEntity<AuthResponse> newResp = rest.postForEntity("/api/auth/refresh", newReq, AuthResponse.class);
        assertEquals(HttpStatus.OK, newResp.getStatusCode(), "New refresh token should work");
    }

    @Test
    void test17LogoutInvalidatesRefreshToken() {
        LoginRequest loginReq = loginReq("customer", "admin123");
        ResponseEntity<AuthResponse> loginResp = rest.postForEntity("/api/user/login", loginReq, AuthResponse.class);
        String refreshToken = Objects.requireNonNull(loginResp.getBody()).getRefreshToken();

        RefreshTokenRequest logoutReq = new RefreshTokenRequest();
        logoutReq.setRefreshToken(refreshToken);
        ResponseEntity<Map> logoutResp = rest.postForEntity("/api/auth/logout", logoutReq, Map.class);
        assertEquals(HttpStatus.OK, logoutResp.getStatusCode());

        RefreshTokenRequest reuseReq = new RefreshTokenRequest();
        reuseReq.setRefreshToken(refreshToken);
        ResponseEntity<Map> reuseResp = rest.postForEntity("/api/auth/refresh", reuseReq, Map.class);
        assertTrue(reuseResp.getStatusCode() == HttpStatus.BAD_REQUEST || reuseResp.getStatusCode() == HttpStatus.UNAUTHORIZED);
    }

    @Test
    void test18PasswordResetFlow() {
        String email = "pwreset_test@test.com";
        User existing = userRepo.findByEmail(email);
        if (existing == null) {
            User u = new User();
            u.setFullName("PW Reset");
            u.setUsername("pwreset_test");
            u.setEmail(email);
            u.setPassword(passwordEncoder.encode("original123"));
            u.setRole(Role.CUSTOMER);
            u.setStatus("ACTIVE");
            u.setActive(true);
            userRepo.save(u);
        }

        ResponseEntity<Map> reqResp = rest.postForEntity("/api/auth/request-password-reset",
                Map.of("email", email), Map.class);
        assertEquals(HttpStatus.OK, reqResp.getStatusCode());

        EmailOtp otpRecord = emailOtpRepo.findByEmail(email);
        assertNotNull(otpRecord);
        String otp = otpRecord.getOtp();
        assertNotNull(otp);

        PasswordResetRequest resetReq = new PasswordResetRequest();
        resetReq.setEmail(email);
        resetReq.setOtp(otp);
        resetReq.setNewPassword("newResetPass123");
        ResponseEntity<Map> resetResp = rest.postForEntity("/api/auth/reset-password", resetReq, Map.class);
        assertEquals(HttpStatus.OK, resetResp.getStatusCode());

        LoginRequest loginReq = loginReq("pwreset_test", "newResetPass123");
        ResponseEntity<AuthResponse> loginResp = rest.postForEntity("/api/user/login", loginReq, AuthResponse.class);
        assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        assertNotNull(Objects.requireNonNull(loginResp.getBody()).getAccessToken());
    }

    @Test
    void test19RegisterNewCustomer() {
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        String email = "newuser" + uniqueSuffix + "@test.com";
        String username = "newuser" + uniqueSuffix;

        Map<String, Object> newUser = Map.of(
                "fullName", "New User",
                "username", username,
                "email", email,
                "password", "newpass123"
        );

        ResponseEntity<Map> regResp = rest.postForEntity("/api/user/register", newUser, Map.class);
        assertEquals(HttpStatus.OK, regResp.getStatusCode());

        LoginRequest loginReq = loginReq(username, "newpass123");
        ResponseEntity<AuthResponse> loginResp = rest.postForEntity("/api/user/login", loginReq, AuthResponse.class);
        assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        assertNotNull(Objects.requireNonNull(loginResp.getBody()).getAccessToken());
    }
}
