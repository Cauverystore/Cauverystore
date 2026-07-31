package com.cauverystore.controller;

import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.repository.GstConfigurationRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.service.ProductService;
import com.cauverystore.service.SellerService;
import com.cauverystore.service.ShippingLabelService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seller")
@PreAuthorize("hasAnyRole('SELLER', 'SUPER_ADMIN')")
public class SellerController {

    private final SellerService sellerService;
    private final ProductService productService;
    private final UserRepository userRepo;
    private final GstConfigurationRepository gstConfigRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final ShippingLabelService shippingLabelService;

    public SellerController(SellerService sellerService, ProductService productService, UserRepository userRepo,
                            GstConfigurationRepository gstConfigRepo,
                            SellerRegistrationRepository sellerRegRepo,
                            ShippingLabelService shippingLabelService) {
        this.sellerService = sellerService;
        this.productService = productService;
        this.userRepo = userRepo;
        this.gstConfigRepo = gstConfigRepo;
        this.sellerRegRepo = sellerRegRepo;
        this.shippingLabelService = shippingLabelService;
    }

    private Long getCurrentSellerId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object details = auth.getDetails();
        if (details instanceof Long) return (Long) details;
        String name = auth.getName();
        var user = userRepo.findByEmail(name);
        if (user == null) user = userRepo.findByUsername(name);
        return user != null ? user.getId() : null;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not authenticated"));
        return ResponseEntity.ok(sellerService.getDashboard(sellerId));
    }

    @GetMapping("/products")
    public ResponseEntity<?> getProducts(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String stockStatus,
                                          @RequestParam(required = false) String dateAdded) {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        return ResponseEntity.ok(sellerService.getProducts(sellerId, page, size, category, stockStatus, dateAdded));
    }

    @PostMapping("/products")
    public ResponseEntity<?> createProduct(@RequestBody com.cauverystore.entities.Product product) {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        boolean hasGstConfig = gstConfigRepo.findBySellerId(sellerId)
                .map(c -> c.getGstin() != null && !c.getGstin().trim().isEmpty())
                .orElse(false);
        boolean hasRegGstin = sellerRegRepo.findByUserId(sellerId)
                .map(r -> r.getGstin() != null && !r.getGstin().trim().isEmpty())
                .orElse(false);
        if (!hasGstConfig && !hasRegGstin) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Cannot list products. Please configure your GSTIN first in GST Settings."));
        }
        product.setSellerId(sellerId);
        product.setActive(true);
        if (product.getProductStatus() == null) product.setProductStatus("published");
        return ResponseEntity.ok(productService.addProduct(product));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody com.cauverystore.entities.Product product) {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        try {
            productService.verifySellerOwnership(id, sellerId);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your product"));
        }
        return ResponseEntity.ok(productService.updateProduct(id, product));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        try {
            productService.verifySellerOwnership(id, sellerId);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your product"));
        }
        boolean deleted = productService.deleteProductCascade(id);
        return ResponseEntity.ok(Map.of(
                "deleted", deleted,
                "message", deleted
                        ? "Product deleted"
                        : "Product has order history and cannot be permanently deleted; it remains active and available"
        ));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<?> getProductDetail(@PathVariable Long id) {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        try {
            return ResponseEntity.ok(sellerService.getProductDetail(id, sellerId));
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/products/bulk-upload")
    public ResponseEntity<?> bulkUpload(@RequestParam("file") MultipartFile file) {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not authenticated"));
        Map<String, Object> result = productService.bulkUpload(file, sellerId);
        if (result.containsKey("error")) return ResponseEntity.badRequest().body(result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/products/bulk-delete")
    public ResponseEntity<?> bulkDelete(@RequestBody Map<String, List<Long>> body) {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        List<Long> ids = body.getOrDefault("ids", List.of());
        int deleted = 0, skipped = 0;
        for (Long id : ids) {
            try {
                productService.verifySellerOwnership(id, sellerId);
                if (productService.deleteProductCascade(id)) deleted++; else skipped++;
            } catch (Exception e) { skipped++; }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        result.put("skipped", skipped);
        result.put("message", "Deleted " + deleted + " of " + ids.size() + " products" + (skipped > 0 ? " (" + skipped + " skipped)" : ""));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getOrders() {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        return ResponseEntity.ok(sellerService.getOrders(sellerId));
    }

    @GetMapping("/orders/{orderId}/shipping-label/pdf")
    public ResponseEntity<?> downloadShippingLabel(@PathVariable Long orderId) {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        try {
            byte[] pdf = shippingLabelService.generateLabelPdf(orderId, sellerId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "shipping-label-" + orderId + ".pdf");
            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long orderId, @RequestBody Map<String, String> body) {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        try {
            return ResponseEntity.ok(sellerService.updateOrderStatus(orderId, sellerId, body.get("status")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/returns")
    public ResponseEntity<?> getReturns() {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        return ResponseEntity.ok(sellerService.getReturns(sellerId));
    }

    @PutMapping("/returns/{returnId}/status")
    public ResponseEntity<?> updateReturnStatus(@PathVariable Long returnId, @RequestBody Map<String, String> body) {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        try {
            return ResponseEntity.ok(sellerService.updateReturnStatus(returnId, sellerId, body.get("status"), body.get("adminNote")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics() {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        return ResponseEntity.ok(sellerService.getAnalytics(sellerId));
    }

    @GetMapping("/store")
    public ResponseEntity<?> getStore() {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        return ResponseEntity.ok(sellerService.getStoreProfile(sellerId));
    }

    @PutMapping("/store")
    public ResponseEntity<?> updateStore(@RequestBody Map<String, Object> body) {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        return ResponseEntity.ok(sellerService.updateStoreProfile(sellerId, body));
    }

    @GetMapping("/notifications")
    public ResponseEntity<?> getNotifications() {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not authenticated"));
        return ResponseEntity.ok(sellerService.getNotifications(sellerId));
    }

    @GetMapping("/gst-info")
    public ResponseEntity<?> getGstInfo() {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not authenticated"));
        SellerRegistration reg = sellerRegRepo.findByUserId(sellerId)
                .orElse(null);
        if (reg == null) return ResponseEntity.ok(Map.of("gstin", "", "panNumber", "", "businessName", "", "businessAddress", "", "licenses", ""));
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("gstin", reg.getGstin() != null ? reg.getGstin() : "");
        info.put("panNumber", reg.getPanNumber() != null ? reg.getPanNumber() : "");
        info.put("businessName", reg.getBusinessName() != null ? reg.getBusinessName() : "");
        info.put("businessAddress", reg.getBusinessAddress() != null ? reg.getBusinessAddress() : "");
        info.put("licenses", reg.getLicenses() != null ? reg.getLicenses() : "");
        return ResponseEntity.ok(info);
    }

    @PutMapping("/gst-info")
    public ResponseEntity<?> updateGstInfo(@RequestBody Map<String, Object> body) {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not authenticated"));
        SellerRegistration reg = sellerRegRepo.findByUserId(sellerId)
                .orElse(null);
        if (reg == null) return ResponseEntity.badRequest().body(Map.of("error", "Seller registration not found. Complete onboarding first."));
        if (body.containsKey("gstin")) reg.setGstin((String) body.get("gstin"));
        if (body.containsKey("panNumber")) reg.setPanNumber((String) body.get("panNumber"));
        if (body.containsKey("businessName")) reg.setBusinessName((String) body.get("businessName"));
        if (body.containsKey("businessAddress")) reg.setBusinessAddress((String) body.get("businessAddress"));
        if (body.containsKey("licenses")) reg.setLicenses((String) body.get("licenses"));
        sellerRegRepo.save(reg);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("gstin", reg.getGstin() != null ? reg.getGstin() : "");
        info.put("panNumber", reg.getPanNumber() != null ? reg.getPanNumber() : "");
        info.put("businessName", reg.getBusinessName() != null ? reg.getBusinessName() : "");
        info.put("businessAddress", reg.getBusinessAddress() != null ? reg.getBusinessAddress() : "");
        info.put("licenses", reg.getLicenses() != null ? reg.getLicenses() : "");
        info.put("message", "GST information updated successfully");
        return ResponseEntity.ok(info);
    }
}
