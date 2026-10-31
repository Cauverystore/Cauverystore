package com.cauverystore.controller;

import com.cauverystore.entities.*;
import com.cauverystore.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final AuthService authService;

    private final com.cauverystore.service.SellerAccessService sellerAccess;
    private final com.cauverystore.repository.UserRepository userRepo;
    private final com.cauverystore.repository.GstConfigurationRepository gstConfigRepo;
    private final com.cauverystore.repository.SellerRegistrationRepository sellerRegRepo;

    /**
     * For a seller (or someone on a seller's team) this is the seller account being worked in,
     * so products stay with that account whoever on the team adds them. For staff it is their
     * own id, as before.
     */
    private Long getCurrentUserId() {
        if (isSeller()) {
            Long acting = sellerAccess.actingSellerId();
            if (acting != null) return acting;
        }
        return authService.deriveUserId();
    }

    /** A seller-team member may only do what their role allows; staff are unaffected. */
    private void sellerMay(String permission) {
        if (isSeller()) sellerAccess.require(permission);
    }

    private boolean isSeller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_SELLER"));
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> bulkDelete(@RequestBody Map<String, List<Long>> body) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        List<Long> ids = body.getOrDefault("ids", List.of());
        int deleted = 0, skipped = 0;
        for (Long id : ids) {
            if (isSeller()) productService.checkSellerOwnership(id, getCurrentUserId());
            try {
                if (productService.deleteProductCascade(id)) deleted++; else skipped++;
            } catch (Exception e) { skipped++; }
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("deleted", deleted);
        result.put("skipped", skipped);
        result.put("message", "Deleted " + deleted + " of " + ids.size() + " products" + (skipped > 0 ? " (" + skipped + " skipped)" : ""));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/bulk-upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> bulkUpload(@RequestParam("file") MultipartFile file) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        Long sellerId = isSeller() ? getCurrentUserId() : null;
        Map<String, Object> result = productService.bulkUpload(file, sellerId);
        if (result.containsKey("error")) return ResponseEntity.badRequest().body(result);
        return ResponseEntity.ok(result);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<?> getProductsPaginated(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(required = false) String search) {
        sellerMay(SellerPermissions.PRODUCTS_VIEW);
        var all = productService.getAllProducts();
        int size = 20;
        int start = page * size;
        int end = Math.min(start + size, all.size());
        return ResponseEntity.ok(Map.of(
            "content", start < all.size() ? all.subList(start, end) : List.of(),
            "totalPages", (int) Math.ceil((double) all.size() / size),
            "totalElements", all.size()
        ));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<List<Product>> getAllProducts() {
        sellerMay(SellerPermissions.PRODUCTS_VIEW);
        Long sellerId = isSeller() ? getCurrentUserId() : null;
        return ResponseEntity.ok(productService.getAllProducts(sellerId));
    }

    /**
     * Sellers a product can be assigned to, with the GSTIN and state that checkout uses to
     * decide between IGST and CGST+SGST. A product with no seller, or a seller with no GSTIN,
     * cannot be taxed and so cannot be ordered. The GSTIN is looked up the same way checkout
     * does: the seller's GST configuration first, then their registration.
     */
    @GetMapping("/sellers")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> sellers() {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (User u : userRepo.findByRole(Role.SELLER)) {
            java.util.Optional<SellerRegistration> reg = sellerRegRepo.findByUserId(u.getId());
            String gstin = gstConfigRepo.findBySellerId(u.getId()).map(GstConfiguration::getGstin).orElse(null);
            if (gstin == null || gstin.length() < 2) gstin = reg.map(SellerRegistration::getGstin).orElse(null);
            boolean hasGstin = gstin != null && gstin.length() >= 2;
            String stateCode = hasGstin ? gstin.substring(0, 2) : null;
            String business = reg.map(SellerRegistration::getBusinessName).orElse(null);
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("name", business != null && !business.isBlank() ? business
                    : (u.getFullName() != null ? u.getFullName() : u.getEmail()));
            m.put("email", u.getEmail());
            m.put("gstin", hasGstin ? gstin : null);
            m.put("stateCode", stateCode);
            m.put("state", stateCode != null ? com.cauverystore.util.StateCodeUtil.codeToStateName(stateCode) : null);
            m.put("city", reg.map(SellerRegistration::getCity).orElse(null));
            m.put("canBeTaxed", hasGstin);
            out.add(m);
        }
        out.sort(java.util.Comparator.comparing(m -> String.valueOf(m.get("name")).toLowerCase()));
        return ResponseEntity.ok(out);
    }

    @PostMapping("/add")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Product> addProduct(@RequestBody Product product) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) {
            product.setSellerId(getCurrentUserId());
        }
        return ResponseEntity.ok(productService.addProduct(product));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Product> getProduct(@PathVariable Long productId) {
        sellerMay(SellerPermissions.PRODUCTS_VIEW);
        if (isSeller()) {
            productService.checkSellerOwnership(productId, getCurrentUserId());
        }
        return ResponseEntity.ok(productService.getProductForAdmin(productId));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Product> updateProduct(@PathVariable Long productId, @RequestBody Product product) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) {
            productService.checkSellerOwnership(productId, getCurrentUserId());
        }
        return ResponseEntity.ok(productService.updateProduct(productId, product));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteProduct(@PathVariable Long productId) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) {
            productService.checkSellerOwnership(productId, getCurrentUserId());
        }
        boolean deleted = productService.deleteProductCascade(productId);
        return ResponseEntity.ok(Map.of(
                "deleted", deleted,
                "message", deleted
                        ? "Product deleted"
                        : "Product has order history and cannot be permanently deleted; it remains active and available"
        ));
    }

    @PutMapping("/{productId}/approval")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Product> updateApproval(@PathVariable Long productId, @RequestBody Map<String, String> body) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        Product product = productService.getProductForAdmin(productId);
        product.setApprovalStatus(body.get("approvalStatus"));
        return ResponseEntity.ok(productService.updateProduct(productId, product));
    }

    @PutMapping("/{productId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Product> toggleStatus(@PathVariable Long productId, @RequestBody Map<String,Object> body) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        return ResponseEntity.ok(productService.updateActiveStatus(productId, (Boolean) body.getOrDefault("active", true)));
    }

    @PutMapping("/{productId}/suspend")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Product> suspendProduct(@PathVariable Long productId) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) {
            productService.checkSellerOwnership(productId, getCurrentUserId());
        }
        return ResponseEntity.ok(productService.suspendProduct(productId));
    }

    @PutMapping("/{productId}/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Product> updateStock(@PathVariable Long productId, @RequestBody Map<String, Integer> body) {
        sellerMay(SellerPermissions.INVENTORY_MANAGE);
        if (isSeller()) {
            productService.checkSellerOwnership(productId, getCurrentUserId());
        }
        return ResponseEntity.ok(productService.updateStock(productId, body.get("stock")));
    }

    @GetMapping("/inventory/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> inventoryDashboard() {
        sellerMay(SellerPermissions.PRODUCTS_VIEW);
        Long sellerId = isSeller() ? getCurrentUserId() : null;
        return ResponseEntity.ok(inventoryService.getDashboard(sellerId));
    }

    @GetMapping("/{productId}/variants")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductVariant>> getVariants(@PathVariable Long productId) {
        sellerMay(SellerPermissions.PRODUCTS_VIEW);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        return ResponseEntity.ok(productService.getVariants(productId));
    }

    @PostMapping("/{productId}/variants")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<ProductVariant> addVariant(@PathVariable Long productId, @RequestBody ProductVariant variant) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        return ResponseEntity.ok(productService.addVariant(productId, variant));
    }

    @PutMapping("/{productId}/variants/{variantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<ProductVariant> updateVariant(@PathVariable Long productId,
                                                  @PathVariable Long variantId,
                                                  @RequestBody ProductVariant variant) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        return ResponseEntity.ok(productService.updateVariant(productId, variantId, variant));
    }

    @DeleteMapping("/{productId}/variants/{variantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteVariant(@PathVariable Long productId,
                                               @PathVariable Long variantId) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        productService.deleteVariant(productId, variantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{productId}/images")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductImage>> getImages(@PathVariable Long productId) {
        sellerMay(SellerPermissions.PRODUCTS_VIEW);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        return ResponseEntity.ok(productService.getImages(productId));
    }

    @PostMapping("/{productId}/images")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<ProductImage> uploadImage(@PathVariable Long productId,
                                                     @RequestParam("file") MultipartFile file) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        return ResponseEntity.ok(productService.uploadImage(productId, file));
    }

    @PostMapping("/{productId}/images/url")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<ProductImage> attachImageUrls(@PathVariable Long productId,
                                                        @RequestBody Map<String, String> body) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        return ResponseEntity.ok(productService.attachImageUrls(
                productId,
                body.get("preview_url"),
                body.get("thumbnail_url"),
                body.get("zoom_url"),
                body.get("original_url")));
    }

    @PutMapping("/{productId}/images/reorder")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Void> reorderImages(@PathVariable Long productId,
                                               @RequestBody Map<String, List<Long>> body) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        productService.reorderImages(productId, body.getOrDefault("order", List.of()));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{productId}/images/{imageId}/main")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<ProductImage> setMainImage(@PathVariable Long productId,
                                                      @PathVariable Long imageId) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        return ResponseEntity.ok(productService.setMainImage(productId, imageId));
    }

    @DeleteMapping("/{productId}/images/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteImage(@PathVariable Long productId,
                                             @PathVariable Long imageId) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        productService.deleteImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{productId}/discounts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Discount>> getDiscounts(@PathVariable Long productId) {
        sellerMay(SellerPermissions.PRODUCTS_VIEW);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        return ResponseEntity.ok(productService.getDiscounts(productId));
    }

    @PostMapping("/{productId}/discounts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Discount> addDiscount(@PathVariable Long productId, @RequestBody Discount discount) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        return ResponseEntity.ok(productService.addDiscount(productId, discount));
    }

    @GetMapping("/{productId}/discounts/{discountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Discount> getDiscount(@PathVariable Long productId,
                                                 @PathVariable Long discountId) {
        sellerMay(SellerPermissions.PRODUCTS_VIEW);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        return ResponseEntity.ok(productService.getDiscount(productId, discountId));
    }

    @PutMapping("/{productId}/discounts/{discountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Discount> updateDiscount(@PathVariable Long productId,
                                                    @PathVariable Long discountId,
                                                    @RequestBody Discount discount) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        return ResponseEntity.ok(productService.updateDiscount(productId, discountId, discount));
    }

    @DeleteMapping("/{productId}/discounts/{discountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteDiscount(@PathVariable Long productId,
                                                @PathVariable Long discountId) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        productService.deleteDiscount(productId, discountId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/clone")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Product> cloneProduct(@PathVariable Long productId) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        return ResponseEntity.ok(productService.cloneProduct(productId));
    }

    @PutMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> bulkEdit(@RequestBody Map<String, Object> body) {
        sellerMay(SellerPermissions.PRODUCTS_MANAGE);
        Long sellerId = isSeller() ? getCurrentUserId() : null;
        return ResponseEntity.ok(productService.bulkEdit(body, sellerId));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportProducts() throws Exception {
        sellerMay(SellerPermissions.PRODUCTS_VIEW);
        var products = productService.getAllProducts();
        org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Products");
        var hs = wb.createCellStyle();
        var f = wb.createFont(); f.setBold(true); hs.setFont(f);
        String[] headers = {"ID","Product Code","Name","Brand","Category","Price","Stock","SKU","Status"};
        var row = sheet.createRow(0);
        for (int i=0; i<headers.length; i++) { var c=row.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(hs); sheet.setColumnWidth(i,3500); }
        int r=1;
        for (var p : products) {
            var dr = sheet.createRow(r++);
            dr.createCell(0).setCellValue(p.getId());
            dr.createCell(1).setCellValue(p.getProductCode());
            dr.createCell(2).setCellValue(p.getName());
            dr.createCell(3).setCellValue(p.getBrand());
            dr.createCell(4).setCellValue(p.getCategory()!=null?p.getCategory().getName():"");
            dr.createCell(5).setCellValue(p.getPrice());
            dr.createCell(6).setCellValue(p.getStock());
            dr.createCell(7).setCellValue(p.getSku());
            dr.createCell(8).setCellValue(p.getProductStatus());
        }
        var bos = new java.io.ByteArrayOutputStream();
        wb.write(bos); wb.close();
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=products-report.xlsx")
            .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(bos.toByteArray());
    }
}
