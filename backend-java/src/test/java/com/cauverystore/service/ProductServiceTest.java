package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.exception.AccessDeniedException;
import com.cauverystore.exception.ProductNotFoundException;
import com.cauverystore.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepo;
    @Mock private CategoryRepository catRepo;
    @Mock private ProductImageRepository productImageRepo;
    @Mock private DiscountRepository discountRepo;
    @Mock private AuthorizationService authorizationService;

    @InjectMocks
    private ProductService productService;

    private Product product;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setPrice(100.0);
        product.setStock(50);
        product.setActive(true);
        product.setSellerId(1L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(Long userId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(userId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void getActiveProducts_shouldReturnAll_whenNotSeller() {
        when(productRepo.findByActiveTrue()).thenReturn(List.of(product));

        List<Product> result = productService.getActiveProducts();

        assertEquals(1, result.size());
        assertEquals("Test Product", result.get(0).getName());
    }

    @Test
    void getActiveProducts_shouldFilterBySeller_whenSeller() {
        setAuthenticatedUser(1L, "SELLER");
        Product own = new Product(); own.setId(1L); own.setActive(true); own.setSellerId(1L);
        Product other = new Product(); other.setId(2L); other.setActive(true); other.setSellerId(2L);
        when(productRepo.findByActiveTrue()).thenReturn(List.of(own, other));
        when(authorizationService.getCurrentUserId()).thenReturn(1L);

        List<Product> result = productService.getActiveProducts();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void getProductById_shouldReturnProduct_whenExists() {
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        Product result = productService.getProductById(1L);

        assertEquals("Test Product", result.getName());
    }

    @Test
    void getProductById_shouldThrow_whenNotFound() {
        when(productRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> productService.getProductById(999L));
    }

    @Test
    void getProductById_shouldRejectSeller_whenProductNotOwned() {
        setAuthenticatedUser(2L, "SELLER");
        product.setSellerId(1L);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(authorizationService.getCurrentUserId()).thenReturn(2L);

        assertThrows(AccessDeniedException.class, () -> productService.getProductById(1L));
    }

    @Test
    void addProduct_shouldSetSellerId_whenSeller() {
        setAuthenticatedUser(1L, "SELLER");
        Product newProduct = new Product();
        newProduct.setName("New Product");
        newProduct.setPrice(50.0);
        when(authorizationService.getCurrentUserId()).thenReturn(1L);
        when(productRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.addProduct(newProduct);

        assertEquals(1L, result.getSellerId());
    }

    @Test
    void addProduct_shouldNotSetSellerId_whenAdmin() {
        setAuthenticatedUser(1L, "ADMIN");
        Product newProduct = new Product();
        newProduct.setName("New Product");
        when(productRepo.save(any())).thenReturn(newProduct);

        Product result = productService.addProduct(newProduct);

        assertNull(result.getSellerId());
    }

    @Test
    void updateProduct_shouldUpdateFields() {
        Product update = new Product();
        update.setName("Updated Name");
        update.setPrice(200.0);
        update.setStock(100);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(any())).thenReturn(product);

        Product result = productService.updateProduct(1L, update);

        assertEquals("Updated Name", result.getName());
        assertEquals(200.0, result.getPrice());
        assertEquals(100, result.getStock());
    }

    @Test
    void updateProduct_shouldEnforceOwnership_whenSeller() {
        setAuthenticatedUser(2L, "SELLER");
        product.setSellerId(1L);
        Product update = new Product();
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(authorizationService.getCurrentUserId()).thenReturn(2L);

        assertThrows(AccessDeniedException.class, () -> productService.updateProduct(1L, update));
    }

    @Test
    void deleteProductCascade_shouldDelete() {
        productService.deleteProductCascade(1L);

        verify(productRepo).deleteById(1L);
    }

    @Test
    void deleteProductCascade_shouldEnforceOwnership_whenSeller() {
        setAuthenticatedUser(2L, "SELLER");
        product.setSellerId(1L);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(authorizationService.getCurrentUserId()).thenReturn(2L);

        assertThrows(AccessDeniedException.class, () -> productService.deleteProductCascade(1L));
        verify(productRepo, never()).deleteById(any());
    }

    @Test
    void getDiscountedPrice_shouldReturnBestDiscount() {
        Discount d1 = new Discount();
        d1.setType("PERCENTAGE"); d1.setValue(10.0); d1.setActive(true);
        d1.setStartDate(LocalDate.now().minusDays(1));
        d1.setEndDate(LocalDate.now().plusDays(1));

        Discount d2 = new Discount();
        d2.setType("FLAT"); d2.setValue(15.0); d2.setActive(true);
        d2.setStartDate(LocalDate.now().minusDays(1));
        d2.setEndDate(LocalDate.now().plusDays(1));

        product.setDiscounts(List.of(d1, d2));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        double price = productService.getDiscountedPriceDouble(1L);

        assertEquals(85.0, price, 0.01);
    }

    @Test
    void getDiscountedPrice_shouldReturnOriginalPrice_whenNoDiscounts() {
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        double price = productService.getDiscountedPriceDouble(1L);

        assertEquals(100.0, price, 0.01);
    }

    @Test
    void suspendProduct_shouldSetActiveFalse() {
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(any())).thenReturn(product);

        Product result = productService.suspendProduct(1L);

        assertFalse(result.isActive());
    }

    @Test
    void updateStock_shouldUpdateStock() {
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(any())).thenReturn(product);

        Product result = productService.updateStock(1L, 25);

        assertEquals(25, result.getStock());
    }

    @Test
    void updateActiveStatus_shouldToggleActive() {
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(any())).thenReturn(product);

        Product result = productService.updateActiveStatus(1L, false);

        assertFalse(result.isActive());
    }

    @Test
    void addDiscount_shouldSaveDiscount() {
        Discount discount = new Discount();
        discount.setType("PERCENTAGE");
        discount.setValue(20.0);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(discountRepo.save(any())).thenReturn(discount);

        Discount result = productService.addDiscount(1L, discount);

        assertEquals(20.0, result.getValue());
        verify(discountRepo).save(discount);
    }

    @Test
    void getSimilarProducts_shouldReturnEmptyList() {
        List<Product> result = productService.getSimilarProducts(1L);
        assertTrue(result.isEmpty());
    }

    @Test
    void getFeaturedProducts_shouldReturnAtMost10() {
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            Product p = new Product();
            p.setId((long) i);
            p.setActive(true);
            products.add(p);
        }
        when(productRepo.findByActiveTrue()).thenReturn(products);

        List<Product> result = productService.getFeaturedProducts();

        assertEquals(10, result.size());
    }
}
