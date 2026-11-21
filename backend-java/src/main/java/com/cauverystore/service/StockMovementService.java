package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.ProductVariant;
import com.cauverystore.entities.StockMovement;
import com.cauverystore.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The stock history. Rows are only written by the code that really changes stock, so the page
 * can be trusted: there is deliberately no way to add a row by hand.
 */
@Service
public class StockMovementService {
    public static final String IN = "IN";
    public static final String OUT = "OUT";
    public static final String ADJUSTMENT = "ADJUSTMENT";
    public static final String RETURN = "RETURN";

    private final StockMovementRepository repo;
    public StockMovementService(StockMovementRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> recent() { return views(repo.findTop500ByOrderByIdDesc()); }

    @Transactional(readOnly = true)
    public Map<String, Object> getById(Long id) {
        return view(repo.findById(id).orElseThrow(() -> new RuntimeException("Movement not found")));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getByProduct(Long productId) {
        return views(repo.findByProductIdOrderByCreatedAtDesc(productId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getByWarehouse(Long warehouseId) {
        return views(repo.findByWarehouseIdOrderByCreatedAtDesc(warehouseId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getByType(String type) {
        return views(repo.findTop500ByTypeOrderByIdDesc(type));
    }

    /** Joins the caller's transaction, so a stock change and its history stand or fall together. */
    @Transactional
    public StockMovement record(Product product, ProductVariant variant, String type, int quantity,
                                Integer balanceAfter, String referenceType, Long referenceId, String notes) {
        StockMovement m = new StockMovement();
        m.setProduct(product);
        m.setVariant(variant);
        m.setType(type);
        m.setQuantity(quantity);
        m.setBalanceAfter(balanceAfter);
        m.setReferenceType(referenceType);
        m.setReferenceId(referenceId);
        m.setNotes(notes);
        return repo.save(m);
    }

    /** A number typed in by staff or a seller. Records the difference; no difference, no row. */
    @Transactional
    public void recordEdit(Product product, ProductVariant variant, Integer before, Integer after) {
        if (after == null) return;
        int was = before != null ? before : 0;
        if (was == after) return;
        record(product, variant, ADJUSTMENT, after - was, after, "MANUAL", null, "Stock edited");
    }

    /** An order's number only exists after its stock is taken, so the rows are labelled afterwards. */
    @Transactional
    public void linkToOrder(Collection<Long> movementIds, Long orderId) {
        if (movementIds == null || movementIds.isEmpty()) return;
        repo.setReference(movementIds, orderId);
    }

    /** History outlives the product: the rows stay, holding its name instead of a link. */
    @Transactional
    public void forgetProduct(Long productId, String name) {
        String kept = name != null && name.length() > 255 ? name.substring(0, 255) : name;
        repo.detachProduct(productId, kept);
    }

    @Transactional
    public void forgetVariant(Long variantId) {
        repo.detachVariant(variantId);
    }

    private List<Map<String, Object>> views(List<StockMovement> rows) {
        return rows.stream().map(this::view).toList();
    }

    private Map<String, Object> view(StockMovement m) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId());
        Product p = m.getProduct();
        v.put("productId", p != null ? p.getId() : null);
        v.put("productName", p != null ? p.getName() : m.getDeletedProductName());
        v.put("productDeleted", p == null && m.getDeletedProductName() != null);
        ProductVariant size = m.getVariant();
        v.put("variantId", size != null ? size.getId() : null);
        v.put("variantLabel", size != null ? size.getVariantValue() : null);
        v.put("type", m.getType());
        v.put("quantity", m.getQuantity());
        v.put("balanceAfter", m.getBalanceAfter());
        v.put("referenceType", m.getReferenceType());
        v.put("referenceId", m.getReferenceId());
        v.put("notes", m.getNotes());
        v.put("createdAt", m.getCreatedAt());
        return v;
    }
}
