package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.ProductVariant;
import com.cauverystore.entities.PurchaseOrder;
import com.cauverystore.entities.PurchaseOrderItem;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.ProductVariantRepository;
import com.cauverystore.repository.PurchaseOrderRepository;
import com.cauverystore.repository.SupplierRepository;
import com.cauverystore.repository.WarehouseRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Purchase orders. Marking one RECEIVED adds its goods to stock, once. RECEIVED is final: undoing
 * it would silently take away stock that may already have been sold.
 */
@Service
public class PurchaseOrderService {
    public static final String PENDING = "PENDING";
    public static final String RECEIVED = "RECEIVED";
    private static final Set<String> STATUSES = Set.of(PENDING, "CONFIRMED", "SHIPPED", RECEIVED, "CANCELLED");

    public record ItemRequest(Long productId, Long variantId, Integer quantity, Double unitPrice) {}
    public record OrderRequest(String poNumber, Long supplierId, Long warehouseId, String status,
                               LocalDate orderDate, LocalDate expectedDelivery, LocalDate actualDelivery,
                               String notes, List<ItemRequest> items) {}

    private final PurchaseOrderRepository poRepo;
    private final SupplierRepository supplierRepo;
    private final WarehouseRepository warehouseRepo;
    private final ProductRepository productRepo;
    private final ProductVariantRepository variantRepo;
    private final InventoryService inventoryService;

    public PurchaseOrderService(PurchaseOrderRepository poRepo, SupplierRepository supplierRepo,
                                WarehouseRepository warehouseRepo, ProductRepository productRepo,
                                ProductVariantRepository variantRepo, InventoryService inventoryService) {
        this.poRepo = poRepo;
        this.supplierRepo = supplierRepo;
        this.warehouseRepo = warehouseRepo;
        this.productRepo = productRepo;
        this.variantRepo = variantRepo;
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAll() {
        return poRepo.findAll(Sort.by(Sort.Direction.DESC, "id")).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getById(Long id) {
        return view(poRepo.findById(id).orElseThrow(() -> new RuntimeException("Purchase order not found")));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getByStatus(String status) {
        return poRepo.findByStatus(status).stream().map(this::view).toList();
    }

    @Transactional
    public Map<String, Object> create(OrderRequest request) {
        String status = cleanStatus(request.status(), PENDING);
        PurchaseOrder po = new PurchaseOrder();
        po.setStatus(PENDING);
        applyDetails(po, request);
        replaceItems(po, request.items());
        po = poRepo.save(po);
        if (RECEIVED.equals(status)) receive(po);
        else po.setStatus(status);
        return view(poRepo.save(po));
    }

    @Transactional
    public Map<String, Object> update(Long id, OrderRequest request) {
        PurchaseOrder po = lockOpenOrder(id);
        String status = cleanStatus(request.status(), po.getStatus());
        applyDetails(po, request);
        if (request.items() != null) replaceItems(po, request.items());
        if (RECEIVED.equals(status)) receive(po);
        else po.setStatus(status);
        return view(poRepo.save(po));
    }

    @Transactional
    public Map<String, Object> changeStatus(Long id, String newStatus) {
        PurchaseOrder po = lockOpenOrder(id);
        String status = cleanStatus(newStatus, null);
        if (RECEIVED.equals(status)) receive(po);
        else po.setStatus(status);
        return view(poRepo.save(po));
    }

    @Transactional
    public void delete(Long id) {
        poRepo.delete(lockOpenOrder(id));
    }

    /** Locks the row, so two clicks at once cannot both add the same goods. */
    private PurchaseOrder lockOpenOrder(Long id) {
        PurchaseOrder po = poRepo.findForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));
        if (RECEIVED.equals(po.getStatus())) {
            throw new RuntimeException("This purchase order is already received and its goods are in stock, "
                    + "so it can no longer be changed.");
        }
        return po;
    }

    private void receive(PurchaseOrder po) {
        if (po.getItems().isEmpty()) {
            throw new RuntimeException("Add at least one product before marking this order as received.");
        }
        // Everything is checked first, so one bad line cannot leave half an order in stock.
        for (PurchaseOrderItem item : po.getItems()) checkLine(item.getProduct(), item.getVariant(), item.getQuantity());
        for (PurchaseOrderItem item : po.getItems()) {
            int already = item.getReceivedQuantity() != null ? item.getReceivedQuantity() : 0;
            int toAdd = item.getQuantity() - already;
            if (toAdd > 0) {
                inventoryService.receivePurchase(item.getProduct(), item.getVariant(), toAdd, po.getId());
                item.setReceivedQuantity(item.getQuantity());
            }
        }
        if (po.getActualDelivery() == null) po.setActualDelivery(LocalDate.now());
        po.setStatus(RECEIVED);
    }

    private void applyDetails(PurchaseOrder po, OrderRequest request) {
        po.setPoNumber(request.poNumber());
        po.setSupplier(request.supplierId() == null ? null : supplierRepo.findById(request.supplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found")));
        po.setWarehouse(request.warehouseId() == null ? null : warehouseRepo.findById(request.warehouseId())
                .orElseThrow(() -> new RuntimeException("Warehouse not found")));
        po.setOrderDate(request.orderDate());
        po.setExpectedDelivery(request.expectedDelivery());
        po.setActualDelivery(request.actualDelivery());
        po.setNotes(request.notes());
    }

    private void replaceItems(PurchaseOrder po, List<ItemRequest> requested) {
        List<PurchaseOrderItem> built = new ArrayList<>();
        double total = 0;
        for (ItemRequest line : requested == null ? List.<ItemRequest>of() : requested) {
            if (line.productId() == null) throw new RuntimeException("Choose a product for every line.");
            Product product = productRepo.findById(line.productId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));
            ProductVariant variant = line.variantId() == null ? null : variantRepo.findById(line.variantId())
                    .orElseThrow(() -> new RuntimeException("Size not found"));
            checkLine(product, variant, line.quantity());
            if (line.unitPrice() != null && line.unitPrice() < 0) {
                throw new RuntimeException("Unit price cannot be negative.");
            }
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(po);
            item.setProduct(product);
            item.setVariant(variant);
            item.setQuantity(line.quantity());
            item.setReceivedQuantity(0);
            item.setUnitPrice(line.unitPrice());
            double lineTotal = line.quantity() * (line.unitPrice() != null ? line.unitPrice() : 0);
            item.setTotalPrice(lineTotal);
            total += lineTotal;
            built.add(item);
        }
        po.getItems().clear();
        po.getItems().addAll(built);
        po.setTotalAmount(total);
    }

    private void checkLine(Product product, ProductVariant variant, Integer quantity) {
        if (product == null) throw new RuntimeException("Every line needs a product before it can be received.");
        if (quantity == null || quantity < 1) {
            throw new RuntimeException("Quantity for " + product.getName() + " must be at least 1.");
        }
        if (variant != null) {
            if (variant.getProduct() == null || !product.getId().equals(variant.getProduct().getId())) {
                throw new RuntimeException("That size does not belong to " + product.getName() + ".");
            }
        } else if (!variantRepo.findByProduct_Id(product.getId()).isEmpty()) {
            throw new RuntimeException("Choose a size for " + product.getName() + ".");
        }
    }

    private String cleanStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            if (fallback != null) return fallback;
            throw new RuntimeException("Choose a status.");
        }
        String clean = status.trim().toUpperCase();
        if (!STATUSES.contains(clean)) throw new RuntimeException("Unknown status: " + status);
        return clean;
    }

