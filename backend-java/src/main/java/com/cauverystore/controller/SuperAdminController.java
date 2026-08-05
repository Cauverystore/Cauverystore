package com.cauverystore.controller;

import com.cauverystore.entities.AuditLog;
import com.cauverystore.entities.ImpersonationSession;
import com.cauverystore.entities.Permission;
import com.cauverystore.entities.PlatformSetting;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.RolePermission;
import com.cauverystore.entities.User;
import com.cauverystore.exception.AccessDeniedException;
import com.cauverystore.repository.AuditLogRepository;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.PermissionRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.RefundRepository;
import com.cauverystore.repository.ReturnRequestRepository;
import com.cauverystore.repository.RolePermissionRepository;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.service.AuditService;
import com.cauverystore.service.AuthorizationService;
import com.cauverystore.service.EmailService;
import com.cauverystore.service.ImpersonationService;
import com.cauverystore.service.PlatformSettingsService;
import com.cauverystore.service.ProductService;
import com.cauverystore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/super-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final UserRepository userRepo;
    private final ProductRepository productRepo;
    private final OrderRepository orderRepo;
    private final RefundRepository refundRepo;
    private final AuditLogRepository auditLogRepo;
    private final PlatformSettingsService platformSettingsService;
    private final ImpersonationService impersonationService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final UserService userService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final PermissionRepository permissionRepo;
    private final RolePermissionRepository rolePermissionRepo;
    private final ProductService productService;
    private final ReturnRequestRepository returnRequestRepo;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRevenue", orderRepo.findAll().stream()
                .filter(o -> !"CANCELLED".equals(o.getStatus()) && !"REFUNDED".equals(o.getStatus()))
                .mapToDouble(o -> o.getTotalAmount()).sum());
        stats.put("totalOrders", orderRepo.count());
        stats.put("totalCustomers", userRepo.findAll().stream()
                .filter(u -> u.getRole() == Role.CUSTOMER).count());
        stats.put("totalSellers", userRepo.findAll().stream()
                .filter(u -> u.getRole() == Role.SELLER).count());
        stats.put("totalProducts", productRepo.count());
        stats.put("totalRefunds", refundRepo.getTotalRefundCount() != null ? refundRepo.getTotalRefundCount() : 0L);
        stats.put("pendingApprovals", productRepo.findAll().stream()
                .filter(p -> "pending".equalsIgnoreCase(p.getProductStatus())).count());
        stats.put("activeProducts", productRepo.findByActiveTrue().size());
        stats.put("failedPayments", 0L);
        stats.put("recentActivity", auditLogRepo.findAllByOrderByTimestampDesc().stream()
                .limit(20).map(a -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", a.getId());
                    m.put("action", a.getAction());
                    m.put("entity", a.getEntity());
                    m.put("userEmail", a.getUserEmail());
                    m.put("timestamp", a.getTimestamp() != null ? a.getTimestamp().toString() : null);
                    return m;
                }).collect(Collectors.toList()));
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/activity-log")
    public ResponseEntity<Map<String, Object>> getActivityLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String performedBy,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        Page<AuditLog> logs;
        if (action != null || performedBy != null || dateFrom != null || dateTo != null) {
            List<AuditLog> all = auditLogRepo.findAllByOrderByTimestampDesc();
            if (action != null && !action.isEmpty()) {
                all = all.stream().filter(l -> action.equalsIgnoreCase(l.getAction())).collect(Collectors.toList());
            }
            if (performedBy != null && !performedBy.isEmpty()) {
                String lower = performedBy.toLowerCase();
                all = all.stream().filter(l -> l.getUserEmail() != null && l.getUserEmail().toLowerCase().contains(lower)).collect(Collectors.toList());
            }
            if (dateFrom != null && !dateFrom.isEmpty()) {
                try {
                    java.time.LocalDateTime from = java.time.LocalDate.parse(dateFrom).atStartOfDay();
                    all = all.stream().filter(l -> l.getTimestamp() != null && !l.getTimestamp().isBefore(from)).collect(Collectors.toList());
                } catch (Exception ignored) {}
            }
            if (dateTo != null && !dateTo.isEmpty()) {
                try {
                    java.time.LocalDateTime to = java.time.LocalDate.parse(dateTo).plusDays(1).atStartOfDay();
                    all = all.stream().filter(l -> l.getTimestamp() != null && l.getTimestamp().isBefore(to)).collect(Collectors.toList());
                } catch (Exception ignored) {}
            }
            int start = page * size;
            int end = Math.min(start + size, all.size());
            List<AuditLog> content = start < all.size() ? all.subList(start, end) : List.of();
            Map<String, Object> result = new HashMap<>();
            result.put("content", content);
            result.put("totalElements", (long) all.size());
            result.put("totalPages", (int) Math.ceil((double) all.size() / size));
            result.put("currentPage", page);
            return ResponseEntity.ok(result);
        }
        logs = auditLogRepo.findAll(PageRequest.of(page, size));
        Map<String, Object> result = new HashMap<>();
        result.put("content", logs.getContent());
        result.put("totalElements", logs.getTotalElements());
        result.put("totalPages", logs.getTotalPages());
        result.put("currentPage", logs.getNumber());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String id) {
        List<User> users = userRepo.findAll();
        if (role != null && !role.isEmpty()) {
            users = users.stream()
                    .filter(u -> u.getRole() != null && u.getRole().name().equalsIgnoreCase(role))
                    .collect(Collectors.toList());
        }
        if (status != null && !status.isEmpty()) {
            users = users.stream()
                    .filter(u -> u.getStatus() != null && u.getStatus().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }
        if (search != null && !search.isEmpty()) {
            String lower = search.toLowerCase();
            users = users.stream()
                    .filter(u -> (u.getUsername() != null && u.getUsername().toLowerCase().contains(lower))
                            || (u.getEmail() != null && u.getEmail().toLowerCase().contains(lower))
                            || (u.getFullName() != null && u.getFullName().toLowerCase().contains(lower)))
                    .collect(Collectors.toList());
        }
        if (id != null && !id.trim().isEmpty()) {
            String idQuery = id.trim();
            users = users.stream()
                    .filter(u -> String.valueOf(u.getId()).contains(idQuery))
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        String role = body.get("role");
        String fullName = body.get("fullName");

        if (email == null || password == null || role == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email, password, and role are required"));
        }
        if (userRepo.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already registered"));
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setUsername(email.split("@")[0]);
        user.setRole(Role.valueOf(role.toUpperCase()));
        user.setStatus("ACTIVE");
        user.setActive(true);
        user = userRepo.save(user);

        String currentEmail = authorizationService.getCurrentUserEmail();
        auditService.logAccountAction("USER_CREATED_BY_SUPER_ADMIN", currentEmail, user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("message", "User created successfully");
        result.put("userId", user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User updated) {
        User user = userService.getUser(id);
        if (updated.getFullName() != null) user.setFullName(updated.getFullName());
        if (updated.getEmail() != null) user.setEmail(updated.getEmail());
        if (updated.getPhone() != null) user.setPhone(updated.getPhone());
        user = userRepo.save(user);

        String currentEmail = authorizationService.getCurrentUserEmail();
        auditService.logAccountAction("USER_UPDATED_BY_SUPER_ADMIN", currentEmail, id);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> changeRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User target = userService.getUser(id);
        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new AccessDeniedException("Cannot change role of another SUPER_ADMIN");
        }
        String newRole = body.get("role");
        Role oldRole = target.getRole();
        User updated = userService.updateUserRole(id, newRole);
        String currentEmail = authorizationService.getCurrentUserEmail();
        auditService.logRoleChange(currentEmail, id, oldRole, updated.getRole());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/users/{id}/suspend")
    public ResponseEntity<Map<String, Object>> suspendUser(@PathVariable Long id) {
        Long currentUserId = authorizationService.getCurrentUserId();
        String currentEmail = authorizationService.getCurrentUserEmail();
        User target = userService.getUser(id);
        if (target.getRole() == Role.SUPER_ADMIN && !target.getId().equals(currentUserId)) {
            throw new AccessDeniedException("Cannot suspend another SUPER_ADMIN account");
        }
        User updated = userService.suspendUser(id, currentUserId);
        auditService.logSuspend(currentEmail, currentUserId, id,
                target.getRole().name(), target.getEmail());
        return ResponseEntity.ok(Map.of("message", "User suspended successfully",
                "userId", id, "status", updated.getStatus(),
                "suspendedBy", updated.getSuspendedBy(), "suspendedAt", updated.getSuspendedAt()));
    }

    @PostMapping("/users/{id}/revoke")
    public ResponseEntity<Map<String, Object>> revokeUser(@PathVariable Long id) {
        Long currentUserId = authorizationService.getCurrentUserId();
        String currentEmail = authorizationService.getCurrentUserEmail();
        User target = userService.getUser(id);
        if (target.getStatus() == null || !target.getStatus().equals("SUSPENDED")) {
            throw new RuntimeException("User is not currently suspended");
        }
        User updated = userService.revokeUser(id);
        auditService.logRevoke(currentEmail, currentUserId, id,
                target.getRole().name(), target.getEmail());
        return ResponseEntity.ok(Map.of("message", "User suspension revoked successfully",
                "userId", id, "status", updated.getStatus()));
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<User> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        User target = userService.getUser(id);
        if (target.getRole() == Role.SUPER_ADMIN && !"ACTIVE".equalsIgnoreCase(status)) {
            throw new AccessDeniedException("Cannot suspend SUPER_ADMIN account");
        }
        User updated;
        if ("BLOCKED".equalsIgnoreCase(status) || "SUSPENDED".equalsIgnoreCase(status)) {
            updated = userService.blockUser(id);
        } else {
            updated = userService.unblockUser(id);
        }
        String currentEmail = authorizationService.getCurrentUserEmail();
        auditService.logAccountAction("STATUS_CHANGE_TO_" + status.toUpperCase(), currentEmail, id);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetUserPassword(@PathVariable Long id) {
        User user = userService.getUser(id);
        String newPassword = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "A1!";
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setMustResetPassword(true);
        user.invalidateSessions();
        userRepo.save(user);

        String currentEmail = authorizationService.getCurrentUserEmail();
        auditService.logAccountAction("PASSWORD_RESET_BY_SUPER_ADMIN", currentEmail, id);
        emailService.sendPasswordResetConfirmation(user.getEmail());

        return ResponseEntity.ok(Map.of(
                "message", "Password reset successfully",
                "newPassword", newPassword
        ));
    }

    @PostMapping("/users/{id}/unlock")
    public ResponseEntity<Map<String, String>> unlockUser(@PathVariable Long id) {
        User user = userService.getUser(id);
        user.setFailedLoginAttempts(0);
        user.setMustResetPassword(true);
        userRepo.save(user);

        String currentEmail = authorizationService.getCurrentUserEmail();
        auditService.logAccountAction("ACCOUNT_UNLOCKED_BY_SUPER_ADMIN", currentEmail, id);

        return ResponseEntity.ok(Map.of("message", "Account unlocked successfully"));
    }

    @PostMapping("/orders/bulk-delete")
    @Transactional
    public ResponseEntity<Map<String, Object>> bulkDeleteOrders(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.getOrDefault("ids", List.of());
        int deleted = 0, skipped = 0;
        for (Long id : ids) {
            try {
                returnRequestRepo.deleteAll(returnRequestRepo.findByOrderId(id));
                orderRepo.deleteById(id);
                deleted++;
            } catch (Exception e) {
                skipped++;
            }
        }
        String currentEmail = authorizationService.getCurrentUserEmail();
        auditService.logAccountAction("ORDERS_BULK_DELETED_BY_SUPER_ADMIN", currentEmail, null);
        Map<String, Object> result = new HashMap<>();
        result.put("deleted", deleted);
        result.put("skipped", skipped);
        result.put("message", "Deleted " + deleted + " of " + ids.size() + " orders" + (skipped > 0 ? " (" + skipped + " skipped)" : ""));
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Map<String, String>> forceDeleteProduct(@PathVariable Long id) {
        productService.forceDeleteProduct(id);
        String currentEmail = authorizationService.getCurrentUserEmail();
        Long currentUserId = authorizationService.getCurrentUserId();
        auditService.log(currentUserId, currentEmail, "PRODUCT_FORCE_DELETED_BY_SUPER_ADMIN", "Product", id,
                "Product " + id + " force-deleted by " + currentEmail, null);
        return ResponseEntity.ok(Map.of("message", "Product permanently deleted"));
    }

    @PostMapping("/users/{id}/delete")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        User target = userService.getUser(id);
        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new AccessDeniedException("Cannot delete SUPER_ADMIN account");
        }
        target.setActive(false);
        target.setStatus("DELETED");
        // Email has a hard unique constraint, so a deleted account would otherwise
        // permanently block that address from ever being used again - free it up
        // while keeping the original traceable in the deleted record.
        if (target.getEmail() != null && !target.getEmail().startsWith("deleted_")) {
            target.setEmail("deleted_" + target.getId() + "_" + target.getEmail());
        }
        userRepo.save(target);
        String currentEmail = authorizationService.getCurrentUserEmail();
        auditService.logAccountAction("USER_DELETED_BY_SUPER_ADMIN", currentEmail, id);
        return ResponseEntity.ok(Map.of("success", "User deleted"));
    }

    @GetMapping("/permissions")
    public ResponseEntity<List<Permission>> getAllPermissions() {
        return ResponseEntity.ok(permissionRepo.findAll());
    }

    @GetMapping("/role-permissions")
    public ResponseEntity<Map<String, Object>> getRolePermissions(@RequestParam String role) {
        List<RolePermission> rps = rolePermissionRepo.findByRole(role);
        Set<Long> assigned = rps.stream()
                .map(rp -> rp.getPermission().getId())
                .collect(Collectors.toSet());
        Map<String, Object> result = new HashMap<>();
        result.put("role", role);
        result.put("permissionIds", assigned);
        result.put("permissions", rps);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/role-permissions")
    public ResponseEntity<Map<String, Object>> updateRolePermissions(@RequestBody Map<String, Object> body) {
        String role = (String) body.get("role");
        List<Integer> permIds = (List<Integer>) body.get("permissionIds");
        List<RolePermission> existing = rolePermissionRepo.findByRole(role);
        rolePermissionRepo.deleteAll(existing);
        for (Integer pid : permIds) {
            Permission perm = permissionRepo.findById(pid.longValue())
                    .orElseThrow(() -> new RuntimeException("Permission not found: " + pid));
            RolePermission rp = new RolePermission();
            rp.setRole(role);
            rp.setPermission(perm);
            rolePermissionRepo.save(rp);
        }
        String currentEmail = authorizationService.getCurrentUserEmail();
        auditService.log(authorizationService.getCurrentUserId(), currentEmail, "PERMISSIONS_UPDATED",
                "RolePermission", null, "Permissions updated for role: " + role, null);
        return ResponseEntity.ok(Map.of("message", "Permissions updated successfully"));
    }

    @GetMapping("/settings")
    public ResponseEntity<List<PlatformSetting>> getSettings() {
        return ResponseEntity.ok(platformSettingsService.getAllSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<List<PlatformSetting>> updateSettings(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(platformSettingsService.updateSettings(body));
    }

    @GetMapping("/settings/security")
    public ResponseEntity<List<PlatformSetting>> getSecuritySettings() {
        return ResponseEntity.ok(platformSettingsService.getSettingsByCategory("SECURITY"));
    }

    @PutMapping("/settings/security")
    public ResponseEntity<List<PlatformSetting>> updateSecuritySettings(@RequestBody Map<String, String> body) {
        body.forEach((key, value) -> {
            PlatformSetting s = new PlatformSetting();
            s.setSettingKey(key);
            s.setSettingValue(value);
            s.setCategory("SECURITY");
            platformSettingsService.updateSetting(key, value);
        });
        return ResponseEntity.ok(platformSettingsService.getSettingsByCategory("SECURITY"));
    }

    @PostMapping("/impersonate/start")
    public ResponseEntity<Map<String, Object>> startImpersonation(@RequestBody Map<String, Object> body) {
        Long impersonatorId = authorizationService.getCurrentUserId();
        Long targetUserId = Long.valueOf(body.get("targetUserId").toString());
        String reason = (String) body.getOrDefault("reason", "");
        return ResponseEntity.ok(impersonationService.startImpersonation(impersonatorId, targetUserId, reason));
    }

    @PostMapping("/impersonate/stop")
    public ResponseEntity<Map<String, String>> stopImpersonation(@RequestBody Map<String, Object> body) {
        Long sessionId = Long.valueOf(body.get("sessionId").toString());
        impersonationService.stopImpersonation(sessionId);
        return ResponseEntity.ok(Map.of("message", "Impersonation session ended"));
    }

    @GetMapping("/impersonate/sessions")
    public ResponseEntity<List<ImpersonationSession>> getActiveImpersonations() {
        return ResponseEntity.ok(impersonationService.getActiveSessions());
    }

    @GetMapping("/impersonate/log")
    public ResponseEntity<List<ImpersonationSession>> getImpersonationLog() {
        return ResponseEntity.ok(impersonationService.getImpersonationLog());
    }
}
