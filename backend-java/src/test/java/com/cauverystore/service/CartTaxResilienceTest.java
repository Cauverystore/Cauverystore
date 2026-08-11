package com.cauverystore.service;

import com.cauverystore.config.JwtUtil;
import com.cauverystore.entities.Cart;
import com.cauverystore.entities.CartItem;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.User;
import com.cauverystore.repository.CartItemRepository;
import com.cauverystore.repository.CartRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A shopper must be able to see their own basket.
 *
 * The resolver refuses to price goods it cannot tax, which is right, and the cart called it
 * without catching that. One unclassified product then threw out of the cart endpoint, so the
 * basket would not load at all - and since the page reloads the cart after every add, the
 * symptom was "nothing can be added to the cart". A catalogue problem became a total shopping
 * outage.
 *
 * The refusal belongs at checkout and on the invoice, where it already is. These tests hold the
 * line at the right place.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartTaxResilienceTest {

    @Mock private CartRepository cartRepo;
    @Mock private CartItemRepository cartItemRepo;
    @Mock private ProductRepository productRepo;
    @Mock private JwtUtil jwtUtil;
    @Mock private UserRepository userRepo;
    @Mock private ProductService productService;
    @Mock private GstRateResolver gstRateResolver;

    private CartServiceImpl service;
    private User user;
    private Cart cart;

    @BeforeEach
    void setUp() {
        service = new CartServiceImpl(cartRepo, cartItemRepo, productRepo, jwtUtil, userRepo,
                productService, gstRateResolver);
        user = new User();
        user.setId(11L);
        cart = new Cart();
        cart.setUser(user);
        cart.setItems(new ArrayList<>());
        when(cartRepo.findByUser(user)).thenReturn(Optional.of(cart));
    }

    private Product add(long id, String name, double price, int qty) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(price);
        p.setStock(10);
        CartItem item = new CartItem();
        item.setProduct(p);
        item.setQuantity(qty);
        item.setSavedForLater(false);
        cart.getItems().add(item);
        when(productService.getDiscountedPriceDouble(id)).thenReturn(price);
        return p;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> untaxable(Map<String, Object> details) {
        return (List<Map<String, Object>>) details.get("untaxableItems");
    }

    @Test
    void shouldStillLoadTheCartWhenAnItemCannotBeTaxed() {
        // The regression that took the shop down: an unclassified product threw out of here, so
        // the basket would not load and adding anything appeared to fail.
        add(1L, "Unclassified test product", 1.0, 1);
        when(gstRateResolver.resolve(any(), anyBoolean(), any(), anyDouble()))
                .thenThrow(new GstRateResolver.GstRateUnresolvedException("no HSN code"));

        Map<String, Object> details = assertDoesNotThrow(() -> service.getCartWithDetails(user));

        assertNotNull(details.get("items"));
        assertEquals(1, ((List<?>) details.get("items")).size(), "the item must still be visible");
        assertEquals(0.0, details.get("tax"));
    }

    @Test
    void shouldNameWhatItCouldNotTaxRatherThanHideIt() {
        // The total excludes these and checkout will stop the order, so the page has to be able
        // to say which item is the problem.
        add(1L, "Unclassified test product", 1.0, 1);
        when(gstRateResolver.resolve(any(), anyBoolean(), any(), anyDouble()))
                .thenThrow(new GstRateResolver.GstRateUnresolvedException("it has no HSN code."));

        Map<String, Object> details = service.getCartWithDetails(user);

        assertEquals(Boolean.FALSE, details.get("taxComplete"));
        assertEquals(1, untaxable(details).size());
        assertEquals("Unclassified test product", untaxable(details).get(0).get("productName"));
        assertTrue(String.valueOf(untaxable(details).get(0).get("reason")).contains("HSN"));
    }

    @Test
    void shouldStillTaxEveryItemItCan() {
        // One bad product must not cost the tax on the good ones, or the total swings wildly on
        // an unrelated catalogue problem.
        Product ok = add(1L, "Cotton Lungi", 450.0, 2);
        Product bad = add(2L, "Unclassified", 100.0, 1);
        when(gstRateResolver.resolve(eq(ok), anyBoolean(), any(), anyDouble()))
                .thenReturn(new GstRateResolver.Resolved(5.0, 0.0, false, "52095110", true));
        when(gstRateResolver.resolve(eq(bad), anyBoolean(), any(), anyDouble()))
                .thenThrow(new GstRateResolver.GstRateUnresolvedException("no rate"));

        Map<String, Object> details = service.getCartWithDetails(user);

        assertEquals(45.0, details.get("tax"), "2 x 450 at 5%");
        assertEquals(1, untaxable(details).size());
    }

    @Test
    void shouldReportACleanCartAsComplete() {
        Product ok = add(1L, "Cotton Lungi", 450.0, 1);
        when(gstRateResolver.resolve(eq(ok), anyBoolean(), any(), anyDouble()))
                .thenReturn(new GstRateResolver.Resolved(5.0, 0.0, false, "52095110", true));

        Map<String, Object> details = service.getCartWithDetails(user);

        assertEquals(Boolean.TRUE, details.get("taxComplete"));
        assertTrue(untaxable(details).isEmpty());
        assertEquals(22.5, details.get("tax"));
    }

    @Test
    void shouldIgnoreSavedForLaterItems() {
        // Saved items are not being bought, so they must not contribute tax nor block anything.
        Product saved = add(1L, "Saved for later", 500.0, 1);
        cart.getItems().get(0).setSavedForLater(true);

        Map<String, Object> details = service.getCartWithDetails(user);

        assertEquals(0.0, details.get("tax"));
        assertTrue(untaxable(details).isEmpty());
        verify(gstRateResolver, never()).resolve(any(), anyBoolean(), any(), anyDouble());
    }
}