    private Map<String, Object> view(PurchaseOrder po) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", po.getId());
        v.put("poNumber", po.getPoNumber());
        v.put("supplierId", po.getSupplier() == null ? null : named(po.getSupplier().getId(), po.getSupplier().getName()));
        v.put("warehouseId", po.getWarehouse() == null ? null : named(po.getWarehouse().getId(), po.getWarehouse().getName()));
        v.put("status", po.getStatus());
        v.put("orderDate", po.getOrderDate());
        v.put("expectedDelivery", po.getExpectedDelivery());
        v.put("actualDelivery", po.getActualDelivery());
        v.put("totalAmount", po.getTotalAmount());
        v.put("notes", po.getNotes());
        List<Map<String, Object>> items = new ArrayList<>();
        for (PurchaseOrderItem item : po.getItems()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("id", item.getId());
            line.put("productId", item.getProduct() != null ? item.getProduct().getId() : null);
            line.put("productName", item.getProduct() != null ? item.getProduct().getName() : null);
            line.put("variantId", item.getVariant() != null ? item.getVariant().getId() : null);
            line.put("variantLabel", item.getVariant() != null ? item.getVariant().getVariantValue() : null);
            line.put("quantity", item.getQuantity());
            line.put("receivedQuantity", item.getReceivedQuantity());
            line.put("unitPrice", item.getUnitPrice());
            line.put("totalPrice", item.getTotalPrice());
            items.add(line);
        }
        v.put("items", items);
        return v;
    }

    private Map<String, Object> named(Long id, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        return m;
    }
}
