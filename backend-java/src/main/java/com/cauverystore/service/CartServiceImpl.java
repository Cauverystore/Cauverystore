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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final ProductRepository productRepo;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepo;

    public CartServiceImpl(CartRepository cartRepo, CartItemRepository cartItemRepo,
                           ProductRepository productRepo, JwtUtil jwtUtil, UserRepository userRepo) {
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.productRepo = productRepo;
        this.jwtUtil = jwtUtil;
        this.userRepo = userRepo;
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
        if (product.getStock() < quantity) throw new RuntimeException("Not enough stock available");

        Cart cart = getCart(user);
        if (!cart.getUser().getId().equals(user.getId()))
            throw new RuntimeException("Unauthorized cart access");

        CartItem existingItem = cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(product.getId()) && !i.isSavedForLater())
                .findFirst().orElse(null);

        if (existingItem != null) {
            int newQty = existingItem.getQuantity() + quantity;
            if (product.getStock() < newQty)
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
        Cart cart = getCart(user);
        CartItem item = cartItemRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));
        if (!item.getCart().getId().equals(cart.getId()))
            throw new RuntimeException("Item does not belong to this user's cart");
        item.setSavedForLater(!item.isSavedForLater());
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
        if (item.getProduct().getStock() < quantity)
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
        result.put("tax", Math.round((double) result.get("totalPrice") * 0.12 * 100.0) / 100.0);
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

        String stockStatus;
        if (p.getStock() <= 0) stockStatus = "Out of Stock";
        else if (p.getStock() <= 5) stockStatus = "Only " + p.getStock() + " left";
        else stockStatus = "In Stock";

        product.put("stockStatus", stockStatus);
        product.put("inStock", p.getStock() > 0);
        detail.put("product", product);

        double subtotal = Math.round(p.getPrice() * item.getQuantity() * 100.0) / 100.0;
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
        return toggleSaveForLater(extractUserFromHeader(authHeader), itemId);
    }

    @Override
    public CartItem moveToCart(String authHeader, Long itemId) {
        return toggleSaveForLater(extractUserFromHeader(authHeader), itemId);
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
                totalPrice += item.getQuantity() * item.getProduct().getPrice();
            }
        }
        cart.setTotalItems(totalItems);
        cart.setTotalPrice(Math.round(totalPrice * 100.0) / 100.0);
    }
}
