package com.cauverystore.service;

import com.cauverystore.entities.User;
import com.cauverystore.entities.WishlistItem;

public interface WishlistService {

    java.util.List<WishlistItem> getWishlist(User user);

    WishlistItem addToWishlist(User user, Long productId);

    java.util.List<WishlistItem> getWishlist(String authHeader);

    void removeFromWishlist(User user, Long itemId);

    void clearWishlist(User user);

    WishlistItem addToWishlist(String authHeader, Long productId);

    WishlistItem removeFromWishlist(String authHeader, Long productId);

    void clearWishlist(String authHeader);

    WishlistItem moveToCart(String authHeader, Long productId);
}
