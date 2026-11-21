package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.ProductVariant;
import com.cauverystore.exception.ProductNotFoundException;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductVariantService {

    private final ProductVariantRepository variantRepo;
    private final ProductRepository productRepo;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private StockMovementService stockMovements;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.cauverystore.repository.PurchaseOrderItemRepository purchaseItems;

    public ProductVariantService(ProductVariantRepository variantRepo, ProductRepository productRepo) {
        this.variantRepo = variantRepo;
        this.productRepo = productRepo;
    }

    public ProductVariant addVariant(Long productId, ProductVariant variant) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));
        variant.setProduct(product);
        return variantRepo.save(variant);
    }

    public ProductVariant updateVariant(Long id, ProductVariant updated) {
        ProductVariant variant = variantRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Variant not found with id: " + id));
        Integer stockBefore = variant.getStock();
        if (updated.getVariantType() != null) {
            variant.setVariantType(updated.getVariantType());
        }
        if (updated.getVariantValue() != null) {
            variant.setVariantValue(updated.getVariantValue());
        }
        if (updated.getPrice() != null) {
            variant.setPrice(updated.getPrice());
        }
        if (updated.getStock() != null) {
            variant.setStock(updated.getStock());
        }
        if (updated.getSku() != null) {
            variant.setSku(updated.getSku());
        }
        ProductVariant saved = variantRepo.save(variant);
        if (stockMovements != null) {
            stockMovements.recordEdit(saved.getProduct(), saved, stockBefore, saved.getStock());
        }
        return saved;
    }

    public List<ProductVariant> getVariants(Long productId) {
        return variantRepo.findByProduct_Id(productId);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteVariant(Long id) {
        if (purchaseItems != null) purchaseItems.clearVariant(id);
        if (stockMovements != null) stockMovements.forgetVariant(id);
        variantRepo.deleteById(id);
    }
}
