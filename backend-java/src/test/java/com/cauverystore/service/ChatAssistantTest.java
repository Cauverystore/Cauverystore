package com.cauverystore.service;

import com.anthropic.models.messages.Tool;
import com.cauverystore.dto.ChatResponse;
import com.cauverystore.entities.Product;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The shop chat sends every message to the AI assistant, which uses tools for the shopper's
 * cart, wishlist, orders and offers. The keyword chat answers only when the AI is off or fails.
 */
class ChatAssistantTest {

    private final ProductService products = mock(ProductService.class);
    private final CartService cart = mock(CartService.class);
    private final WishlistService wishlist = mock(WishlistService.class);
    private final OrderService orders = mock(OrderService.class);
    private final CouponService coupons = mock(CouponService.class);
    private final AuthorizationService auth = mock(AuthorizationService.class);
    private final AiShoppingAssistant ai = mock(AiShoppingAssistant.class);
    private final RateLimiterService limiter = mock(RateLimiterService.class);

    private final ChatService chat = new ChatService(products, mock(FaqService.class), cart, wishlist,
            orders, coupons, auth, ai, limiter);

    /** Makes the mocked assistant run one tool, then reply with whatever the tool returned. */
    private AtomicReference<String> assistantRuns(String tool, Map<String, Object> input) {
        AtomicReference<String> toolResult = new AtomicReference<>();
        when(ai.isEnabled()).thenReturn(true);
        when(limiter.allow(anyString(), anyInt(), anyLong())).thenReturn(true);
        when(ai.answer(anyString(), any(), any(), anyString())).thenAnswer(inv -> {
            AiShoppingAssistant.Shop shop = inv.getArgument(2);
            toolResult.set(shop.run(tool, input));
            return new AiShoppingAssistant.Reply("Done.", List.of());
        });
        return toolResult;
    }

    @Test
    void cartQuestionsGoToTheAssistantToo() {
        assistantRuns("view_cart", Map.of());
        when(cart.getCartWithDetails("Bearer t")).thenReturn(new HashMap<>(Map.of("items", List.of())));

        ChatResponse r = chat.respond("Bearer t", "show my cart", List.of(), "1.2.3.4");

        assertEquals("ai", r.getIntent());
        verify(ai).answer(eq("show my cart"), any(), any(), anyString());
    }

    @Test
    void signedOutShopperGetsLoginButtons() {
        AtomicReference<String> result = assistantRuns("view_orders", Map.of());

        ChatResponse r = chat.respond(null, "where is my order", List.of(), "1.2.3.4");

        assertTrue(result.get().contains("not signed in"));
        assertTrue(r.getActions().stream().anyMatch(a -> "LOGIN".equals(a.get("type"))));
        verifyNoInteractions(orders);
    }

    @Test
    void addToCartActsForTheShopperAndShowsCartButtons() {
        Product p = new Product();
        p.setId(7L);
        p.setName("Cotton Towel");
        p.setActive(true);
        when(products.getProductById(7L)).thenReturn(p);
        when(cart.getCartWithDetails("Bearer t")).thenReturn(new HashMap<>(Map.of("items", List.of())));
        AtomicReference<String> result = assistantRuns("add_to_cart", Map.of("product_id", 7, "quantity", 2));

        ChatResponse r = chat.respond("Bearer t", "add two towels to my cart", List.of(), "1.2.3.4");

        verify(cart).addItem("Bearer t", 7L, 2);
        assertTrue(result.get().startsWith("Added 2 x Cotton Towel"));
        assertTrue(r.getActions().stream().anyMatch(a -> "VIEW_CART".equals(a.get("type"))));
    }

    @Test
    void keywordChatAnswersWhenTheAssistantIsOff() {
        when(ai.isEnabled()).thenReturn(false);

        ChatResponse r = chat.respond(null, "show my cart", List.of(), "1.2.3.4");

        assertEquals("auth_required", r.getIntent());
        verify(ai, never()).answer(anyString(), any(), any(), anyString());
    }

    @Test
    void assistantHasATool_forEveryShopAction() {
        List<String> names = AiShoppingAssistant.tools().stream().map(Tool::name).toList();
        assertTrue(names.contains("search_products"));
        assertTrue(names.containsAll(AiShoppingAssistant.SHOP_TOOLS));
    }
}
