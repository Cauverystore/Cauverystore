package com.cauverystore.service;

import com.cauverystore.entities.Inventory;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.InventoryRepository;
import com.cauverystore.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepo;
    private final ProductRepository productRepo;

    public InventoryService(InventoryRepository inventoryRepo, ProductRepository productRepo) {
        this.inventoryRepo = inventoryRepo;
        this.productRepo = productRepo;
    }

    public Inventory addStock(Inventory inventory) {
        if (inventory.getProduct() == null || inventory.getProduct().getId() == null) {
            throw new RuntimeException("Product is required for inventory");
        }
        return inventoryRepo.save(inventory);
    }

    public Inventory getStock(Long productId) {
        return inventoryRepo.findByProduct_Id(productId);
    }

    public void deductStock(Product product, int qty) {
        Inventory inventory = inventoryRepo.findByProduct_Id(product.getId());
        if (inventory != null) {
            if (inventory.getStock() < qty) {
                throw new RuntimeException("Insufficient inventory for " + product.getName());
            }
            inventory.setStock(inventory.getStock() - qty);
            inventoryRepo.save(inventory);
        } else {
            if (product.getStock() == null || product.getStock() < qty) {
                throw new RuntimeException("Insufficient stock for " + product.getName());
            }
            product.setStock(product.getStock() - qty);
            productRepo.save(product);
        }
    }

    public Map<String, Object> getDashboard(Long sellerId) {
        return getInventoryDashboard();
    }

    public Map<String, Object> getInventoryDashboard() {
        Map<String, Object> dashboard = new HashMap<>();

        List<Product> activeProducts = productRepo.findByActiveTrue();
        dashboard.put("totalProducts", activeProducts.size());

        List<Map<String, Object>> lowStockProducts = new ArrayList<>();
        List<Map<String, Object>> outOfStockProducts = new ArrayList<>();
        List<Map<String, Object>> overstockProducts = new ArrayList<>();
        double totalValue = 0;

        for (Product product : activeProducts) {
            Inventory inv = inventoryRepo.findByProduct_Id(product.getId());
            int stock = inv != null ? inv.getStock() : (product.getStock() != null ? product.getStock() : 0);
            double value = stock * (product.getPrice() != null ? product.getPrice() : 0);
            totalValue += value;

            Map<String, Object> item = new HashMap<>();
            item.put("id", product.getId());
            item.put("name", product.getName());
            item.put("stock", stock);
            item.put("price", product.getPrice());
            item.put("value", Math.round(value * 100.0) / 100.0);

            if (stock <= 0) {
                outOfStockProducts.add(item);
            } else if (stock <= 5) {
                lowStockProducts.add(item);
            }

            if (stock > 50) {
                overstockProducts.add(item);
            }
        }

        dashboard.put("lowStockCount", lowStockProducts.size());
        dashboard.put("outOfStock", outOfStockProducts.size());
        dashboard.put("totalValue", Math.round(totalValue * 100.0) / 100.0);
        dashboard.put("lowStockProductList", lowStockProducts);
        dashboard.put("overstockProducts", overstockProducts);

        return dashboard;
    }
}
