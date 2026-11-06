package com.cauverystore.service;

import com.cauverystore.dto.ChatResponse;
import com.cauverystore.entities.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The store's own chat assistant, with no outside AI: typos, "the second one", cart and
 * wishlist commands, order numbers, and price searches that keep the product words.
 */
class SmartChatTest {

    private final ProductService products = mock(ProductService.class);
    private final CartService cart = mock(CartService.class);
    private final WishlistService wishlist = mock(WishlistService.class);
    private final OrderService orders = mock(OrderService.class);
    private final AiShoppingAssistant ai = mock(AiShoppingAssistant.class);

    private final ChatService chat = new ChatService(products, mock(FaqService.class), cart, wishlist,
            orders, mock(CouponService.class), mock(AuthorizationService.class), ai, mock(RateLimiterService.class));

    private final Product towel = product(1L, "Cotton Bath Towel", 299.0);
    private final Product saree = product(2L, "Silk Saree", 2499.0);
    private final Product cheapSaree = product(3L, "Cotton Saree", 799.0);
    private final Product rice = product(4L, "Ponni Rice 5kg", 450.0);

    private static Product product(Long id, String name, double price) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(price);
        p.setStock(20);
        p.setActive(true);
        return p;
    }

    @BeforeEach
    void catalogue() {
        when(ai.isEnabled()).thenReturn(false);
        List<Product> all = List.of(towel, saree, cheapSaree, rice);
        when(products.getActiveProducts()).thenReturn(all);
        for (Product p : all) when(products.getProductById(p.getId())).thenReturn(p);
        when(cart.getCartWithDetails(anyString())).thenReturn(new HashMap<>(Map.of("items", List.of())));
    }

    private ChatResponse say(String text, Long... shown) {
        return chat.respond("Bearer t", text, List.of(), "1.1.1.1", List.of(shown));
    }

    @Test
    void typosStillFindProducts() {
        ChatResponse r = say("towl");
        assertTrue(r.getReply().contains("Cotton Bath Towel"), r.getReply());
    }

    @Test
    void pluralsFindSingularNames() {
        assertTrue(say("sarees").getReply().contains("Silk Saree"));
    }

    @Test
    void addTheSecondOneUsesTheLastList() {
        ChatResponse r = say("add the second one to my cart", 2L, 3L);
        verify(cart).addItem("Bearer t", 3L, 1);
        assertEquals(Boolean.TRUE, r.getData().get("cartChanged"));
    }

    @Test
    void addByNameWithQuantity() {
        say("add 2 cotton bath towels to cart");
        verify(cart).addItem("Bearer t", 1L, 2);
    }

    @Test
    void unclearProductAsksWhichOne() {
        ChatResponse r = say("add saree to cart");
        assertEquals("choose_product", r.getIntent());
        verify(cart, never()).addItem(anyString(), anyLong(), anyInt());
        // The choices are remembered, so "the second one" works next.
        assertEquals(2, ((List<?>) r.getData().get("products")).size());
    }

    @Test
    void addTheCheapestOne() {
        say("add the cheapest one", 2L, 3L);
        verify(cart).addItem("Bearer t", 3L, 1);
    }

    @Test
    void saveToWishlist() {
        say("save the first one to my wishlist", 1L, 4L);
        verify(wishlist).addToWishlist("Bearer t", 1L);
    }

    @Test
    void signedOutShopperIsAskedToLogIn() {
        ChatResponse r = chat.respond(null, "add the first one", List.of(), "1.1.1.1", List.of(1L));
        assertEquals("auth_required", r.getIntent());
    }

    @Test
    void removeByNameFromCart() {
        Map<String, Object> line = new HashMap<>(Map.of("id", 55L, "quantity", 1,
                "product", Map.of("id", 1L, "name", "Cotton Bath Towel")));
        when(cart.getCartWithDetails("Bearer t")).thenReturn(new HashMap<>(Map.of("items", List.of(line))));
        say("remove the towel");
        verify(cart).removeItem("Bearer t", 55L);
    }

    @Test
    void orderNumberIsTracked() {
        when(orders.getOrderDetail(12L, "Bearer t")).thenReturn(new HashMap<>(Map.of("status", "SHIPPED", "totalAmount", 450.0)));
        when(orders.getOrderTracking(12L, "Bearer t")).thenReturn(new HashMap<>(Map.of("courier", "Delhivery", "trackingNumber", "AWB1")));
        ChatResponse r = say("where is order #12");
        assertTrue(r.getReply().startsWith("Order #12 is shipped."), r.getReply());
        assertTrue(r.getReply().contains("Delhivery"));
    }

    @Test
    void priceSearchKeepsTheProductWords() {
        ChatResponse r = say("sarees under 1000");
        assertTrue(r.getReply().contains("Cotton Saree"), r.getReply());
        assertFalse(r.getReply().contains("Towel"), r.getReply());
        assertFalse(r.getReply().contains("Silk Saree"), r.getReply());
    }

    @Test
    void orderingSomethingIsASearchNotOrderHistory() {
        assertTrue(say("i want to order rice").getReply().contains("Ponni Rice"));
    }

    @Test
    void niceProductIsNotThanks() {
        assertNotEquals("thanks", say("nice cotton saree for a wedding gift").getIntent());
    }

    @Test
    void detailsOfAShownProduct() {
        ChatResponse r = say("is it in stock?", 4L);
        assertEquals("product_details", r.getIntent());
    }

    @Test
    void chatTextReadsQuantitiesAndOrdinals() {
        assertEquals(3, ChatText.quantity("add three to cart"));
        assertEquals(1, ChatText.quantity("add 5kg rice"));
        assertEquals(1, ChatText.ordinal("the second one", 4));
        assertEquals(3, ChatText.ordinal("the last one", 4));
        assertEquals(7L, ChatText.orderNumber("track order 7"));
        assertTrue(ChatText.closeEnough("sareee", "saree"));
    }
}
