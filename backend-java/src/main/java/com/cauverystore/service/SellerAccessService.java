package com.cauverystore.service;

import com.cauverystore.entities.Role;
import com.cauverystore.entities.SellerTeamMember;
import com.cauverystore.entities.SellerTeamRole;
import com.cauverystore.entities.User;
import com.cauverystore.exception.AccessDeniedException;
import com.cauverystore.repository.SellerTeamMemberRepository;
import com.cauverystore.repository.SellerTeamRoleRepository;
import com.cauverystore.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

/**
 * Which seller account the signed-in person is working in, and what they may do there.
 *
 * <ul>
 *   <li>A seller working in their own account may do everything, as before.</li>
 *   <li>A team member works in the account named by the {@code X-Seller-Id} header (the seller
 *       panel's store switcher sends it), or in their only store when they have just one. What
 *       they may do comes from their role.</li>
 *   <li>Naming a store you are not on the team of is refused outright, never quietly ignored.</li>
 * </ul>
 *
 * Every seller-panel endpoint asks this class rather than taking the user's own id, which is how
 * one login can reach several stores without any product or order changing hands.
 */
@Service
public class SellerAccessService {

    public static final String HEADER = "X-Seller-Id";
    private static final String CACHE_KEY = SellerAccessService.class.getName() + ".context";

    private final UserRepository userRepo;
    private final SellerTeamMemberRepository memberRepo;
    private final SellerTeamRoleRepository roleRepo;

    public SellerAccessService(UserRepository userRepo, SellerTeamMemberRepository memberRepo,
                               SellerTeamRoleRepository roleRepo) {
        this.userRepo = userRepo;
        this.memberRepo = memberRepo;
        this.roleRepo = roleRepo;
    }

    /** The resolved context for this request: who, for which seller, with which permissions. */
    public record Context(Long userId, Long sellerId, boolean owner, Set<String> permissions, String roleCode) {
        public boolean can(String permission) {
            return owner || permissions.contains(permission);
        }
    }

    /** The seller this request acts for, or null when the person has no seller access at all. */
    public Long actingSellerId() {
        Context c = context();
        return c == null ? null : c.sellerId();
    }

    /** Refuses the request unless the person may do this in the store they are working in. */
    public Context require(String permission) {
        Context c = context();
        if (c == null) throw new AccessDeniedException("You do not have access to a seller account.");
        if (!c.can(permission)) {
            throw new AccessDeniedException("Your role on this store does not allow this. Ask the store owner for access.");
        }
        return c;
    }

    /** True when the signed-in person is a team member somewhere (not counting their own account). */
    public boolean isTeamMember(Long userId) {
        return userId != null && memberRepo.existsByUserIdAndStatus(userId, "ACTIVE");
    }

    public Context context() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            Object cached = attrs.getAttribute(CACHE_KEY, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof Optional<?> o) return (Context) o.orElse(null);
        }
        Context c = resolve();
        if (attrs != null) attrs.setAttribute(CACHE_KEY, Optional.ofNullable(c), RequestAttributes.SCOPE_REQUEST);
        return c;
    }

    private Context resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof Long userId)) return null;

        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return null;
        boolean ownSellerAccount = user.getRole() == Role.SELLER;
        boolean superAdmin = user.getRole() == Role.SUPER_ADMIN;

        Long requested = requestedSellerId();
        if (requested != null && !requested.equals(userId)) {
            SellerTeamMember m = memberRepo.findBySellerIdAndUserIdAndStatus(requested, userId, "ACTIVE")
                    .orElseThrow(() -> new AccessDeniedException("You are not on the team of that store."));
            return memberContext(userId, m);
        }
        if (ownSellerAccount || superAdmin) {
            return new Context(userId, userId, true, SellerPermissions.ALL.keySet(), "OWNER");
        }
        List<SellerTeamMember> memberships = memberRepo.findByUserIdAndStatus(userId, "ACTIVE");
        if (memberships.isEmpty()) return null;
        memberships.sort(Comparator.comparing(SellerTeamMember::getId));
        return memberContext(userId, memberships.get(0));
    }

    private Context memberContext(Long userId, SellerTeamMember m) {
        SellerTeamRole role = roleRepo.findById(m.getRoleId()).orElse(null);
        Set<String> perms = role == null ? Set.of() : Set.copyOf(role.getPermissions());
        return new Context(userId, m.getSellerId(), false, perms, role == null ? null : role.getCode());
    }

    private static Long requestedSellerId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) return null;
        String raw = sra.getRequest().getHeader(HEADER);
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("That store could not be found.");
        }
    }
}
