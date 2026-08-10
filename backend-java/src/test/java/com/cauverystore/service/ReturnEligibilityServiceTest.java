package com.cauverystore.service;

import com.cauverystore.entities.Category;
import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderItem;
import com.cauverystore.entities.Product;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Whether goods may be sent back.
 *
 * The only check used to be that the order had been delivered - so a return could be raised years
 * later, and innerwear was as returnable as a kettle. Both refusals have to happen before the
 * customer packs a parcel, not when it reaches a warehouse that will not accept it.
 */
class ReturnEligibilityServiceTest {

    private final ReturnEligibilityService service = new ReturnEligibilityService();

    private Product product(String name, String categoryName, Integer window, String policy) {
        Product p = new Product();
        p.setName(name);
        p.setReturnWindow(window);
        p.setReturnPolicy(policy);
        if (categoryName != null) {
            Category c = new Category();
            c.setName(categoryName);
            p.setCategory(c);
        }
        return p;
    }

    private Order delivered(LocalDateTime when, Product... products) {
        Order o = new Order();
        o.setStatus("DELIVERED");
        o.setDeliveredAt(when);
        List<OrderItem> items = new ArrayList<>();
        for (Product p : products) {
            OrderItem i = new OrderItem();
            i.setProduct(p);
            items.add(i);
        }
        o.setItems(items);
        return o;
    }

    @Test
    void aRecentDeliveryCanBeReturned() {
        Order o = delivered(LocalDateTime.now().minusDays(2), product("Kettle", "Appliances", 7, "7 Days"));

        Map<String, Object> terms = service.check(o, "SIZE_ISSUE");

        assertEquals(Boolean.TRUE, terms.get("returnable"));
        assertEquals(7, terms.get("returnWindowDays"));
        assertNotNull(terms.get("returnBy"));
    }

    @Test
    void aReturnRaisedAfterTheWindowIsRefusedWithTheNumbers() {
        // Previously accepted without question, however long ago the order arrived.
        Order o = delivered(LocalDateTime.now().minusDays(30), product("Kettle", "Appliances", 7, "7 Days"));

        ReturnEligibilityService.NotReturnableException e = assertThrows(
                ReturnEligibilityService.NotReturnableException.class,
                () -> service.check(o, "SIZE_ISSUE"));

        assertTrue(e.getMessage().contains("closed"), e.getMessage());
        assertTrue(e.getMessage().contains("7 days"), e.getMessage());
    }

    @Test
    void innerwearCannotComeBack() {
        Order o = delivered(LocalDateTime.now().minusDays(1), product("Cotton Briefs", "Innerwear", 7, "7 Days"));

        ReturnEligibilityService.NotReturnableException e = assertThrows(
                ReturnEligibilityService.NotReturnableException.class,
                () -> service.check(o, "SIZE_ISSUE"));

        assertTrue(e.getMessage().contains("Cotton Briefs"), e.getMessage());
        assertTrue(e.getMessage().toLowerCase().contains("hygiene"), e.getMessage());
    }

    @Test
    void oneNonReturnableItemBlocksTheOrder() {
        // A basket cannot be half returnable, and the customer has to be told which item it is.
        Order o = delivered(LocalDateTime.now().minusDays(1),
                product("Kettle", "Appliances", 7, "7 Days"),
                product("Fresh Milk 1L", "Dairy", 7, "7 Days"));

        assertThrows(ReturnEligibilityService.NotReturnableException.class,
                () -> service.check(o, "OTHER"));
    }

    @Test
    void aSellerSayingNoReturnsIsTakenAtTheirWord() {
        Order o = delivered(LocalDateTime.now().minusDays(1),
                product("Engraved Mug", "Gifts", 7, "No Returns"));

        assertThrows(ReturnEligibilityService.NotReturnableException.class,
                () -> service.check(o, "OTHER"));
    }

    @Test
    void theShortestWindowInTheOrderGoverns() {
        // Taking the longest would hold a seller to a promise they never made about their item.
        Order o = delivered(LocalDateTime.now().minusDays(8),
                product("Jacket", "Fashion", 30, "30 Days"),
                product("Kettle", "Appliances", 7, "7 Days"));

        assertThrows(ReturnEligibilityService.NotReturnableException.class,
                () -> service.check(o, "SIZE_ISSUE"));
    }

    @Test
    void damagedGoodsAreCollectedFreeAndAChangeOfMindIsNot() {
        // The line the shipping cost turns on. Charging a customer to send back something that
        // arrived broken is the failure worth guarding.
        Order o = delivered(LocalDateTime.now().minusDays(1), product("Kettle", "Appliances", 7, "7 Days"));

        Map<String, Object> faulty = service.check(o, "DAMAGED");
        Map<String, Object> changedMind = service.check(o, "OTHER");

        assertEquals(Boolean.TRUE, faulty.get("pickupFree"));
        assertEquals("SELLER", faulty.get("shippingBorneBy"));
        assertEquals(Boolean.FALSE, changedMind.get("pickupFree"));
        assertEquals("CUSTOMER", changedMind.get("shippingBorneBy"));
    }

    @Test
    void reasonWordingIsMatchedLooselyEnoughToBeUsable() {
        // Screens send "Wrong Item"; the rule is written as WRONG_ITEM. A customer should not pay
        // postage over a space.
        Order o = delivered(LocalDateTime.now().minusDays(1), product("Kettle", "Appliances", 7, "7 Days"));

        assertEquals(Boolean.TRUE, service.check(o, "Wrong Item").get("pickupFree"));
    }

    @Test
    void anUndeliveredOrderCannotBeReturned() {
        Order o = new Order();
        o.setStatus("SHIPPED");

        assertThrows(ReturnEligibilityService.NotReturnableException.class,
                () -> service.check(o, "DAMAGED"));
    }

    @Test
    void aDeliveredOrderWithNoTimestampIsAllowedThrough() {
        // Rows predating deliveredAt. Refusing on a date we do not hold would deny a return
        // somebody is entitled to, so the doubt goes their way.
        Order o = delivered(null, product("Kettle", "Appliances", 7, "7 Days"));

        assertEquals(Boolean.TRUE, service.check(o, "DAMAGED").get("returnable"));
    }

    @Test
    void aZeroDayWindowMeansTheItemIsNotReturnableAtAll() {
        Order o = delivered(LocalDateTime.now().minusDays(1), product("Sale Item", "Deals", 0, null));

        assertThrows(ReturnEligibilityService.NotReturnableException.class,
                () -> service.check(o, "DAMAGED"));
    }
}
