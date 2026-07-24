package com.cauverystore.controller;

import com.cauverystore.entities.WishlistItem;
import com.cauverystore.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wishlist")
@CrossOrigin("*")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<List<WishlistItem>> getWishlist(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(wishlistService.getWishlist(authHeader));
    }

    @PostMapping("/add/{productId}")
    public ResponseEntity<WishlistItem> addToWishlist(@PathVariable Long productId,
                                                   @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(wishlistService.addToWishlist(authHeader, productId));
    }

    @DeleteMapping("/remove/{productId}")
    public ResponseEntity<WishlistItem> removeFromWishlist(@PathVariable Long productId,
                                                        @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(wishlistService.removeFromWishlist(authHeader, productId));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearWishlist(@RequestHeader("Authorization") String authHeader) {
        wishlistService.clearWishlist(authHeader);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/move-to-cart/{productId}")
    public ResponseEntity<WishlistItem> moveToCart(@PathVariable Long productId,
                                                @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(wishlistService.moveToCart(authHeader, productId));
    }
}
