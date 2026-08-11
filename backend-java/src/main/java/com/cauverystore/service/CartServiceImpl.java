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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    private static final Logger log = LoggerFactory.getLogger(CartServiceImpl.class);

    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final ProductRepository productRepo;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepo;
    private final ProductService productService;

    private final GstRateResolver gstRateResolver;

    public CartServiceImpl(CartRepository cartRepo, CartItemRepository cartItemRepo,
                           ProductRepository productRepo, JwtUtil jwtUtil, UserRepository userRepo,
                           ProductService productService, GstRateResolver gstRateResolver) {
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.productRepo = productRepo;
        this.jwtUtil = jwtUtil;
        this.userRepo = userRepo;
        this.productService = productService;
        this.gstRateResolver = gstRateResolver;
    }

    // Mirrors OrderService.placeOrder's line-pricing exactly - whatever the cart shows the
    // customer here must be what checkout actually charges, or the two silently diverge.
    private double effectiveUnitPrice(CartItem item) {
        if (item.getVariant() != null && item.getVariant().getPrice() != null) {
            return item.getVariant().getPrice();
        }
        return productService.getDiscountedPriceDouble(item.getProduct().getId());
    }

    private User extractUserFromHeader(String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String email = jwtUtil.getEmailFromToken(token);
        User user = userRepo.findByEmail(email);
        if (user == null) throw new RuntimeException("User not found");
        return user;
    }

    @Override
    public Cart getCart(User user) {
        return cartRepo.findByUser(user).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUser(user);
            cart.setItems(new ArrayList<>());
            cart.setTotalItems(0);
            cart.setTotalPrice(0.0);
            return cartRepo.save(cart);
        });
    }

    @Override
    public Cart addItem(User user, Product product, int quantity) {
        if (product == null) throw new RuntimeException("Product not found");
        if (quantity <= 0) throw new RuntimeException("Quantity must be positive");
        if ((product.getStock() == null ? 0 : product.getStock()) < quantity) throw new RuntimeException("Not enough stock available");

        Cart cart = getCart(user);
        if (!cart.getUser().getId().equals(user.getId()))
            throw new RuntimeException("Unauthorized cart access");

        CartItem existingItem = cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(product.getId()) && !i.isSavedForLater())
                .findFirst().orElse(null);

        if (existingItem != null) {
            int newQty = existingItem.getQuantity() + quantity;
            if ((product.getStock() == null ? 0 : product.getStock()) < newQty)
                throw new RuntimeException("Not enough stock for updated quantity");
            existingItem.setQuantity(newQty);
            cartItemRepo.save(existingItem);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setSavedForLater(false);
            cart.getItems().add(item);
            cartItemRepo.save(item);
        }
        updateCartTotals(cart);
        return cartRepo.save(cart);
    }

    @Override
    public Cart removeItem(User user, Long cartItemId) {
        Cart cart = getCart(user);
        CartItem item = cartItemRepo.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));
        if (!item.getCart().getId().equals(cart.getId()))
            throw new RuntimeException("Item does not belong to this user's cart");
        cart.getItems().remove(item);
        cartItemRepo.delete(item);
        updateCartTotals(cart);
        return cartRepo.save(cart);
    }

    @Override
    public Cart clearCart(User user) {
        Cart cart = getCart(user);
        List<CartItem> items = new ArrayList<>(cart.getItems());
        cart.getItems().clear();
        cart.setTotalItems(0);
        cart.setTotalPrice(0.0);
        cartRepo.save(cart);
        cartItemRepo.deleteAll(items);
        return cart;
    }

    @Transactional
    @Override
    public CartItem toggleSaveForLater(User user, Long itemId) {
        return setSavedForLater(user, itemId, null);
    }

    // Directional set, not a blind flip - saveForLater/moveToCart each need a guaranteed
    // outcome regardless of current state (a retry or double-click on "Move to Cart" must not
    // toggle the item back to saved). Pass null to flip whatever the current value is.
    @Transactional
    public CartItem setSavedForLater(User user, Long itemId, Boolean savedForLater) {
        Cart cart = getCart(user);
        CartItem item = cartItemRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));
        if (!item.getCart().getId().equals(cart.getId()))
            throw new RuntimeException("Item does not belong to this user's cart");
        item.setSavedForLater(savedForLater != null ? savedForLater : !item.isSavedForLater());
        CartItem saved = cartItemRepo.save(item);
        updateCartTotals(cart);
        cartRepo.save(cart);
        return saved;
    }

    @Transactional
    @Override
    public Cart updateItemQuantity(User user, Long itemId, int quantity) {
        if (quantity < 1) throw new RuntimeException("Quantity must be at least 1");
        Cart cart = getCart(user);
        CartItem item = cartItemRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));
        if (!item.getCart().getId().equals(cart.getId()))
            throw new RuntimeException("Item does not belong to this user's cart");
        Integer availableStock = item.getProduct().getStock();
        if ((availableStock == null ? 0 : availableStock) < quantity)
            throw new RuntimeException("Not enough stock available");
        item.setQuantity(quantity);
        cartItemRepo.save(item);
        updateCartTotals(cart);
        return cartRepo.save(cart);
    }

    @Override
    public Map<String, Object> getCartWithDetails(User user) {
        Cart cart = getCart(user);
        List<Map<String, Object>> activeItems = new ArrayList<>();
        List<Map<String, Object>> savedItems = new ArrayList<>();

        for (CartItem item : cart.getItems()) {
            Map<String, Object> detail = buildItemDetail(item);
            if (item.isSavedForLater()) {
                savedItems.add(detail);
            } else {
                activeItems.add(detail);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", activeItems);
        result.put("savedForLater", savedItems);
        result.put("totalItems", activeItems.stream().mapToInt(i -> (int) i.get("quantity")).sum());
        result.put("totalPrice", activeItems.stream().mapToDouble(i -> (double) i.get("subtotal")).sum());
        result.put("deliveryCharge", activeItems.isEmpty() || (double) result.get("totalPrice") >= 500 ? 0.0 : 40.0);
        // Tax shown in the cart must be the tax checkout will actually charge, so it is
        // resolved per item from its HSN via the same rate master OrderService uses rather
        // than applying a flat percentage to the basket.
        double cartTax = 0.0;
        List<Map<String, Object>> untaxable = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            if (item.isSavedForLater()) continue;
            double unitPrice = effectiveUnitPrice(item);
            double lineValue = unitPrice * item.getQuantity();
            try {
                GstRateResolver.Resolved resolved = gstRateResolver
                        .resolve(item.getProduct(), false, LocalDate.now(), unitPrice);
                cartTax += Math.round(lineValue * resolved.getTotalRate() / 100.0 * 100.0) / 100.0;
                // Cess is charged to the customer like GST, so it belongs in the figure this
                // page shows as tax, or the cart and the checkout bill would disagree.
                cartTax += resolved.cessOn(lineValue);
            } catch (GstRateResolver.GstRateUnresolvedException e) {
                // A cart is a display, not a tax point. Letting this escape made one
                // unclassified product break the whole cart endpoint, so the basket would not
                // load at all and adding anything looked broken - which is a far worse outcome
                // than an incomplete tax figure on a page nobody is charged from.
                //
                // The refusal still stands where it counts: CheckoutBillService will not let
                // the order be placed, and the invoice cannot be raised. This only stops a
                // shopper being locked out of their own basket by a catalogue problem.
                Map<String, Object> problem = new HashMap<>();
                problem.put("productId", item.getProduct() != null ? item.getProduct().getId() : null);
                problem.put("productName", item.getProduct() != null ? item.getProduct().getName() : null);
                problem.put("reason", e.getMessage());
                untaxable.add(problem);
                log.warn("Cart for user {} contains '{}', which has no determinable GST rate: {}",
                        user.getId(),
                        item.getProduct() != null ? item.getProduct().getName() : "?",
                        e.getMessage());
            }
        }
        result.put("tax", Math.round(cartTax * 100.0) / 100.0);
        // Named rather than hidden: the total below excludes these, and the customer will be
        // stopped at checkout, so the page has to be able to say why.
        result.put("untaxableItems", untaxable);
        result.put("taxComplete", untaxable.isEmpty());
        result.put("finalAmount", Math.round(((double) result.get("totalPrice") + (double) result.get("deliveryCharge") + (double) result.get("tax")) * 100.0) / 100.0);
        result.put("freeDeliveryEligible", (double) result.get("totalPrice") >= 500);
        result.put("deliveryDate", LocalDate.now().plusDays(4).toString());
        result.put("itemCount", activeItems.size());
        return result;
    }

    private Map<String, Object> buildItemDetail(CartItem item) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", item.getId());
        detail.put("quantity", item.getQuantity());
        detail.put("savedForLater", item.isSavedForLater());

        Product p = item.getProduct();
        Map<String, Object> product = new HashMap<>();
        product.put("id", p.getId());
        product.put("name", p.getName());
        product.put("price", p.getPrice());
        product.put("brand", p.getBrand());
        product.put("color", p.getColor());
        product.put("size", p.getSize());
        product.put("material", p.getMaterial());
        product.put("weight", p.getWeight());
        product.put("stock", p.getStock());
        product.put("description", p.getDescription());
        product.put("image", (p.getImages() != null && !p.getImages().isEmpty())
                ? p.getImages().get(0).getUrl() : null);

        int stock = p.getStock() == null ? 0 : p.getStock();
        String stockStatus;
        if (stock <= 0) stockStatus = "Out of Stock";
        else if (stock <= 5) stockStatus = "Only " + stock + " left";
        else stockStatus = "In Stock";

        product.put("stockStatus", stockStatus);
        product.put("inStock", stock > 0);
        detail.put("product", product);

        double subtotal = Math.round(effectiveUnitPrice(item) * item.getQuantity() * 100.0) / 100.0;
        detail.put("subtotal", subtotal);

        if (item.getVariant() != null) {
            Map<String, Object> variant = new HashMap<>();
            variant.put("id", item.getVariant().getId());
            variant.put("type", item.getVariant().getVariantType());
            variant.put("value", item.getVariant().getVariantValue());
            variant.put("price", item.getVariant().getPrice());
            detail.put("variant", variant);
        }

        detail.put("deliveryEstimate", "Get it by " + LocalDate.now().plusDays(3 + (int)(Math.random() * 3)).toString());
        detail.put("seller", "Cauvery Retail");
        return detail;
    }

    @Override
    public List<Map<String, Object>> getFrequentlyBoughtTogether(User user) {
        Cart cart = getCart(user);
        if (cart.getItems().isEmpty()) return new ArrayList<>();
        Set<Long> existingIds = cart.getItems().stream()
                .map(i -> i.getProduct().getId()).collect(Collectors.toSet());

        List<Product> active = productRepo.findByActiveTrue();
        return active.stream()
            .filter(p -> !existingIds.contains(p.getId()) && p.getStock() != null && p.getStock() > 0)
            .limit(4)
            .map(p -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", p.getId());
                m.put("name", p.getName());
                m.put("price", p.getPrice());
                m.put("image", p.getImages() != null && !p.getImages().isEmpty() ? p.getImages().get(0).getUrl() : null);
                return m;
            }).toList();
    }

    @Override
    public Map<String, Object> getCartWithDetails(String authHeader) {
        return getCartWithDetails(extractUserFromHeader(authHeader));
    }

    @Override
    public CartItem addItem(String authHeader, Long productId, int quantity) {
        User user = extractUserFromHeader(authHeader);
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        addItem(user, product, quantity);
        Cart cart = getCart(user);
        return cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .findFirst().orElse(null);
    }

    @Override
    public void removeItem(String authHeader, Long itemId) {
        User user = extractUserFromHeader(authHeader);
        Cart cart = getCart(user);
        CartItem item = cartItemRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));
        if (!item.getCart().getId().equals(cart.getId()))
            throw new RuntimeException("Item does not belong to this user's cart");
        cart.getItems().remove(item);
        cartItemRepo.delete(item);
        updateCartTotals(cart);
        cartRepo.save(cart);
    }

    @Override
    public void clearCart(String authHeader) {
        clearCart(extractUserFromHeader(authHeader));
    }

    @Override
    public CartItem saveForLater(String authHeader, Long itemId) {
        return setSavedForLater(extractUserFromHeader(authHeader), itemId, true);
    }

    @Override
    public CartItem moveToCart(String authHeader, Long itemId) {
        return setSavedForLater(extractUserFromHeader(authHeader), itemId, false);
    }

    @Override
    public List<Product> getFrequentlyBought(String authHeader) {
        return getFrequentlyBoughtTogether(extractUserFromHeader(authHeader)).stream()
                .map(m -> {
                    Product p = new Product();
                    p.setId((Long) m.get("id"));
                    p.setName((String) m.get("name"));
                    p.setPrice((Double) m.get("price"));
                    return p;
                })
                .toList();
    }

    @Override
    public CartItem updateQuantity(String authHeader, Long itemId, int quantity) {
        User user = extractUserFromHeader(authHeader);
        updateItemQuantity(user, itemId, quantity);
        return cartItemRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));
    }

    private void updateCartTotals(Cart cart) {
        int totalItems = 0;
        double totalPrice = 0.0;
        for (CartItem item : cart.getItems()) {
            if (!item.isSavedForLater()) {
                totalItems += item.getQuantity();
                totalPrice += item.getQuantity() * effectiveUnitPrice(item);
            }
        }
        cart.setTotalItems(totalItems);
        cart.setTotalPrice(Math.round(totalPrice * 100.0) / 100.0);
    }
}
