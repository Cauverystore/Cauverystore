package com.cauverystore.service;

import com.cauverystore.entities.User;
import com.cauverystore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Stopping one account - a customer or a seller, the mechanism is the same.
 *
 * Two different things share the word. SUSPENDED is a wind-down: no new business, but the orders
 * already placed run to their end, return window included, so the account keeps its session and
 * can still be signed into. BLOCKED is the hard stop and does end the session.
 *
 * The distinction matters because obligations outlive the decision. Signing a suspended seller
 * out strands the orders they still owe goods for; signing a suspended buyer out takes away the
 * return they are entitled to. Which is why suspension deliberately does not do it.
 */
@ExtendWith(MockitoExtension.class)
class AccountSuspensionTest {

    @Mock private UserRepository userRepo;

    private UserService userService;
    private User customer;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepo);
        customer = new User();
        customer.setId(7L);
        customer.setEmail("buyer@example.com");
        customer.setStatus("ACTIVE");
        customer.setActive(true);
        customer.setTokenVersion(3);
        customer.setRefreshToken("a-live-refresh-token");
        when(userRepo.findById(7L)).thenReturn(Optional.of(customer));
        when(userRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void suspendingKeepsTheSessionSoOrdersCanBeSeenOut() {
        // Bumping the token version here would sign them out on the next request, which is
        // exactly wrong for a wind-down: a seller could not ship what they owe and a buyer could
        // not ask for a return. What they cannot do is start something new, and that is refused
        // where it is attempted rather than by cutting off access.
        userService.suspendUser(7L, 1L, "Chargeback fraud");

        assertEquals(3, customer.getTokenVersion(), "a wind-down must not end the session");
        assertEquals("SUSPENDED", customer.getStatus());
        assertNull(customer.getRefreshToken(), "no new session may be minted either");
    }

    @Test
    void theReasonIsRecordedSoThePersonCanBeToldWhy() {
        userService.suspendUser(7L, 1L, "Repeated fraudulent returns");

        assertEquals("Repeated fraudulent returns", customer.getSuspensionReason());
        assertEquals(1L, customer.getSuspendedBy());
        assertNotNull(customer.getSuspendedAt());
    }

    @Test
    void blockingIsTheHardStopAndDoesEndTheSession() {
        // The other half of the pair. Someone who has to be off the site now goes here, and the
        // stranded-order cost is accepted deliberately.
        userService.blockUser(7L);

        assertEquals(4, customer.getTokenVersion());
        assertEquals("BLOCKED", customer.getStatus());
        assertFalse(customer.isActive());
    }

    @Test
    void reinstatingClearsTheSuspensionCompletely() {
        userService.suspendUser(7L, 1L, "Under review");
        userService.revokeUser(7L);

        assertEquals("ACTIVE", customer.getStatus());
        assertTrue(customer.isActive());
        assertNull(customer.getSuspendedBy());
        assertNull(customer.getSuspendedAt());
        // A stale reason left behind would keep being shown to somebody in good standing.
        assertNull(customer.getSuspensionReason());
    }

    @Test
    void reinstatingLeavesTheTokenVersionAlone() {
        userService.suspendUser(7L, 1L, "Under review");
        int afterSuspend = customer.getTokenVersion();
        userService.revokeUser(7L);

        assertEquals(afterSuspend, customer.getTokenVersion());
    }

    @Test
    void unblockingClearsTheReasonAsWell() {
        userService.blockUser(7L);
        customer.setSuspensionReason("spam");
        userService.unblockUser(7L);

        assertEquals("ACTIVE", customer.getStatus());
        assertNull(customer.getSuspensionReason());
    }

    @Test
    void aSuspensionWithoutAReasonIsStillAllowed() {
        // The two-argument form is still used by existing callers and must not break.
        assertDoesNotThrow(() -> userService.suspendUser(7L, 1L));
        assertEquals("SUSPENDED", customer.getStatus());
    }
}
