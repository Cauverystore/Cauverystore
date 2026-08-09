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
 * The failure worth pinning is quiet. Suspension used to clear the refresh token and set a
 * status, which blocks the next sign-in and nothing else: whoever was already signed in kept a
 * perfectly valid access token and carried on browsing, filling a basket and placing orders
 * until it expired on its own. Nothing in any log said so, and the admin screen showed the
 * account as suspended the whole time.
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
    void suspendingEndsTheSessionTheyAreAlreadyIn() {
        // The token version is what JwtFilter compares. Leaving it untouched is what let a
        // suspended customer keep shopping.
        userService.suspendUser(7L, 1L, "Chargeback fraud");

        assertEquals(4, customer.getTokenVersion(), "tokens already issued still pass");
        assertNull(customer.getRefreshToken());
        assertEquals("SUSPENDED", customer.getStatus());
        assertFalse(customer.isActive());
    }

    @Test
    void theReasonIsRecordedSoThePersonCanBeToldWhy() {
        userService.suspendUser(7L, 1L, "Repeated fraudulent returns");

        assertEquals("Repeated fraudulent returns", customer.getSuspensionReason());
        assertEquals(1L, customer.getSuspendedBy());
        assertNotNull(customer.getSuspendedAt());
    }

    @Test
    void blockingEndsTheSessionToo() {
        // A second route to the same place. It has to behave the same way, or which button an
        // admin happens to press decides whether the block actually works.
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
    void reinstatingDoesNotHandBackTheOldSession() {
        // The bump is not undone. Whoever it was must sign in again, which is the point - the
        // old token was issued to an account that was then stopped.
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
        assertEquals(4, customer.getTokenVersion());
    }
}
