package com.cauverystore.controller;

import com.cauverystore.entities.Cart;
import com.cauverystore.entities.CartItem;
import com.cauverystore.entities.Product;
import com.cauverystore.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@CrossOrigin("*")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getCartWithDetails(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(cartService.getCartWithDetails(authHeader));
    }

    @PostMapping("/add")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SUPER_ADMIN')")
    public ResponseEntity<CartItem> addItem(@RequestParam Long productId,
                                             @RequestParam(defaultValue = "1") int quantity,
                                             @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(cartService.addItem(authHeader, productId, quantity));
    }

    @DeleteMapping("/remove/{itemId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SUPER_ADMIN')")
    public ResponseEntity<Void> removeItem(@PathVariable Long itemId,
                                            @RequestHeader("Authorization") String authHeader) {
        cartService.removeItem(authHeader, itemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/clear")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SUPER_ADMIN')")
    public ResponseEntity<Void> clearCart(@RequestHeader("Authorization") String authHeader) {
        cartService.clearCart(authHeader);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/save-for-later/{itemId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SUPER_ADMIN')")
    public ResponseEntity<CartItem> saveForLater(@PathVariable Long itemId,
                                                   @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(cartService.saveForLater(authHeader, itemId));
    }

    @PostMapping("/move-to-cart/{itemId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SUPER_ADMIN')")
    public ResponseEntity<CartItem> moveToCart(@PathVariable Long itemId,
                                                @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(cartService.moveToCart(authHeader, itemId));
    }

    @GetMapping("/frequently-bought")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SUPER_ADMIN')")
    public ResponseEntity<List<Product>> getFrequentlyBought(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(cartService.getFrequentlyBought(authHeader));
    }

    @PostMapping("/update-quantity/{itemId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SUPER_ADMIN')")
    public ResponseEntity<CartItem> updateQuantity(@PathVariable Long itemId,
                                                    @RequestParam int quantity,
                                                    @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(cartService.updateQuantity(authHeader, itemId, quantity));
    }
}
