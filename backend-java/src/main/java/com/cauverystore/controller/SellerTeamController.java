package com.cauverystore.controller;

import com.cauverystore.service.SellerAccessService;
import com.cauverystore.service.SellerPermissions;
import com.cauverystore.service.SellerTeamService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Seller teams: roles (POST/PUT/DELETE), permissions (GET), members and invitations.
 *
 * Everything but the invitation look-up and acceptance works on the store the person is
 * currently working in (see SellerAccessService) and needs the team.manage permission there.
 */
@RestController
@RequestMapping("/api/seller/team")
public class SellerTeamController {

    private final SellerTeamService teamService;
    private final SellerAccessService access;

    public SellerTeamController(SellerTeamService teamService, SellerAccessService access) {
        this.teamService = teamService;
        this.access = access;
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getDetails() instanceof Long id ? id : null;
    }

    /** The stores this person can work in, for the seller panel's store switcher. */
    @GetMapping("/my-stores")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> myStores() {
        Long userId = currentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return ResponseEntity.ok(teamService.myStores(userId));
    }

    /** What the person may do in the store they are working in now. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> me() {
        SellerAccessService.Context c = access.context();
        if (c == null) return ResponseEntity.status(403).body(Map.of("error", "You do not have access to a seller account."));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sellerId", c.sellerId());
        out.put("storeName", teamService.storeName(c.sellerId()));
        out.put("owner", c.owner());
        out.put("roleCode", c.roleCode());
        out.put("permissions", c.owner() ? SellerPermissions.ALL.keySet() : c.permissions());
        return ResponseEntity.ok(out);
    }

    @GetMapping("/permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> permissions() {
        access.require(SellerPermissions.DASHBOARD_VIEW);
        List<Map<String, Object>> out = new ArrayList<>();
        SellerPermissions.ALL.forEach((code, label) -> out.add(Map.of("code", code, "label", label)));
        return ResponseEntity.ok(out);
    }

    @GetMapping("/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> roles() {
        var c = access.require(SellerPermissions.TEAM_MANAGE);
        return ResponseEntity.ok(teamService.roles(c.sellerId()));
    }

    @PostMapping("/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createRole(@RequestBody Map<String, Object> body) {
        var c = access.require(SellerPermissions.TEAM_MANAGE);
        return handle(() -> ResponseEntity.status(HttpStatus.CREATED).body(teamService.saveRole(c.sellerId(), c.userId(), null, body)));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateRole(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        var c = access.require(SellerPermissions.TEAM_MANAGE);
        return handle(() -> ResponseEntity.ok(teamService.saveRole(c.sellerId(), c.userId(), id, body)));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteRole(@PathVariable Long id) {
        var c = access.require(SellerPermissions.TEAM_MANAGE);
        return handle(() -> {
            teamService.deleteRole(c.sellerId(), c.userId(), id);
            return ResponseEntity.ok(Map.of("message", "Role deleted"));
        });
    }

    @GetMapping("/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> members() {
        var c = access.require(SellerPermissions.TEAM_MANAGE);
        return ResponseEntity.ok(teamService.members(c.sellerId()));
    }

    @PutMapping("/members/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changeRole(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        var c = access.require(SellerPermissions.TEAM_MANAGE);
        return handle(() -> ResponseEntity.ok(teamService.changeRole(c.sellerId(), c.userId(), id, asLong(body.get("roleId")))));
    }

    @DeleteMapping("/members/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> removeMember(@PathVariable Long id) {
        var c = access.require(SellerPermissions.TEAM_MANAGE);
        return handle(() -> {
            teamService.removeMember(c.sellerId(), c.userId(), id);
            return ResponseEntity.ok(Map.of("message", "Removed from the team"));
        });
    }

    @GetMapping("/invitations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> invitations() {
        var c = access.require(SellerPermissions.TEAM_MANAGE);
        return ResponseEntity.ok(teamService.invitations(c.sellerId()));
    }

    @PostMapping("/invitations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> invite(@RequestBody Map<String, Object> body) {
        var c = access.require(SellerPermissions.TEAM_MANAGE);
        return handle(() -> ResponseEntity.status(HttpStatus.CREATED).body(teamService.invite(
                c.sellerId(), c.userId(), body.get("email") == null ? null : body.get("email").toString(), asLong(body.get("roleId")))));
    }

    @DeleteMapping("/invitations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> revoke(@PathVariable Long id) {
        var c = access.require(SellerPermissions.TEAM_MANAGE);
        return handle(() -> {
            teamService.revokeInvitation(c.sellerId(), c.userId(), id);
            return ResponseEntity.ok(Map.of("message", "Invitation cancelled"));
        });
    }

    /** Open to anyone holding the link: shows which store and role, with the email masked. */
    @GetMapping("/invitations/lookup")
    public ResponseEntity<?> lookup(@RequestParam String token) {
        return ResponseEntity.ok(teamService.lookup(token));
    }

    @PostMapping("/invitations/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> accept(@RequestBody Map<String, Object> body) {
        Long userId = currentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        return handle(() -> ResponseEntity.ok(teamService.accept(body.get("token") == null ? null : body.get("token").toString(), userId)));
    }

    private static ResponseEntity<?> handle(java.util.function.Supplier<ResponseEntity<?>> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static Long asLong(Object o) {
        try { return o == null ? null : Long.valueOf(o.toString()); } catch (NumberFormatException e) { return null; }
    }
}
