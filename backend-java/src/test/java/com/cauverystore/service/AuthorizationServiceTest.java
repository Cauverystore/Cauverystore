package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.User;
import com.cauverystore.exception.AccessDeniedException;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private ProductRepository productRepo;

    @InjectMocks
    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(Long userId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(userId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void getCurrentUserId_shouldReturnUserId_whenAuthenticated() {
        setAuthenticatedUser(42L, "CUSTOMER");

        assertEquals(42L, authorizationService.getCurrentUserId());
    }

    @Test
    void getCurrentUserId_shouldThrow_whenNotAuthenticated() {
        assertThrows(AccessDeniedException.class, () -> authorizationService.getCurrentUserId());
    }

    @Test
    void getCurrentUserRole_shouldReturnRole_whenAuthenticated() {
        setAuthenticatedUser(1L, "ADMIN");

        assertEquals("ADMIN", authorizationService.getCurrentUserRole());
    }

    @Test
    void getCurrentUserEmail_shouldReturnEmail_whenAuthenticated() {
        setAuthenticatedUser(1L, "CUSTOMER");

        assertEquals("user@test.com", authorizationService.getCurrentUserEmail());
    }

    @Test
    void requireRole_shouldPass_whenRoleMatches() {
        setAuthenticatedUser(1L, "ADMIN");

        assertDoesNotThrow(() -> authorizationService.requireRole("ADMIN"));
        assertDoesNotThrow(() -> authorizationService.requireRole("ADMIN", "SUPER_ADMIN"));
    }

    @Test
    void requireRole_shouldThrow_whenRoleDoesNotMatch() {
        setAuthenticatedUser(1L, "CUSTOMER");

        assertThrows(AccessDeniedException.class, () -> authorizationService.requireRole("ADMIN"));
    }

    @Test
    void requireSelfOrAdmin_shouldPass_whenUserAccessesOwnData() {
        setAuthenticatedUser(1L, "CUSTOMER");

        assertDoesNotThrow(() -> authorizationService.requireSelfOrAdmin(1L));
    }

    @Test
    void requireSelfOrAdmin_shouldPass_whenAdminAccessesOtherData() {
        setAuthenticatedUser(1L, "ADMIN");

        assertDoesNotThrow(() -> authorizationService.requireSelfOrAdmin(2L));
    }

    @Test
    void requireSelfOrAdmin_shouldPass_whenSuperAdminAccessesOtherData() {
        setAuthenticatedUser(1L, "SUPER_ADMIN");

        assertDoesNotThrow(() -> authorizationService.requireSelfOrAdmin(2L));
    }

    @Test
    void requireSelfOrAdmin_shouldThrow_whenCustomerAccessesOtherData() {
        setAuthenticatedUser(1L, "CUSTOMER");

        assertThrows(AccessDeniedException.class, () -> authorizationService.requireSelfOrAdmin(2L));
    }

    @Test
    void requireSellerOwnership_shouldPass_whenProductBelongsToSeller() {
        setAuthenticatedUser(1L, "SELLER");
        Product product = new Product();
        product.setId(10L);
        product.setSellerId(1L);
        when(productRepo.findById(10L)).thenReturn(Optional.of(product));

        assertDoesNotThrow(() -> authorizationService.requireSellerOwnership(10L));
    }

    @Test
    void requireSellerOwnership_shouldThrow_whenProductBelongsToOtherSeller() {
        setAuthenticatedUser(1L, "SELLER");
        Product product = new Product();
        product.setId(10L);
        product.setSellerId(2L);
        when(productRepo.findById(10L)).thenReturn(Optional.of(product));

        assertThrows(AccessDeniedException.class,
                () -> authorizationService.requireSellerOwnership(10L));
    }

    @Test
    void requireAdminOrSuperAdmin_shouldPass_whenAdmin() {
        setAuthenticatedUser(1L, "ADMIN");
        assertDoesNotThrow(() -> authorizationService.requireAdminOrSuperAdmin());
    }

    @Test
    void requireAdminOrSuperAdmin_shouldPass_whenSuperAdmin() {
        setAuthenticatedUser(1L, "SUPER_ADMIN");
        assertDoesNotThrow(() -> authorizationService.requireAdminOrSuperAdmin());
    }

    @Test
    void requireAdminOrSuperAdmin_shouldThrow_whenCustomer() {
        setAuthenticatedUser(1L, "CUSTOMER");
        assertThrows(AccessDeniedException.class,
                () -> authorizationService.requireAdminOrSuperAdmin());
    }

    @Test
    void requireSuperAdmin_shouldPass_whenSuperAdmin() {
        setAuthenticatedUser(1L, "SUPER_ADMIN");
        assertDoesNotThrow(() -> authorizationService.requireSuperAdmin());
    }

    @Test
    void requireSuperAdmin_shouldThrow_whenAdmin() {
        setAuthenticatedUser(1L, "ADMIN");
        assertThrows(AccessDeniedException.class,
                () -> authorizationService.requireSuperAdmin());
    }

    @Test
    void getCurrentUser_shouldReturnUser_whenAuthenticated() {
        setAuthenticatedUser(1L, "CUSTOMER");
        User user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        User result = authorizationService.getCurrentUser();

        assertNotNull(result);
        assertEquals("user@test.com", result.getEmail());
    }
}
