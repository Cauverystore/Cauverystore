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
@CrossOrigin("*")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final AuthService authService;

    private Long getCurrentUserId() {
        return authService.deriveUserId();
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
        Long sellerId = isSeller() ? getCurrentUserId() : null;
        return ResponseEntity.ok(productService.bulkUpload(file, sellerId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<?> getProductsPaginated(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(required = false) String search) {
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
        Long sellerId = isSeller() ? getCurrentUserId() : null;
        return ResponseEntity.ok(productService.getAllProducts(sellerId));
    }

    @PostMapping("/add")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Product> addProduct(@RequestBody Product product) {
        if (isSeller()) {
            product.setSellerId(getCurrentUserId());
        }
        return ResponseEntity.ok(productService.addProduct(product));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Product> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.getProductForAdmin(productId));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Product> updateProduct(@PathVariable Long productId, @RequestBody Product product) {
        if (isSeller()) {
            productService.checkSellerOwnership(productId, getCurrentUserId());
        }
        return ResponseEntity.ok(productService.updateProduct(productId, product));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        if (isSeller()) {
            productService.checkSellerOwnership(productId, getCurrentUserId());
        }
        productService.deleteProductCascade(productId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{productId}/approval")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Product> updateApproval(@PathVariable Long productId, @RequestBody Map<String, String> body) {
        Product product = productService.getProductForAdmin(productId);
        product.setApprovalStatus(body.get("approvalStatus"));
        return ResponseEntity.ok(productService.updateProduct(productId, product));
    }

    @PutMapping("/{productId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Product> toggleStatus(@PathVariable Long productId, @RequestBody Map<String,Object> body) {
        if (isSeller()) productService.checkSellerOwnership(productId, getCurrentUserId());
        return ResponseEntity.ok(productService.updateActiveStatus(productId, (Boolean) body.getOrDefault("active", true)));
    }

    @PutMapping("/{productId}/suspend")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Product> suspendProduct(@PathVariable Long productId) {
        if (isSeller()) {
            productService.checkSellerOwnership(productId, getCurrentUserId());
        }
        return ResponseEntity.ok(productService.suspendProduct(productId));
    }

    @PutMapping("/{productId}/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Product> updateStock(@PathVariable Long productId, @RequestBody Map<String, Integer> body) {
        if (isSeller()) {
            productService.checkSellerOwnership(productId, getCurrentUserId());
        }
        return ResponseEntity.ok(productService.updateStock(productId, body.get("stock")));
    }

    @GetMapping("/inventory/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> inventoryDashboard() {
        Long sellerId = isSeller() ? getCurrentUserId() : null;
        return ResponseEntity.ok(inventoryService.getDashboard(sellerId));
    }

    @GetMapping("/{productId}/variants")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductVariant>> getVariants(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.getVariants(productId));
    }

    @PostMapping("/{productId}/variants")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<ProductVariant> addVariant(@PathVariable Long productId, @RequestBody ProductVariant variant) {
        return ResponseEntity.ok(productService.addVariant(productId, variant));
    }

    @PutMapping("/{productId}/variants/{variantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<ProductVariant> updateVariant(@PathVariable Long productId,
                                                  @PathVariable Long variantId,
                                                  @RequestBody ProductVariant variant) {
        return ResponseEntity.ok(productService.updateVariant(productId, variantId, variant));
    }

    @DeleteMapping("/{productId}/variants/{variantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteVariant(@PathVariable Long productId,
                                               @PathVariable Long variantId) {
        productService.deleteVariant(productId, variantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{productId}/images")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductImage>> getImages(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.getImages(productId));
    }

    @PostMapping("/{productId}/images")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<ProductImage> uploadImage(@PathVariable Long productId,
                                                     @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(productService.uploadImage(productId, file));
    }

    @PutMapping("/{productId}/images/reorder")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Void> reorderImages(@PathVariable Long productId,
                                               @RequestBody Map<String, List<Long>> body) {
        productService.reorderImages(productId, body.getOrDefault("order", List.of()));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{productId}/images/{imageId}/main")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<ProductImage> setMainImage(@PathVariable Long productId,
                                                      @PathVariable Long imageId) {
        return ResponseEntity.ok(productService.setMainImage(productId, imageId));
    }

    @DeleteMapping("/{productId}/images/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteImage(@PathVariable Long productId,
                                             @PathVariable Long imageId) {
        productService.deleteImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{productId}/discounts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Discount>> getDiscounts(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.getDiscounts(productId));
    }

    @PostMapping("/{productId}/discounts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Discount> addDiscount(@PathVariable Long productId, @RequestBody Discount discount) {
        return ResponseEntity.ok(productService.addDiscount(productId, discount));
    }

    @GetMapping("/{productId}/discounts/{discountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Discount> getDiscount(@PathVariable Long productId,
                                                 @PathVariable Long discountId) {
        return ResponseEntity.ok(productService.getDiscount(productId, discountId));
    }

    @PutMapping("/{productId}/discounts/{discountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Discount> updateDiscount(@PathVariable Long productId,
                                                    @PathVariable Long discountId,
                                                    @RequestBody Discount discount) {
        return ResponseEntity.ok(productService.updateDiscount(productId, discountId, discount));
    }

    @DeleteMapping("/{productId}/discounts/{discountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteDiscount(@PathVariable Long productId,
                                                @PathVariable Long discountId) {
        productService.deleteDiscount(productId, discountId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/clone")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Product> cloneProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.cloneProduct(productId));
    }

    @PutMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> bulkEdit(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(productService.bulkEdit(body));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportProducts() throws Exception {
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
