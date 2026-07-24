package com.cauverystore.service;

import com.cauverystore.config.JwtUtil;
import com.cauverystore.entities.Cart;
import com.cauverystore.entities.CartItem;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.User;
import com.cauverystore.entities.WishlistItem;
import com.cauverystore.exception.ProductNotFoundException;
import com.cauverystore.repository.CartItemRepository;
import com.cauverystore.repository.CartRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.repository.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepo;
    private final ProductRepository productRepo;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepo;
    private final CartService cartService;
    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;

    public WishlistServiceImpl(WishlistRepository wishlistRepo, ProductRepository productRepo,
                               JwtUtil jwtUtil, UserRepository userRepo,
                               CartService cartService, CartRepository cartRepo,
                               CartItemRepository cartItemRepo) {
        this.wishlistRepo = wishlistRepo;
        this.productRepo = productRepo;
        this.jwtUtil = jwtUtil;
        this.userRepo = userRepo;
        this.cartService = cartService;
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
    }

    private User extractUserFromHeader(String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String email = jwtUtil.getEmailFromToken(token);
        User user = userRepo.findByEmail(email);
        if (user == null) throw new RuntimeException("User not found");
        return user;
    }

    @Override
    public List<WishlistItem> getWishlist(User user) {
        return wishlistRepo.findByUser(user);
    }

    @Override
    public WishlistItem addToWishlist(User user, Long productId) {
        List<WishlistItem> existing = wishlistRepo.findByUser(user);
        boolean alreadyExists = existing.stream()
                .anyMatch(w -> w.getProduct().getId().equals(productId));
        if (alreadyExists) {
            throw new RuntimeException("Product already in wishlist");
        }

        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));

        WishlistItem item = new WishlistItem();
        item.setUser(user);
        item.setProduct(product);
        return wishlistRepo.save(item);
    }

    @Override
    public void removeFromWishlist(User user, Long itemId) {
        WishlistItem item = wishlistRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Wishlist item not found"));
        if (!item.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Wishlist item does not belong to this user");
        }
        wishlistRepo.delete(item);
    }

    @Override
    public void clearWishlist(User user) {
        List<WishlistItem> items = wishlistRepo.findByUser(user);
        wishlistRepo.deleteAll(items);
    }

    @Override
    public List<WishlistItem> getWishlist(String authHeader) {
        User user = extractUserFromHeader(authHeader);
        return wishlistRepo.findByUser(user);
    }

    @Override
    public WishlistItem addToWishlist(String authHeader, Long productId) {
        return addToWishlist(extractUserFromHeader(authHeader), productId);
    }

    @Override
    public WishlistItem removeFromWishlist(String authHeader, Long productId) {
        User user = extractUserFromHeader(authHeader);
        List<WishlistItem> items = wishlistRepo.findByUser(user);
        WishlistItem target = items.stream()
                .filter(w -> w.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Product not found in wishlist"));
        wishlistRepo.delete(target);
        return target;
    }

    @Override
    public void clearWishlist(String authHeader) {
        clearWishlist(extractUserFromHeader(authHeader));
    }

    @Override
    @Transactional
    public WishlistItem moveToCart(String authHeader, Long productId) {
        User user = extractUserFromHeader(authHeader);
        List<WishlistItem> items = wishlistRepo.findByUser(user);
        WishlistItem target = items.stream()
                .filter(w -> w.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Product not found in wishlist"));

        Product product = target.getProduct();
        wishlistRepo.delete(target);

        Cart cart = cartService.getCart(user);
        CartItem existingItem = cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(productId) && !i.isSavedForLater())
                .findFirst().orElse(null);

        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + 1);
            cartItemRepo.save(existingItem);
        } else {
            CartItem cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProduct(product);
            cartItem.setQuantity(1);
            cartItem.setSavedForLater(false);
            cart.getItems().add(cartItem);
            cartItemRepo.save(cartItem);
        }

        cartService.getCartWithDetails(user);
        return target;
    }
}
