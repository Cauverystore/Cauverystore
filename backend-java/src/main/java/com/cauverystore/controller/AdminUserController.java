package com.cauverystore.controller;

import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.exception.AccessDeniedException;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.service.AuditService;
import com.cauverystore.service.AuthorizationService;
import com.cauverystore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin("*")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminUserController {

    private final UserService userService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final UserRepository userRepo;

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
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
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<User> changeRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newRole = body.get("role");
        User target = userService.getUser(id);
        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new AccessDeniedException("Cannot modify SUPER_ADMIN role");
        }
        Role oldRole = target.getRole();
        User updated = userService.updateUserRole(id, newRole);
        String currentEmail = authorizationService.getCurrentUserEmail();
        auditService.logRoleChange(currentEmail, id, oldRole, updated.getRole());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<Map<String, Object>> suspendUser(@PathVariable Long id) {
        Long currentUserId = authorizationService.getCurrentUserId();
        String currentEmail = authorizationService.getCurrentUserEmail();
        User target = userService.getUser(id);
        if (target.getRole() == Role.SUPER_ADMIN || target.getRole() == Role.ADMIN) {
            throw new AccessDeniedException("Cannot suspend ADMIN or SUPER_ADMIN accounts");
        }
        User updated = userService.suspendUser(id, currentUserId);
        auditService.logSuspend(currentEmail, currentUserId, id,
                target.getRole().name(), target.getEmail());
        return ResponseEntity.ok(Map.of("message", "User suspended successfully",
                "userId", id, "status", updated.getStatus(),
                "suspendedBy", updated.getSuspendedBy(), "suspendedAt", updated.getSuspendedAt()));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<Map<String, Object>> revokeUser(@PathVariable Long id) {
        Long currentUserId = authorizationService.getCurrentUserId();
        String currentEmail = authorizationService.getCurrentUserEmail();
        User target = userService.getUser(id);
        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new AccessDeniedException("Cannot modify SUPER_ADMIN account");
        }
        if (target.getStatus() == null || !target.getStatus().equals("SUSPENDED")) {
            throw new RuntimeException("User is not currently suspended");
        }
        User updated = userService.revokeUser(id);
        auditService.logRevoke(currentEmail, currentUserId, id,
                target.getRole().name(), target.getEmail());
        return ResponseEntity.ok(Map.of("message", "User suspension revoked successfully",
                "userId", id, "status", updated.getStatus()));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<User> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        User target = userService.getUser(id);
        if (target.getRole() == Role.SUPER_ADMIN && !"ACTIVE".equalsIgnoreCase(status)) {
            throw new AccessDeniedException("Cannot suspend SUPER_ADMIN account");
        }
        User updated;
        if ("BLOCKED".equalsIgnoreCase(status)) {
            updated = userService.blockUser(id);
        } else {
            updated = userService.unblockUser(id);
        }
        String currentEmail = authorizationService.getCurrentUserEmail();
        auditService.logAccountAction("STATUS_CHANGE_TO_" + status.toUpperCase(), currentEmail, id);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        User target = userService.getUser(id);
        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new AccessDeniedException("Cannot delete SUPER_ADMIN account");
        }
        target.setActive(false);
        target.setStatus("DELETED");
        userRepo.save(target);
        String currentEmail = authorizationService.getCurrentUserEmail();
        auditService.logAccountAction("USER_DELETED", currentEmail, id);
        return ResponseEntity.ok(Map.of("message", "User soft-deleted successfully"));
    }
}
