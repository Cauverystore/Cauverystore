package com.cauverystore.service;

import com.cauverystore.entities.Cart;
import com.cauverystore.entities.CartItem;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.User;

import java.util.List;
import java.util.Map;

public interface CartService {

    Cart getCart(User user);

    Cart addItem(User user, Product product, int quantity);

    Cart removeItem(User user, Long cartItemId);

    Cart clearCart(User user);

    CartItem toggleSaveForLater(User user, Long itemId);

    Cart updateItemQuantity(User user, Long itemId, int quantity);

    Map<String, Object> getCartWithDetails(User user);

    List<Map<String, Object>> getFrequentlyBoughtTogether(User user);

    Map<String, Object> getCartWithDetails(String authHeader);

    CartItem addItem(String authHeader, Long productId, int quantity);

    void removeItem(String authHeader, Long itemId);

    void clearCart(String authHeader);

    CartItem saveForLater(String authHeader, Long itemId);

    CartItem moveToCart(String authHeader, Long itemId);

    List<Product> getFrequentlyBought(String authHeader);

    CartItem updateQuantity(String authHeader, Long itemId, int quantity);
}
