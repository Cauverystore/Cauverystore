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
        return variantRepo.save(variant);
    }

    public List<ProductVariant> getVariants(Long productId) {
        return variantRepo.findByProduct_Id(productId);
    }

    public void deleteVariant(Long id) {
        variantRepo.deleteById(id);
    }
}
