package com.cauverystore.service;

import com.cauverystore.entities.Order;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.exception.AccessDeniedException;
import com.cauverystore.exception.PermissionDeniedException;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final UserRepository userRepo;
    private final ProductRepository productRepo;
    private final OrderRepository orderRepo;

    public void requireRole(String... roles) {
        String userRole = getCurrentUserRole();
        boolean match = Arrays.stream(roles).anyMatch(r -> r.equalsIgnoreCase(userRole));
        if (!match) {
            throw new AccessDeniedException("Access denied. Required role(s): " + String.join(", ", roles));
        }
    }

    public void requireSelfOrAdmin(Long targetUserId) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId.equals(targetUserId)) {
            return;
        }
        String role = getCurrentUserRole();
        if ("ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role)) {
            return;
        }
        throw new AccessDeniedException("Access denied. You can only access your own data.");
    }

    public void requireSellerOwnership(Long productId) {
        Long currentUserId = getCurrentUserId();
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        if (product.getSellerId() != null && !product.getSellerId().equals(currentUserId)) {
            throw new AccessDeniedException("Seller does not own this product");
        }
    }

    public void requireAdminOrSuperAdmin() {
        String role = getCurrentUserRole();
        if (!"ADMIN".equalsIgnoreCase(role) && !"SUPER_ADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Access denied. Admin or Super Admin role required.");
        }
    }

    public void requireSuperAdmin() {
        String role = getCurrentUserRole();
        if (!"SUPER_ADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Access denied. Super Admin role required.");
        }
    }

    public User getCurrentUser() {
        Long userId = getCurrentUserId();
        return userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }
        Object details = auth.getDetails();
        if (details instanceof Long) {
            return (Long) details;
        }
        throw new RuntimeException("Unable to extract user ID from security context");
    }

    public String getCurrentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", ""))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No role found"));
    }

    public String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }
        return auth.getName();
    }

    public boolean hasRole(String role) {
        String userRole = getCurrentUserRole();
        return userRole.equalsIgnoreCase(role);
    }

    public boolean hasAnyRole(String... roles) {
        String userRole = getCurrentUserRole();
        return Arrays.stream(roles).anyMatch(r -> r.equalsIgnoreCase(userRole));
    }

    public void requireAnyRole(String... roles) {
        String userRole = getCurrentUserRole();
        boolean match = Arrays.stream(roles).anyMatch(r -> r.equalsIgnoreCase(userRole));
        if (!match) {
            throw new PermissionDeniedException("Access denied. Required one of: " + String.join(", ", roles));
        }
    }

    public void requireProductAccess(Long productId) {
        String role = getCurrentUserRole();
        if ("ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role)) {
            return;
        }
        if ("SELLER".equalsIgnoreCase(role)) {
            Long currentUserId = getCurrentUserId();
            Product product = productRepo.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
            if (product.getSellerId() != null && !product.getSellerId().equals(currentUserId)) {
                throw new PermissionDeniedException("Seller does not own this product");
            }
            return;
        }
        throw new PermissionDeniedException("Access denied. Insufficient permissions to access this product.");
    }

    public void requireOrderAccess(Long orderId) {
        String role = getCurrentUserRole();
        if ("ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role)) {
            return;
        }
        Long currentUserId = getCurrentUserId();
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
        if ("CUSTOMER".equalsIgnoreCase(role)) {
            if (order.getUser() == null || !currentUserId.equals(order.getUser().getId())) {
                throw new PermissionDeniedException("Customer can only access their own orders");
            }
            return;
        }
        if ("SELLER".equalsIgnoreCase(role)) {
            if (order.getSellerId() != null && !order.getSellerId().equals(currentUserId)) {
                throw new PermissionDeniedException("Seller does not own this order");
            }
            return;
        }
        throw new PermissionDeniedException("Access denied. Insufficient permissions to access this order.");
    }

    public void requireUserManagement() {
        String role = getCurrentUserRole();
        if (!"ADMIN".equalsIgnoreCase(role) && !"SUPER_ADMIN".equalsIgnoreCase(role)) {
            throw new PermissionDeniedException("Access denied. Admin or Super Admin role required for user management.");
        }
    }

    public void requireSystemSettings() {
        String role = getCurrentUserRole();
        if (!"SUPER_ADMIN".equalsIgnoreCase(role)) {
            throw new PermissionDeniedException("Access denied. Super Admin role required for system settings.");
        }
    }

    public void requireReports() {
        String role = getCurrentUserRole();
        if (!"ADMIN".equalsIgnoreCase(role) && !"EXECUTIVE".equalsIgnoreCase(role) && !"SUPER_ADMIN".equalsIgnoreCase(role)) {
            throw new PermissionDeniedException("Access denied. Admin, Executive, or Super Admin role required for reports.");
        }
    }

    public void requireCustomerRead() {
        String role = getCurrentUserRole();
        if (!"ADMIN".equalsIgnoreCase(role) && !"EXECUTIVE".equalsIgnoreCase(role) && !"SUPER_ADMIN".equalsIgnoreCase(role)) {
            throw new PermissionDeniedException("Access denied. Admin, Executive, or Super Admin role required.");
        }
    }

    public void requireSellerManagement() {
        String role = getCurrentUserRole();
        if (!"ADMIN".equalsIgnoreCase(role) && !"SUPER_ADMIN".equalsIgnoreCase(role)) {
            throw new PermissionDeniedException("Access denied. Admin or Super Admin role required for seller management.");
        }
    }

    public void requireProductApproval() {
        String role = getCurrentUserRole();
        if (!"ADMIN".equalsIgnoreCase(role) && !"SUPER_ADMIN".equalsIgnoreCase(role)) {
            throw new PermissionDeniedException("Access denied. Admin or Super Admin role required for product approval.");
        }
    }

    public Map<String, Object> maskUserData(User user) {
        String role = getCurrentUserRole();
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("fullName", user.getFullName());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("profilePicture", user.getProfilePicture());
        data.put("status", user.getStatus());
        data.put("role", user.getRole());
        data.put("active", user.isActive());
        data.put("mfaEnabled", user.getMfaEnabled());
        data.put("lastLoginAt", user.getLastLoginAt());
        data.put("failedLoginAttempts", user.getFailedLoginAttempts());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        data.put("phone", isAdmin ? "****" : user.getPhone());
        data.put("address", isAdmin ? "****" : user.getAddress());
        return data;
    }

    public void verifySellerOwnership(Long productId) {
        Long currentUserId = getCurrentUserId();
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        if (product.getSellerId() == null || !product.getSellerId().equals(currentUserId)) {
            throw new PermissionDeniedException("Seller does not own this product");
        }
    }

    public Long resolveSellerId() {
        String role = getCurrentUserRole();
        if ("SELLER".equalsIgnoreCase(role)) {
            return getCurrentUserId();
        }
        return null;
    }

    public boolean isSellerOfProduct(Long productId) {
        String role = getCurrentUserRole();
        if (!"SELLER".equalsIgnoreCase(role)) {
            return false;
        }
        Long currentUserId = getCurrentUserId();
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        return product.getSellerId() != null && product.getSellerId().equals(currentUserId);
    }
}
