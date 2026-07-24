package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.repository.ProductRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BulkOperationService {
    private final ProductRepository productRepo;
    public BulkOperationService(ProductRepository productRepo) { this.productRepo = productRepo; }

    public Map<String, Object> bulkPriceUpdate(List<Long> ids, Double price, Double offerPrice) {
        List<Product> products = productRepo.findAllById(ids);
        for (Product p : products) {
            if (price != null) p.setPrice(price);
            if (offerPrice != null) p.setOfferPrice(offerPrice);
        }
        productRepo.saveAll(products);
        return Map.of("message", products.size() + " product(s) updated", "count", products.size());
    }

    public Map<String, Object> bulkStockUpdate(List<Long> ids, Integer stock) {
        List<Product> products = productRepo.findAllById(ids);
        for (Product p : products) { p.setStock(stock); }
        productRepo.saveAll(products);
        return Map.of("message", products.size() + " product(s) updated", "count", products.size());
    }

    public Map<String, Object> bulkStatusUpdate(List<Long> ids, Boolean active) {
        List<Product> products = productRepo.findAllById(ids);
        for (Product p : products) { p.setActive(active); }
        productRepo.saveAll(products);
        return Map.of("message", products.size() + " product(s) updated", "count", products.size());
    }

    public Map<String, Object> bulkDelete(List<Long> ids) {
        productRepo.deleteAllById(ids);
        return Map.of("message", ids.size() + " product(s) deleted", "count", ids.size());
    }
}
