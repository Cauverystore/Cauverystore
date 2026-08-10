package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.ReturnRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * When a suspended account has finished.
 *
 * A suspension stops new business but cannot stop what is already running - somebody has paid
 * and is waiting for goods, or has received them and may still send them back. Getting the end
 * of that wrong is not visible from the outside: closing an account a day early quietly takes
 * away a return somebody was entitled to.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountWindDownTest {

    @Mock private OrderRepository orderRepo;
    @Mock private ReturnRequestRepository returnRepo;

    private AccountRestrictionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AccountRestrictionService(orderRepo, returnRepo);
        user = new User();
        user.setId(7L);
        user.setStatus("ACTIVE");
        when(returnRepo.findByUserId(anyLong())).thenReturn(List.of());
    }

    private Order order(String status, LocalDateTime deliveredAt, Integer returnWindowDays) {
        Order o = new Order();
        o.setStatus(status);
        o.setDeliveredAt(deliveredAt);
        Product p = new Product();
        p.setReturnWindow(returnWindowDays);
        OrderItem item = new OrderItem();
        item.setProduct(p);
        o.setItems(new ArrayList<>(List.of(item)));
        return o;
    }

    private void ordersAre(Order... orders) {
        when(orderRepo.findByUser(user)).thenReturn(new ArrayList<>(List.of(orders)));
    }

    @Test
    void anOrderStillOnItsWayIsUnfinishedBusiness() {
        ordersAre(order("SHIPPED", null, 7));

        Map<String, Object> w = service.windDown(user);

        assertEquals(1, w.get("ordersAwaitingFulfilment"));
        assertEquals(Boolean.FALSE, w.get("complete"));
    }

    @Test
    void deliveryIsNotTheEndOfIt() {
        // The mistake this guards against. Delivered looks final and is not - the goods can come
        // back for as long as the return window runs.
        ordersAre(order("DELIVERED", LocalDateTime.now().minusDays(2), 7));

        Map<String, Object> w = service.windDown(user);

        assertEquals(1, w.get("ordersWithinReturnWindow"));
        assertEquals(Boolean.FALSE, w.get("complete"));
        assertNotNull(w.get("lastObligationEnds"));
    }

    @Test
    void onceTheReturnWindowHasRunOutTheOrderIsDone() {
        ordersAre(order("DELIVERED", LocalDateTime.now().minusDays(20), 7));

        Map<String, Object> w = service.windDown(user);

        assertEquals(0, w.get("ordersWithinReturnWindow"));
        assertEquals(Boolean.TRUE, w.get("complete"));
    }

    @Test
    void theLongestWindowInTheOrderIsTheOneThatCounts() {
        // A basket where one item is returnable for thirty days keeps the account open for
        // thirty days, whatever the rest of it said.
        Order o = order("DELIVERED", LocalDateTime.now().minusDays(10), 7);
        Product generous = new Product();
        generous.setReturnWindow(30);
        OrderItem second = new OrderItem();
        second.setProduct(generous);
        o.getItems().add(second);
        ordersAre(o);

        Map<String, Object> w = service.windDown(user);

        assertEquals(1, w.get("ordersWithinReturnWindow"));
        assertEquals(Boolean.FALSE, w.get("complete"));
    }

    @Test
    void cancelledAndRefundedOrdersAreFinishedAtOnce() {
        ordersAre(order("CANCELLED", null, 7), order("REFUNDED", null, 7));

        Map<String, Object> w = service.windDown(user);

        assertEquals(0, w.get("ordersAwaitingFulfilment"));
        assertEquals(Boolean.TRUE, w.get("complete"));
    }

    @Test
    void anOpenReturnKeepsTheAccountOpenWhateverTheOrdersSay() {
        ordersAre(order("DELIVERED", LocalDateTime.now().minusDays(60), 7));
        ReturnRequest open = new ReturnRequest();
        open.setStatus("APPROVED");
        when(returnRepo.findByUserId(7L)).thenReturn(List.of(open));

        Map<String, Object> w = service.windDown(user);

        assertEquals(1L, w.get("openReturns"));
        assertEquals(Boolean.FALSE, w.get("complete"));
        // Settlement is assumed to take ten days, so there is a date to show. It is marked as an
        // estimate, because inspection and refund can overrun and nobody should plan around it
        // as though it were fixed.
        assertNotNull(w.get("lastObligationEnds"));
        assertEquals(Boolean.TRUE, w.get("lastObligationEstimated"));
    }

    @Test
    void theTenDaysRunFromWhenTheReturnWasRaisedNotFromToday() {
        // Counting from now would push the date back every time anybody looked at the screen,
        // and a wind-down that never gets closer is worse than no date at all.
        ordersAre();
        ReturnRequest raisedAWeekAgo = new ReturnRequest();
        raisedAWeekAgo.setStatus("REQUESTED");
        raisedAWeekAgo.setCreatedAt(LocalDateTime.now().minusDays(7));
        when(returnRepo.findByUserId(7L)).thenReturn(List.of(raisedAWeekAgo));

        LocalDateTime ends = (LocalDateTime) service.windDown(user).get("lastObligationEnds");

        assertTrue(ends.isBefore(LocalDateTime.now().plusDays(4)),
                "expected roughly three days left, not ten");
        assertTrue(ends.isAfter(LocalDateTime.now().plusDays(2)));
    }

    @Test
    void aDateThatIsSimplyAReturnWindowRunningOutIsNotAnEstimate() {
        ordersAre(order("DELIVERED", LocalDateTime.now().minusDays(2), 7));

        Map<String, Object> w = service.windDown(user);

        assertNotNull(w.get("lastObligationEnds"));
        assertEquals(Boolean.FALSE, w.get("lastObligationEstimated"));
    }

    @Test
    void aSettledReturnDoesNotHoldTheAccountOpen() {
        ordersAre(order("DELIVERED", LocalDateTime.now().minusDays(60), 7));
        ReturnRequest done = new ReturnRequest();
        done.setStatus("REFUNDED");
        when(returnRepo.findByUserId(7L)).thenReturn(List.of(done));

        assertEquals(Boolean.TRUE, service.windDown(user).get("complete"));
    }

    @Test
    void aCompletedReturnStillHoldsTheAccountOpenUntilTheMoneyIsBack() {
        // Completed means the goods are back and accepted - which is where the credit note is
        // issued - but not that the customer has been paid. Closing the account here would drop
        // somebody who is still owed a refund.
        ordersAre(order("DELIVERED", LocalDateTime.now().minusDays(60), 7));
        ReturnRequest goodsBackNotYetPaid = new ReturnRequest();
        goodsBackNotYetPaid.setStatus("COMPLETED");
        when(returnRepo.findByUserId(7L)).thenReturn(List.of(goodsBackNotYetPaid));

        assertEquals(Boolean.FALSE, service.windDown(user).get("complete"));
    }

    @Test
    void aDeliveredOrderWithNoTimestampIsTreatedAsStillOpen() {
        // Rows predating the deliveredAt column. Assuming the window had expired would silently
        // deny somebody a return, so the doubt is resolved the other way.
        ordersAre(order("DELIVERED", null, 7));

        assertEquals(Boolean.FALSE, service.windDown(user).get("complete"));
    }

    @Test
    void anAccountWithNothingRunningIsFinishedImmediately() {
        ordersAre();

        assertEquals(Boolean.TRUE, service.windDown(user).get("complete"));
    }

    @Test
    void aSuspendedAccountIsRefusedANewOrderButToldWhatItCanStillDo() {
        user.setStatus("SUSPENDED");
        user.setSuspensionReason("Chargeback fraud");

        AccountRestrictionService.AccountRestrictedException e = assertThrows(
                AccountRestrictionService.AccountRestrictedException.class,
                () -> service.requireMayPlaceOrder(user));

        assertTrue(e.getMessage().contains("Chargeback fraud"));
        assertTrue(e.getMessage().contains("track them"),
                "a refusal that does not say what is still possible reads as a dead end");
    }

    @Test
    void anOrdinaryAccountIsNotRefused() {
        assertDoesNotThrow(() -> service.requireMayPlaceOrder(user));
    }

    @Test
    void aBlockedAccountIsRefusedToo() {
        user.setStatus("BLOCKED");

        assertThrows(AccountRestrictionService.AccountRestrictedException.class,
                () -> service.requireMayPlaceOrder(user));
    }
}
