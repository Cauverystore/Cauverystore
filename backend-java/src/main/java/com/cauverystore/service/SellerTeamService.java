package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.exception.AccessDeniedException;
import com.cauverystore.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Seller teams: the roles a seller can hand out, who is on the team, and invitations to join.
 *
 * Every method takes the seller account it works on and the person doing it; whether that person
 * may do it at all is checked by the controller through SellerAccessService before calling here.
 */
@Service
public class SellerTeamService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int INVITE_DAYS = 7;
    private static final int MAX_CUSTOM_ROLES = 20;
    private static final int MAX_PENDING_INVITES = 20;

    private final SellerTeamRoleRepository roleRepo;
    private final SellerTeamMemberRepository memberRepo;
    private final SellerInvitationRepository inviteRepo;
    private final UserRepository userRepo;
    private final SellerStoreRepository storeRepo;
    private final SellerRegistrationRepository regRepo;
    private final EmailService emailService;
    private final AuditService auditService;
    private final String frontendUrl;

    public SellerTeamService(SellerTeamRoleRepository roleRepo, SellerTeamMemberRepository memberRepo,
                             SellerInvitationRepository inviteRepo, UserRepository userRepo,
                             SellerStoreRepository storeRepo, SellerRegistrationRepository regRepo,
                             EmailService emailService, AuditService auditService,
                             @Value("${frontend.url:https://cauverystore.in}") String frontendUrl) {
        this.roleRepo = roleRepo;
        this.memberRepo = memberRepo;
        this.inviteRepo = inviteRepo;
        this.userRepo = userRepo;
        this.storeRepo = storeRepo;
        this.regRepo = regRepo;
        this.emailService = emailService;
        this.auditService = auditService;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    // ------------------------------------------------------------------ roles

    /** Creates the built-in roles, and keeps their permissions in step with the code. Safe to repeat. */
    @Transactional
    public void ensureBuiltInRoles() {
        for (SellerPermissions.BuiltInRole b : SellerPermissions.BUILT_IN) {
            SellerTeamRole r = roleRepo.findByBuiltInTrueAndCode(b.code()).orElseGet(() -> {
                SellerTeamRole n = new SellerTeamRole();
                n.setBuiltIn(true);
                n.setCode(b.code());
                return n;
            });
            r.setName(b.name());
            r.setDescription(b.description());
            if (!r.getPermissions().equals(b.permissions())) r.setPermissions(new LinkedHashSet<>(b.permissions()));
            roleRepo.save(r);
        }
    }

    public List<Map<String, Object>> roles(Long sellerId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SellerTeamRole r : roleRepo.findByBuiltInTrueOrderByIdAsc()) out.add(roleMap(r));
        for (SellerTeamRole r : roleRepo.findBySellerIdOrderByIdAsc(sellerId)) out.add(roleMap(r));
        return out;
    }

    @Transactional
    public Map<String, Object> saveRole(Long sellerId, Long actorId, Long roleId, Map<String, Object> body) {
        SellerTeamRole r;
        if (roleId == null) {
            if (roleRepo.findBySellerIdOrderByIdAsc(sellerId).size() >= MAX_CUSTOM_ROLES) {
                throw new IllegalArgumentException("A store can have at most " + MAX_CUSTOM_ROLES + " custom roles.");
            }
            r = new SellerTeamRole();
            r.setSellerId(sellerId);
            r.setBuiltIn(false);
        } else {
            r = ownCustomRole(sellerId, roleId);
        }
        String name = text(body.get("name"));
        if (name == null || name.length() > 80) throw new IllegalArgumentException("Give the role a name (up to 80 characters).");
        Set<String> perms = new LinkedHashSet<>();
        Object raw = body.get("permissions");
        if (raw instanceof Collection<?> c) {
            for (Object p : c) {
                String code = String.valueOf(p);
                if (!SellerPermissions.isKnown(code)) throw new IllegalArgumentException("Unknown permission: " + code);
                perms.add(code);
            }
        }
        if (perms.isEmpty()) throw new IllegalArgumentException("Choose at least one thing this role may do.");
        perms.add(SellerPermissions.DASHBOARD_VIEW);
        r.setName(name);
        r.setDescription(text(body.get("description")));
        r.setPermissions(perms);
        if (r.getCode() == null) r.setCode("CUSTOM");
        r = roleRepo.save(r);
        if ("CUSTOM".equals(r.getCode())) {
            r.setCode("CUSTOM_" + r.getId());
            r = roleRepo.save(r);
        }
        auditService.log(actorId, null, roleId == null ? "SELLER_ROLE_CREATED" : "SELLER_ROLE_UPDATED",
                "SellerTeamRole", r.getId(), "Seller " + sellerId + ": " + name + " " + perms, null);
        return roleMap(r);
    }

    @Transactional
    public void deleteRole(Long sellerId, Long actorId, Long roleId) {
        SellerTeamRole r = ownCustomRole(sellerId, roleId);
        if (memberRepo.countByRoleIdAndStatus(roleId, "ACTIVE") > 0 || inviteRepo.countByRoleIdAndStatus(roleId, "PENDING") > 0) {
            throw new IllegalArgumentException("Move the people (and pending invitations) using this role to another role first.");
        }
        roleRepo.delete(r);
        auditService.log(actorId, null, "SELLER_ROLE_DELETED", "SellerTeamRole", roleId, "Seller " + sellerId + ": " + r.getName(), null);
    }

    // ---------------------------------------------------------------- members

    public List<Map<String, Object>> members(Long sellerId) {
        List<Map<String, Object>> out = new ArrayList<>();
        userRepo.findById(sellerId).ifPresent(owner -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", null);
            m.put("userId", owner.getId());
            m.put("name", displayName(owner));
            m.put("email", owner.getEmail());
            m.put("roleCode", "OWNER");
            m.put("roleName", "Account owner");
            m.put("accountOwner", true);
            m.put("status", "ACTIVE");
            out.add(m);
        });
        for (SellerTeamMember tm : memberRepo.findBySellerIdOrderByIdAsc(sellerId)) {
            if (!"ACTIVE".equals(tm.getStatus())) continue;
            User u = userRepo.findById(tm.getUserId()).orElse(null);
            SellerTeamRole r = roleRepo.findById(tm.getRoleId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", tm.getId());
            m.put("userId", tm.getUserId());
            m.put("name", u != null ? displayName(u) : "User #" + tm.getUserId());
            m.put("email", u != null ? u.getEmail() : null);
            m.put("roleId", tm.getRoleId());
            m.put("roleCode", r != null ? r.getCode() : null);
            m.put("roleName", r != null ? r.getName() : "Role removed");
            m.put("accountOwner", false);
            m.put("status", tm.getStatus());
            m.put("joinedAt", tm.getJoinedAt());
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> changeRole(Long sellerId, Long actorId, Long memberId, Long roleId) {
        SellerTeamMember m = ownMember(sellerId, memberId);
        if (m.getUserId().equals(actorId)) throw new AccessDeniedException("You cannot change your own role. Ask the store owner.");
        SellerTeamRole r = usableRole(sellerId, roleId);
        m.setRoleId(r.getId());
        memberRepo.save(m);
        auditService.log(actorId, null, "SELLER_TEAM_ROLE_CHANGED", "SellerTeamMember", m.getId(),
                "Seller " + sellerId + ": user " + m.getUserId() + " is now " + r.getCode(), null);
        return Map.of("id", m.getId(), "roleId", r.getId(), "roleName", r.getName());
    }

    @Transactional
    public void removeMember(Long sellerId, Long actorId, Long memberId) {
        SellerTeamMember m = ownMember(sellerId, memberId);
        if (m.getUserId().equals(actorId)) throw new AccessDeniedException("You cannot remove yourself. Ask the store owner.");
        m.setStatus("REMOVED");
        m.setRemovedAt(LocalDateTime.now());
        m.setRemovedBy(actorId);
        memberRepo.save(m);
        auditService.log(actorId, null, "SELLER_TEAM_MEMBER_REMOVED", "SellerTeamMember", m.getId(),
                "Seller " + sellerId + ": removed user " + m.getUserId(), null);
    }

    // ------------------------------------------------------------ invitations

    @Transactional
    public Map<String, Object> invite(Long sellerId, Long actorId, String rawEmail, Long roleId) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) throw new IllegalArgumentException("Enter a valid email address.");
        SellerTeamRole role = usableRole(sellerId, roleId);
        User owner = userRepo.findById(sellerId).orElseThrow(() -> new IllegalArgumentException("Seller not found."));
        if (email.equalsIgnoreCase(owner.getEmail())) throw new IllegalArgumentException("That is the store owner's own email.");
        User existing = userRepo.findByEmail(email);
        if (existing != null) {
            if (isStaff(existing)) throw new IllegalArgumentException("Marketplace staff accounts cannot join a seller's team.");
            if (memberRepo.findBySellerIdAndUserIdAndStatus(sellerId, existing.getId(), "ACTIVE").isPresent()) {
                throw new IllegalArgumentException("That person is already on your team.");
            }
        }
        if (inviteRepo.findBySellerIdAndStatusOrderByIdDesc(sellerId, "PENDING").size() >= MAX_PENDING_INVITES) {
            throw new IllegalArgumentException("You have " + MAX_PENDING_INVITES + " invitations waiting. Cancel some first.");
        }
        // A fresh invitation replaces any earlier one to the same address.
        for (SellerInvitation old : inviteRepo.findBySellerIdAndEmailIgnoreCaseAndStatus(sellerId, email, "PENDING")) {
            old.setStatus("REVOKED");
            inviteRepo.save(old);
        }
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);

        SellerInvitation inv = new SellerInvitation();
        inv.setSellerId(sellerId);
        inv.setEmail(email);
        inv.setRoleId(role.getId());
        inv.setTokenHash(sha256(token));
        inv.setExpiresAt(LocalDateTime.now().plusDays(INVITE_DAYS));
        inv.setInvitedBy(actorId);
        inv = inviteRepo.save(inv);

        String inviter = userRepo.findById(actorId).map(SellerTeamService::displayName).orElse(null);
        emailService.sendSellerInvitation(email, storeName(sellerId), role.getName(), inviter,
                frontendUrl + "/seller/invite?token=" + token);
        auditService.log(actorId, null, "SELLER_TEAM_INVITED", "SellerInvitation", inv.getId(),
                "Seller " + sellerId + ": invited " + email + " as " + role.getCode(), null);
        return inviteMap(inv, role);
    }

    public List<Map<String, Object>> invitations(Long sellerId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SellerInvitation inv : inviteRepo.findBySellerIdAndStatusOrderByIdDesc(sellerId, "PENDING")) {
            out.add(inviteMap(inv, roleRepo.findById(inv.getRoleId()).orElse(null)));
        }
        return out;
    }

    @Transactional
    public void revokeInvitation(Long sellerId, Long actorId, Long inviteId) {
        SellerInvitation inv = inviteRepo.findById(inviteId).filter(i -> i.getSellerId().equals(sellerId))
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found."));
        if (!"PENDING".equals(inv.getStatus())) throw new IllegalArgumentException("This invitation is no longer open.");
        inv.setStatus("REVOKED");
        inviteRepo.save(inv);
        auditService.log(actorId, null, "SELLER_TEAM_INVITE_REVOKED", "SellerInvitation", inv.getId(),
                "Seller " + sellerId + ": " + inv.getEmail(), null);
    }

    /** What the invitation page may show before sign-in: the store and role, and a masked email. */
    public Map<String, Object> lookup(String token) {
        SellerInvitation inv = byToken(token);
        Map<String, Object> m = new LinkedHashMap<>();
        if (inv == null) {
            m.put("valid", false);
            m.put("reason", "This invitation link is not valid.");
            return m;
        }
        SellerTeamRole role = roleRepo.findById(inv.getRoleId()).orElse(null);
        m.put("storeName", storeName(inv.getSellerId()));
        m.put("roleName", role != null ? role.getName() : null);
        m.put("email", maskEmail(inv.getEmail()));
        boolean open = "PENDING".equals(inv.getStatus()) && !inv.isExpired();
        m.put("valid", open);
        if (!open) {
            m.put("reason", "ACCEPTED".equals(inv.getStatus()) ? "This invitation has already been used."
                    : "REVOKED".equals(inv.getStatus()) ? "This invitation was cancelled."
                    : "This invitation has expired. Ask the store owner to send a new one.");
        }
        return m;
    }

    @Transactional
    public Map<String, Object> accept(String token, Long userId) {
        SellerInvitation inv = byToken(token);
        if (inv == null || !"PENDING".equals(inv.getStatus()) || inv.isExpired()) {
            throw new IllegalArgumentException("This invitation is not valid any more. Ask the store owner to send a new one.");
        }
        User user = userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(inv.getEmail())) {
            throw new AccessDeniedException("This invitation was sent to " + maskEmail(inv.getEmail())
                    + ". Sign in with that email address to accept it.");
        }
        if (isStaff(user)) throw new AccessDeniedException("Marketplace staff accounts cannot join a seller's team.");
        if (user.getId().equals(inv.getSellerId())) throw new IllegalArgumentException("This is your own store.");

        SellerTeamMember m = memberRepo.findBySellerIdAndUserId(inv.getSellerId(), userId).orElseGet(SellerTeamMember::new);
        m.setSellerId(inv.getSellerId());
        m.setUserId(userId);
        m.setRoleId(inv.getRoleId());
        m.setStatus("ACTIVE");
        m.setInvitedBy(inv.getInvitedBy());
        m.setJoinedAt(LocalDateTime.now());
        m.setRemovedAt(null);
        m.setRemovedBy(null);
        m = memberRepo.save(m);

        inv.setStatus("ACCEPTED");
        inv.setAcceptedUserId(userId);
        inv.setAcceptedAt(LocalDateTime.now());
        inviteRepo.save(inv);
        auditService.log(userId, user.getEmail(), "SELLER_TEAM_JOINED", "SellerTeamMember", m.getId(),
                "Joined seller " + inv.getSellerId() + " via invitation " + inv.getId(), null);
        return Map.of("sellerId", inv.getSellerId(), "storeName", storeName(inv.getSellerId()));
    }

    /** Every store this person can work in: their own, then the teams they are on. */
    public List<Map<String, Object>> myStores(Long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return out;
        if (user.getRole() == Role.SELLER) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sellerId", userId);
            m.put("storeName", storeName(userId));
            m.put("roleCode", "OWNER");
            m.put("roleName", "Account owner");
            m.put("owner", true);
            m.put("permissions", SellerPermissions.ALL.keySet());
            out.add(m);
        }
        List<SellerTeamMember> list = new ArrayList<>(memberRepo.findByUserIdAndStatus(userId, "ACTIVE"));
        list.sort(Comparator.comparing(SellerTeamMember::getId));
        for (SellerTeamMember tm : list) {
            SellerTeamRole r = roleRepo.findById(tm.getRoleId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sellerId", tm.getSellerId());
            m.put("storeName", storeName(tm.getSellerId()));
            m.put("roleCode", r != null ? r.getCode() : null);
            m.put("roleName", r != null ? r.getName() : null);
            m.put("owner", false);
            m.put("permissions", r != null ? r.getPermissions() : Set.of());
            out.add(m);
        }
        return out;
    }

    // ----------------------------------------------------------------- helpers

    private SellerTeamRole ownCustomRole(Long sellerId, Long roleId) {
        SellerTeamRole r = roleRepo.findById(roleId).orElseThrow(() -> new IllegalArgumentException("Role not found."));
        if (Boolean.TRUE.equals(r.getBuiltIn())) throw new IllegalArgumentException("Built-in roles cannot be changed. Make a new role instead.");
        if (!sellerId.equals(r.getSellerId())) throw new IllegalArgumentException("Role not found.");
        return r;
    }

    /** A role this seller may hand out: any built-in, or one of their own. */
    private SellerTeamRole usableRole(Long sellerId, Long roleId) {
        if (roleId == null) throw new IllegalArgumentException("Choose a role.");
        SellerTeamRole r = roleRepo.findById(roleId).orElseThrow(() -> new IllegalArgumentException("Role not found."));
        if (!Boolean.TRUE.equals(r.getBuiltIn()) && !sellerId.equals(r.getSellerId())) throw new IllegalArgumentException("Role not found.");
        return r;
    }

    private SellerTeamMember ownMember(Long sellerId, Long memberId) {
        return memberRepo.findById(memberId)
                .filter(m -> m.getSellerId().equals(sellerId) && "ACTIVE".equals(m.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Team member not found."));
    }

    private SellerInvitation byToken(String token) {
        if (token == null || !token.matches("[0-9a-f]{64}")) return null;
        return inviteRepo.findByTokenHash(sha256(token)).orElse(null);
    }

    public String storeName(Long sellerId) {
        String name = storeRepo.findBySellerId(sellerId).map(SellerStore::getStoreName).filter(n -> n != null && !n.isBlank()).orElse(null);
        if (name == null) name = regRepo.findByUserId(sellerId).map(SellerRegistration::getBusinessName).filter(n -> n != null && !n.isBlank()).orElse(null);
        if (name == null) name = userRepo.findById(sellerId).map(SellerTeamService::displayName).orElse("Seller #" + sellerId);
        return name;
    }

    private static boolean isStaff(User u) {
        return u.getRole() == Role.ADMIN || u.getRole() == Role.SUPER_ADMIN || u.getRole() == Role.EXECUTIVE;
    }

    private static String displayName(User u) {
        return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail();
    }

    static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "*".repeat(Math.max(1, at - 2)) + email.charAt(at - 1) + email.substring(at);
    }

    private static Map<String, Object> roleMap(SellerTeamRole r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("code", r.getCode());
        m.put("name", r.getName());
        m.put("description", r.getDescription());
        m.put("builtIn", Boolean.TRUE.equals(r.getBuiltIn()));
        m.put("permissions", r.getPermissions());
        return m;
    }

    private static Map<String, Object> inviteMap(SellerInvitation inv, SellerTeamRole role) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", inv.getId());
        m.put("email", inv.getEmail());
        m.put("roleId", inv.getRoleId());
        m.put("roleName", role != null ? role.getName() : null);
        m.put("expiresAt", inv.getExpiresAt());
        m.put("expired", inv.isExpired());
        m.put("createdAt", inv.getCreatedAt());
        return m;
    }

    private static String text(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
