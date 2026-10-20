package com.cauverystore.service;

import com.cauverystore.entities.Role;
import com.cauverystore.entities.SellerTeamMember;
import com.cauverystore.entities.SellerTeamRole;
import com.cauverystore.entities.User;
import com.cauverystore.exception.AccessDeniedException;
import com.cauverystore.repository.SellerTeamMemberRepository;
import com.cauverystore.repository.SellerTeamRoleRepository;
import com.cauverystore.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Who may work in which seller account. A mistake here hands one seller's orders, payouts or
 * products to someone else, so each way in is pinned down.
 */
class SellerAccessServiceTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final SellerTeamMemberRepository memberRepo = mock(SellerTeamMemberRepository.class);
    private final SellerTeamRoleRepository roleRepo = mock(SellerTeamRoleRepository.class);
    private final SellerAccessService access = new SellerAccessService(userRepo, memberRepo, roleRepo);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void signedIn(long userId, Role role, String sellerHeader) {
        User u = new User();
        u.setId(userId);
        u.setRole(role);
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        var auth = new UsernamePasswordAuthenticationToken("u" + userId, null, List.of());
        auth.setDetails(userId);
        SecurityContextHolder.getContext().setAuthentication(auth);
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (sellerHeader != null) req.addHeader(SellerAccessService.HEADER, sellerHeader);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    private SellerTeamMember membership(long sellerId, long userId, long roleId, String... perms) {
        SellerTeamMember m = new SellerTeamMember();
        m.setSellerId(sellerId);
        m.setUserId(userId);
        m.setRoleId(roleId);
        SellerTeamRole r = new SellerTeamRole();
        r.setCode("ORDER_MANAGER");
        r.setPermissions(new LinkedHashSet<>(Arrays.asList(perms)));
        when(roleRepo.findById(roleId)).thenReturn(Optional.of(r));
        return m;
    }

    @Test
    void aSellerWorksInTheirOwnAccountWithEveryPermission() {
        signedIn(10, Role.SELLER, null);
        var c = access.require(SellerPermissions.TEAM_MANAGE);
        assertEquals(10L, c.sellerId());
        assertTrue(c.owner());
    }

    @Test
    void aTeamMemberWorksInTheStoreTheyNameWithTheirRolesPermissions() {
        signedIn(20, Role.CUSTOMER, "10");
        SellerTeamMember m = membership(10, 20, 5, SellerPermissions.ORDERS_VIEW);
        when(memberRepo.findBySellerIdAndUserIdAndStatus(10L, 20L, "ACTIVE")).thenReturn(Optional.of(m));

        assertEquals(10L, access.actingSellerId());
        assertEquals(10L, access.require(SellerPermissions.ORDERS_VIEW).sellerId());
        assertThrows(AccessDeniedException.class, () -> access.require(SellerPermissions.PAYOUTS_VIEW));
    }

    @Test
    void namingAStoreYouAreNotOnIsRefusedNotIgnored() {
        signedIn(20, Role.CUSTOMER, "99");
        when(memberRepo.findBySellerIdAndUserIdAndStatus(99L, 20L, "ACTIVE")).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, access::actingSellerId);
    }

    @Test
    void aSellerNamingAnotherSellersStoreNeedsToBeOnThatTeam() {
        signedIn(10, Role.SELLER, "11");
        when(memberRepo.findBySellerIdAndUserIdAndStatus(11L, 10L, "ACTIVE")).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, access::actingSellerId);
    }

    @Test
    void aCustomerWithNoTeamHasNoSellerAccess() {
        signedIn(30, Role.CUSTOMER, null);
        when(memberRepo.findByUserIdAndStatus(30L, "ACTIVE")).thenReturn(new ArrayList<>());
        assertNull(access.actingSellerId());
        assertThrows(AccessDeniedException.class, () -> access.require(SellerPermissions.DASHBOARD_VIEW));
    }

    @Test
    void withoutAHeaderAMemberLandsInTheirOnlyStore() {
        signedIn(20, Role.CUSTOMER, null);
        SellerTeamMember m = membership(10, 20, 5, SellerPermissions.DASHBOARD_VIEW);
        when(memberRepo.findByUserIdAndStatus(20L, "ACTIVE")).thenReturn(new ArrayList<>(List.of(m)));
        assertEquals(10L, access.actingSellerId());
        assertFalse(access.context().owner());
    }

    @Test
    void builtInOwnerRoleHoldsEveryPermissionAndOthersDoNotManageTheTeam() {
        for (SellerPermissions.BuiltInRole r : SellerPermissions.BUILT_IN) {
            if (r.code().equals("OWNER")) assertEquals(SellerPermissions.ALL.keySet(), r.permissions());
            else assertFalse(r.permissions().contains(SellerPermissions.TEAM_MANAGE), r.code());
            assertTrue(r.permissions().contains(SellerPermissions.DASHBOARD_VIEW), r.code());
        }
    }
}
