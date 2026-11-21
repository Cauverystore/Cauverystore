package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.repository.OrderItemRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The admin Bulk Operations page. Each product goes through the same checks as a single edit,
 * one at a time: a product that fails is reported and left unchanged, the rest still go through.
 */
@Service
public class BulkOperationService {
    private final ProductRepository productRepo;
    private final ProductService productService;
    private final ProductVariantRepository variantRepo;
    private final OrderItemRepository orderItemRepo;
    private final StockMovementService stockMovements;

    public BulkOperationService(ProductRepository productRepo, ProductService productService,
                                ProductVariantRepository variantRepo, OrderItemRepository orderItemRepo,
                                StockMovementService stockMovements) {
        this.productRepo = productRepo;
        this.productService = productService;
        this.variantRepo = variantRepo;
        this.orderItemRepo = orderItemRepo;
        this.stockMovements = stockMovements;
    }

    public Map<String, Object> bulkPriceUpdate(List<Long> ids, Double price, Double offerPrice) {
        if (price == null && offerPrice == null) throw new IllegalArgumentException("Enter a price.");
        return forEach(ids, "updated", id -> productService.updatePriceChecked(id, price, offerPrice));
    }

    public Map<String, Object> bulkStockUpdate(List<Long> ids, Integer stock) {
        if (stock == null || stock < 0) throw new IllegalArgumentException("Stock must be 0 or more.");
        return forEach(ids, "updated", id -> {
            Product p = find(id);
            // A product with sizes counts stock per size; one total here would disagree with them.
            if (!variantRepo.findByProduct_Id(id).isEmpty()) {
                throw new IllegalArgumentException("it has sizes, so update each size on its product page");
            }
            Integer before = p.getStock();
            p.setStock(stock);
            Product saved = productRepo.save(p);
            stockMovements.recordEdit(saved, null, before, stock);
        });
    }

    public Map<String, Object> bulkStatusUpdate(List<Long> ids, Boolean active) {
        if (active == null) throw new IllegalArgumentException("Choose Active or Inactive.");
        return forEach(ids, "updated", id -> productService.setActiveChecked(id, active));
    }

    public Map<String, Object> bulkDelete(List<Long> ids) {
        return forEach(ids, "deleted", id -> {
            find(id);
            // Checked here because the single-delete route switches such a product back on,
            // which must not happen to a hidden product from a page meant to remove it.
            if (orderItemRepo.existsByProduct_Id(id)) {
                throw new IllegalArgumentException("it has order history, so it was kept");
            }
            if (!productService.deleteProductCascade(id)) {
                throw new IllegalArgumentException("it is on a purchase order, so it was kept");
            }
        });
    }

    private Product find(Long id) {
        return productRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("not found"));
    }

    private Map<String, Object> forEach(List<Long> ids, String verb, Consumer<Long> step) {
        int done = 0;
        List<String> problems = new ArrayList<>();
        for (Long id : ids.stream().distinct().toList()) {
            try {
                step.accept(id);
                done++;
            } catch (Exception e) {
                problems.add("Product " + id + ": " + reason(e));
            }
        }
        productService.evictProductCache();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", done + " product(s) " + verb
                + (problems.isEmpty() ? "" : ", " + problems.size() + " not changed"));
        result.put("count", done);
        result.put("skipped", problems.size());
        result.put("problems", problems);
        return result;
    }

    private String reason(Exception e) {
        if (e instanceof ProductComplianceService.ComplianceBlockedException) {
            return "required legal details are missing";
        }
        String msg = e.getMessage();
        if (msg == null || msg.contains("Exception") || msg.length() > 200) return "could not be changed";
        return msg;
    }
}
