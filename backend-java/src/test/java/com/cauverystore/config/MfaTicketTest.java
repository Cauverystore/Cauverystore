package com.cauverystore.config;

import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.repository.SellerTeamMemberRepository;
import com.cauverystore.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The ticket between the password and authenticator-code steps. */
class MfaTicketTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-test-secret-test-secret-test-secret-0123456789");
        SecurityContextHolder.clearContext();
    }

    @Test
    void aTicketCarriesTheUserAndPreferredRole() {
        Claims claims = jwtUtil.parseMfaToken(jwtUtil.generateMfaToken(7L, 3, "SELLER"));
        assertNotNull(claims);
        assertEquals(7L, claims.get("userId", Long.class));
        assertEquals(3, claims.get("tokenVersion", Integer.class));
        assertEquals("SELLER", claims.get("preferredRole", String.class));
    }

    @Test
    void anAccessTokenOrRubbishIsNotATicket() {
        String access = jwtUtil.generateAccessToken("a@b.c", 7L, "a", List.of("CUSTOMER"), 0);
        assertNull(jwtUtil.parseMfaToken(access));
        assertNull(jwtUtil.parseMfaToken("not-a-token"));
        assertNull(jwtUtil.parseMfaToken(null));
    }

    @Test
    void aTicketCannotBeUsedToCallTheApi() throws Exception {
        UserRepository userRepo = mock(UserRepository.class);
        User user = new User();
        user.setId(7L);
        user.setRole(Role.ADMIN);
        user.setStatus("ACTIVE");
        user.setActive(true);
        user.setTokenVersion(0);
        when(userRepo.findById(7L)).thenReturn(Optional.of(user));
        JwtFilter filter = new JwtFilter(jwtUtil, new ObjectMapper(), userRepo, mock(SellerTeamMemberRepository.class));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/users");
        req.addHeader("Authorization", "Bearer " + jwtUtil.generateMfaToken(7L, 0, "ADMIN"));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        assertNull(chain.getRequest(), "the request must stop at the filter");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
