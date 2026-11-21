package com.cauverystore.service;

import com.cauverystore.entities.Inventory;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.InventoryRepository;
import com.cauverystore.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepo;
    private final ProductRepository productRepo;
    private final ProductService productService;
    private final com.cauverystore.repository.ProductVariantRepository variantRepo;
    private final StockMovementService movements;

    public InventoryService(InventoryRepository inventoryRepo, ProductRepository productRepo, ProductService productService,
                            com.cauverystore.repository.ProductVariantRepository variantRepo,
                            StockMovementService movements) {
        this.inventoryRepo = inventoryRepo;
        this.productRepo = productRepo;
        this.productService = productService;
        this.variantRepo = variantRepo;
        this.movements = movements;
    }

    // An order line for a chosen size takes stock from that size AND from the product's total,
    // which is what the storefront shows as "in stock" / "only N left".
    // Returns the history row, so the caller can label it once the order has a number.
    @Transactional
    public Long deductStock(Product product, com.cauverystore.entities.ProductVariant variant, int qty) {
        if (variant != null) {
            int updated = variantRepo.decrementStockIfAvailable(variant.getId(), qty);
            if (updated == 0) {
                throw new RuntimeException("Insufficient stock for " + product.getName()
                        + " (" + variant.getVariantValue() + ")");
            }
        }
        takeFromProduct(product, qty);
        return movements.record(product, variant, StockMovementService.OUT, qty, balance(product, variant),
                "ORDER", null, "Sold").getId();
    }

    @Transactional
    public void restoreForCancelledOrder(Product product, com.cauverystore.entities.ProductVariant variant,
                                         int qty, Long orderId) {
        addBack(product, variant, qty);
        movements.record(product, variant, StockMovementService.IN, qty, balance(product, variant),
                "ORDER", orderId, "Order cancelled");
    }

    @Transactional
    public void restoreForReturn(Product product, com.cauverystore.entities.ProductVariant variant,
                                 int qty, Long returnId) {
        addBack(product, variant, qty);
        movements.record(product, variant, StockMovementService.RETURN, qty, balance(product, variant),
                "RETURN", returnId, "Customer return");
    }

    @Transactional
    public void receivePurchase(Product product, com.cauverystore.entities.ProductVariant variant,
                                int qty, Long purchaseOrderId) {
        addBack(product, variant, qty);
        movements.record(product, variant, StockMovementService.IN, qty, balance(product, variant),
                "PURCHASE_ORDER", purchaseOrderId, "Purchase received");
    }

    private void addBack(Product product, com.cauverystore.entities.ProductVariant variant, int qty) {
        if (variant != null) {
            variantRepo.incrementStock(variant.getId(), qty);
        }
        addToProduct(product, qty);
    }

    // For a size, its own count; otherwise whichever table this product's stock lives in.
    private Integer balance(Product product, com.cauverystore.entities.ProductVariant variant) {
        if (variant != null) return variantRepo.findStockById(variant.getId());
        Integer held = inventoryRepo.findStockByProductId(product.getId());
        return held != null ? held : productRepo.findStockById(product.getId());
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

    private void takeFromProduct(Product product, int qty) {
        Inventory inventory = inventoryRepo.findByProduct_Id(product.getId());
        if (inventory != null) {
            int updated = inventoryRepo.decrementStockIfAvailable(product.getId(), qty);
            if (updated == 0) {
                throw new RuntimeException("Insufficient inventory for " + product.getName());
            }
        } else {
            int updated = productRepo.decrementStockIfAvailable(product.getId(), qty);
            if (updated == 0) {
                throw new RuntimeException("Insufficient stock for " + product.getName());
            }
        }
        // These queries update stock straight in the DB, bypassing ProductService's own
        // @CacheEvict methods entirely - without this, the storefront would keep serving
        // cached (and increasingly wrong) stock numbers after every order.
        productService.evictProductCache();
    }

    // Mirrors deductStock's choice of table exactly, so a cancellation always credits back
    // whichever table the original order actually deducted from instead of drifting the two
    // out of sync.
    private void addToProduct(Product product, int qty) {
        Inventory inventory = inventoryRepo.findByProduct_Id(product.getId());
        if (inventory != null) {
            inventoryRepo.incrementStock(product.getId(), qty);
        } else {
            productRepo.incrementStock(product.getId(), qty);
        }
        productService.evictProductCache();
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
