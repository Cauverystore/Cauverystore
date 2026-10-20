package com.cauverystore.controller;

import com.cauverystore.entities.SellerPayout;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.repository.GstConfigurationRepository;
import com.cauverystore.repository.SellerGstinRepository;
import com.cauverystore.repository.SellerPayoutRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.service.ProductService;
import com.cauverystore.service.SellerAccessService;
import com.cauverystore.service.SellerLicenceService;
import com.cauverystore.service.SellerPermissions;
import com.cauverystore.service.SellerService;
import com.cauverystore.util.GstComplianceUtil;
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
    private final SellerPayoutRepository payoutRepo;
    private final SellerLicenceService licenceService;
    private final SellerGstinRepository sellerGstinRepo;
    private final SellerAccessService access;

    public SellerController(SellerService sellerService, ProductService productService, UserRepository userRepo,
                            GstConfigurationRepository gstConfigRepo,
                            SellerRegistrationRepository sellerRegRepo,
                            ShippingLabelService shippingLabelService,
                            SellerPayoutRepository payoutRepo,
                            SellerLicenceService licenceService,
                            SellerGstinRepository sellerGstinRepo,
                            SellerAccessService access) {
        this.sellerService = sellerService;
        this.productService = productService;
        this.userRepo = userRepo;
        this.gstConfigRepo = gstConfigRepo;
        this.sellerRegRepo = sellerRegRepo;
        this.shippingLabelService = shippingLabelService;
        this.payoutRepo = payoutRepo;
        this.licenceService = licenceService;
        this.sellerGstinRepo = sellerGstinRepo;
        this.access = access;
    }

    /**
     * The seller account this request works on: the seller's own, or the store a team member is
     * working in. Each endpoint first checks the person's permission there (access.require).
     */
    private Long getCurrentSellerId() {
        return access.actingSellerId();
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        access.require(SellerPermissions.DASHBOARD_VIEW);
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
        access.require(SellerPermissions.PRODUCTS_VIEW);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        return ResponseEntity.ok(sellerService.getProducts(sellerId, page, size, category, stockStatus, dateAdded));
    }

    @PostMapping("/products")
    public ResponseEntity<?> createProduct(@RequestBody com.cauverystore.entities.Product product) {
        access.require(SellerPermissions.PRODUCTS_MANAGE);
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
        // A seller's listing is reviewed before shoppers see it; only an admin can approve.
        product.setApprovalStatus("pending");
        if (product.getProductStatus() == null) product.setProductStatus("published");
        return ResponseEntity.ok(productService.addProduct(product));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody com.cauverystore.entities.Product product) {
        // A role with stock rights but not product rights (a warehouse manager) may change the
        // stock level and nothing else.
        boolean stockOnly = !access.require(SellerPermissions.DASHBOARD_VIEW).can(SellerPermissions.PRODUCTS_MANAGE);
        if (stockOnly) access.require(SellerPermissions.INVENTORY_MANAGE);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        try {
            productService.verifySellerOwnership(id, sellerId);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your product"));
        }
        // A seller cannot approve their own listing, or hand it to another seller, by sending
        // the field in an edit.
        product.setApprovalStatus(null);
        product.setSellerId(null);
        if (stockOnly) {
            com.cauverystore.entities.Product current = productService.getProductForAdmin(id);
            com.cauverystore.entities.Product stock = new com.cauverystore.entities.Product();
            stock.setStock(product.getStock());
            stock.setActive(current.isActive());
            return ResponseEntity.ok(productService.updateProduct(id, stock));
        }
        return ResponseEntity.ok(productService.updateProduct(id, product));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        access.require(SellerPermissions.PRODUCTS_MANAGE);
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
        access.require(SellerPermissions.PRODUCTS_VIEW);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        try {
            return ResponseEntity.ok(sellerService.getProductDetail(id, sellerId));
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/products/export")
    public ResponseEntity<?> exportProducts() throws Exception {
        access.require(SellerPermissions.PRODUCTS_VIEW);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not authenticated"));

        var products = productService.getAllProducts(sellerId);
        org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Products");
        var hs = wb.createCellStyle();
        var f = wb.createFont(); f.setBold(true); hs.setFont(f);
        String[] headers = {"ID","Product Code","Name","Brand","Category","Price","Stock","SKU","Status"};
        var row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) { var c = row.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(hs); sheet.setColumnWidth(i, 3500); }
        int r = 1;
        for (var p : products) {
            var dr = sheet.createRow(r++);
            dr.createCell(0).setCellValue(p.getId());
            dr.createCell(1).setCellValue(p.getProductCode());
            dr.createCell(2).setCellValue(p.getName());
            dr.createCell(3).setCellValue(p.getBrand());
            dr.createCell(4).setCellValue(p.getCategory() != null ? p.getCategory().getName() : "");
            dr.createCell(5).setCellValue(p.getPrice());
            dr.createCell(6).setCellValue(p.getStock());
            dr.createCell(7).setCellValue(p.getSku());
            dr.createCell(8).setCellValue(p.getProductStatus());
        }
        var bos = new java.io.ByteArrayOutputStream();
        wb.write(bos); wb.close();
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=my-products-report.xlsx")
            .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(bos.toByteArray());
    }

    @PostMapping("/products/bulk-upload")
    public ResponseEntity<?> bulkUpload(@RequestParam("file") MultipartFile file) {
        access.require(SellerPermissions.PRODUCTS_MANAGE);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not authenticated"));
        Map<String, Object> result = productService.bulkUpload(file, sellerId);
        if (result.containsKey("error")) return ResponseEntity.badRequest().body(result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/products/bulk-delete")
    public ResponseEntity<?> bulkDelete(@RequestBody Map<String, List<Long>> body) {
        access.require(SellerPermissions.PRODUCTS_MANAGE);
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
        access.require(SellerPermissions.ORDERS_VIEW);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        return ResponseEntity.ok(sellerService.getOrders(sellerId));
    }

    @GetMapping("/orders/{orderId}/shipping-label/pdf")
    public ResponseEntity<?> downloadShippingLabel(@PathVariable Long orderId) {
        access.require(SellerPermissions.ORDERS_VIEW);
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
        access.require(SellerPermissions.ORDERS_MANAGE);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        try {
            return ResponseEntity.ok(sellerService.updateOrderStatus(orderId, sellerId, body.get("status")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/orders/{orderId}/courier")
    public ResponseEntity<?> assignCourier(@PathVariable Long orderId, @RequestBody Map<String, String> body) {
        access.require(SellerPermissions.ORDERS_MANAGE);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        try {
            return ResponseEntity.ok(sellerService.assignCourier(orderId, sellerId, body.get("courier"), body.get("trackingNumber")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/returns")
    public ResponseEntity<?> getReturns() {
        access.require(SellerPermissions.ORDERS_VIEW);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        return ResponseEntity.ok(sellerService.getReturns(sellerId));
    }

    @PutMapping("/returns/{returnId}/status")
    public ResponseEntity<?> updateReturnStatus(@PathVariable Long returnId, @RequestBody Map<String, String> body) {
        access.require(SellerPermissions.RETURNS_MANAGE);
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
        access.require(SellerPermissions.ANALYTICS_VIEW);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        return ResponseEntity.ok(sellerService.getAnalytics(sellerId));
    }

    /**
     * The seller's GST numbers and licences for the Store Profile page. GST numbers are read-only
     * here (they come from registration and are verified there); licences are the seller's to add.
     */
    @GetMapping("/licences")
    public ResponseEntity<?> getLicences() {
        access.require(SellerPermissions.DASHBOARD_VIEW);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        List<Map<String, Object>> gst = new java.util.ArrayList<>();
        sellerRegRepo.findByUserId(sellerId).ifPresent(reg -> {
            if (reg.getGstin() != null && !reg.getGstin().isBlank()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("gstin", reg.getGstin());
                m.put("primary", true);
                m.put("status", reg.getGstinStatus());
                m.put("stateCode", reg.getGstinStateCode());
                gst.add(m);
            }
        });
        sellerGstinRepo.findBySellerId(sellerId).forEach(g -> {
            if (gst.stream().anyMatch(m -> g.getGstin().equalsIgnoreCase((String) m.get("gstin")))) return;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("gstin", g.getGstin());
            m.put("primary", Boolean.TRUE.equals(g.getPrimaryFlag()));
            m.put("status", g.getStatus());
            m.put("stateCode", g.getStateCode());
            gst.add(m);
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gstNumbers", gst);
        out.put("licences", licenceService.list(sellerId));
        out.put("types", licenceService.types());
        return ResponseEntity.ok(out);
    }

    @PostMapping("/licences")
    public ResponseEntity<?> addLicence(@RequestBody Map<String, Object> body) {
        access.require(SellerPermissions.STORE_MANAGE);
        return saveLicence(null, body);
    }

    @PutMapping("/licences/{id}")
    public ResponseEntity<?> updateLicence(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        access.require(SellerPermissions.STORE_MANAGE);
        return saveLicence(id, body);
    }

    private ResponseEntity<?> saveLicence(Long id, Map<String, Object> body) {
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        try {
            return ResponseEntity.ok(licenceService.save(sellerId, id, body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/licences/{id}")
    public ResponseEntity<?> deleteLicence(@PathVariable Long id) {
        access.require(SellerPermissions.STORE_MANAGE);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        try {
            licenceService.delete(sellerId, id);
            return ResponseEntity.ok(Map.of("message", "Licence removed"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Payments the marketplace has made to this seller. Voided entries stay in the list, marked,
     * so a figure the seller saw earlier never just disappears; they do not count in the total.
     */
    @GetMapping("/payouts")
    public ResponseEntity<?> getPayouts() {
        access.require(SellerPermissions.PAYOUTS_VIEW);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        List<Map<String, Object>> payouts = new java.util.ArrayList<>();
        double totalPaid = 0;
        for (SellerPayout p : payoutRepo.findBySellerIdOrderByPaidOnDescIdDesc(sellerId)) {
            Map<String, Object> m = AdminSellerPayoutController.toMap(p);
            m.remove("recordedBy");
            payouts.add(m);
            if (!Boolean.TRUE.equals(p.getVoided())) totalPaid += p.getAmount();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("payouts", payouts);
        out.put("totalPaid", Math.round(totalPaid * 100.0) / 100.0);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/store")
    public ResponseEntity<?> getStore() {
        access.require(SellerPermissions.DASHBOARD_VIEW);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        return ResponseEntity.ok(sellerService.getStoreProfile(sellerId));
    }

    @PutMapping("/store")
    public ResponseEntity<?> updateStore(@RequestBody Map<String, Object> body) {
        access.require(SellerPermissions.STORE_MANAGE);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not found"));
        return ResponseEntity.ok(sellerService.updateStoreProfile(sellerId, body));
    }

    @GetMapping("/notifications")
    public ResponseEntity<?> getNotifications() {
        access.require(SellerPermissions.DASHBOARD_VIEW);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not authenticated"));
        return ResponseEntity.ok(sellerService.getNotifications(sellerId));
    }

    @GetMapping("/gst-info")
    public ResponseEntity<?> getGstInfo() {
        access.require(SellerPermissions.DASHBOARD_VIEW);
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
        access.require(SellerPermissions.TAX_MANAGE);
        Long sellerId = getCurrentSellerId();
        if (sellerId == null) return ResponseEntity.status(403).body(Map.of("error", "Seller not authenticated"));
        SellerRegistration reg = sellerRegRepo.findByUserId(sellerId)
                .orElse(null);
        if (reg == null) return ResponseEntity.badRequest().body(Map.of("error", "Seller registration not found. Complete onboarding first."));
        if (body.containsKey("gstin")) {
            String gstin = body.get("gstin") == null ? "" : body.get("gstin").toString().trim().toUpperCase();
            if (!gstin.equals(reg.getGstin() == null ? "" : reg.getGstin())) {
                if (!gstin.isEmpty()) {
                    try {
                        GstComplianceUtil.validateGstinFormat(gstin);
                    } catch (RuntimeException e) {
                        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
                    }
                }
                // A new GSTIN has not been checked by anyone; it must not keep the old one's tick.
                reg.setGstin(gstin.isEmpty() ? null : gstin);
                reg.setGstinStatus("UNVERIFIED");
                reg.setGstinVerifiedAt(null);
            }
        }
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
