package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.exception.AccessDeniedException;
import com.cauverystore.exception.ProductNotFoundException;
import com.cauverystore.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

@Service
public class ProductService {

    private final ProductRepository productRepo;
    private final CategoryRepository catRepo;
    private final ProductImageRepository productImageRepo;
    private final DiscountRepository discountRepo;
    private final ProductVariantRepository variantRepo;
    private final AuthorizationService authorizationService;
    private final CartItemRepository cartItemRepo;
    private final WishlistRepository wishlistRepo;
    private final InventoryRepository inventoryRepo;
    private final ProductDiscountRepository productDiscountRepo;
    private final OrderItemRepository orderItemRepo;

    private final HsnClassificationService hsnClassificationService;

    public ProductService(ProductRepository productRepo, CategoryRepository catRepo,
                          ProductImageRepository productImageRepo, DiscountRepository discountRepo,
                          ProductVariantRepository variantRepo,
                          AuthorizationService authorizationService,
                          CartItemRepository cartItemRepo,
                          WishlistRepository wishlistRepo,
                          InventoryRepository inventoryRepo,
                          ProductDiscountRepository productDiscountRepo,
                          OrderItemRepository orderItemRepo,
                          HsnClassificationService hsnClassificationService) {
        this.productRepo = productRepo;
        this.hsnClassificationService = hsnClassificationService;
        this.catRepo = catRepo;
        this.productImageRepo = productImageRepo;
        this.discountRepo = discountRepo;
        this.variantRepo = variantRepo;
        this.authorizationService = authorizationService;
        this.cartItemRepo = cartItemRepo;
        this.wishlistRepo = wishlistRepo;
        this.inventoryRepo = inventoryRepo;
        this.productDiscountRepo = productDiscountRepo;
        this.orderItemRepo = orderItemRepo;
    }

    // Public storefront browsing (ProductController, permitAll) - never filtered by the
    // viewer's own role. A logged-in seller browsing the catalog to shop is a customer here,
    // same as anyone else; "my own products" is served exclusively by the seller/admin
    // management endpoints below, not the public catalog.
    @Cacheable(value = "products", key = "'active'")
    public List<Product> getActiveProducts() {
        return productRepo.findByActiveTrue();
    }

    @Cacheable(value = "products", key = "#id")
    public Product getProductById(Long id) {
        return productRepo.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
    }

    // Stock changes happen via InventoryService's atomic UPDATE queries, which never go through
    // this class's own @CacheEvict-annotated methods - so the "products" cache (including
    // getProductById's stock figure) would otherwise stay stale until an unrelated product edit
    // happened to evict it. InventoryService calls this after every stock change instead.
    @CacheEvict(value = "products", allEntries = true)
    public void evictProductCache() {
    }

    @Cacheable(value = "products", key = "'all'")
    public List<Product> getAllProducts() {
        if (authorizationService.hasRole("SELLER")) {
            Long sellerId = authorizationService.getCurrentUserId();
            return productRepo.findAll().stream()
                    .filter(p -> sellerId.equals(p.getSellerId()))
                    .toList();
        }
        return productRepo.findAll();
    }

    public Page<Product> searchProducts(String name, String categoryName, Double min, Double max,
                                         int page, int size, String sortBy, String direction) {
        Sort sort = direction != null && direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy != null ? sortBy : "id").descending()
                : Sort.by(sortBy != null ? sortBy : "id").ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        if (categoryName != null && !categoryName.isEmpty()) {
            Optional<Category> category = catRepo.findByName(categoryName);
            if (category.isPresent()) {
                if (name != null && !name.isEmpty()) {
                    return productRepo.searchProductsByCategory(name, min, max, category.get(), pageable);
                }
                return productRepo.searchAllByCategory(category.get(), min, max, pageable);
            }
        }

        if (name != null && !name.isEmpty()) {
            return productRepo.searchProducts(name, min, max, pageable);
        }

        return productRepo.searchAllProducts(min, max, pageable);
    }

    public ProductImage addImage(Long productId, String url) {
        Product product = getProductById(productId);
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setUrl(url);
        if (product.getImages().isEmpty()) {
            image.setMain(true);
        }
        return productImageRepo.save(image);
    }

    public double getDiscountedPriceDouble(Long productId) {
        Product product = getProductById(productId);
        List<Discount> discounts = product.getDiscounts();
        if (discounts == null || discounts.isEmpty()) {
            return product.getPrice();
        }

        double bestPrice = product.getPrice();
        LocalDate today = LocalDate.now();

        for (Discount discount : discounts) {
            if (!discount.isActive()) continue;
            if (discount.getStartDate() != null && discount.getEndDate() != null) {
                if (today.isBefore(discount.getStartDate()) || today.isAfter(discount.getEndDate())) {
                    continue;
                }
            }
            double discounted;
            if ("PERCENTAGE".equalsIgnoreCase(discount.getType())) {
                discounted = product.getPrice() * (1 - discount.getValue() / 100);
            } else {
                discounted = product.getPrice() - discount.getValue();
            }
            if (discounted < bestPrice) {
                bestPrice = discounted;
            }
        }

        return Math.max(bestPrice, 0);
    }

    public Map<String, Object> getDiscountedPrice(Long productId) {
        double price = getDiscountedPriceDouble(productId);
        return Map.of("price", price);
    }

    @CacheEvict(value = {"products", "categories"}, allEntries = true)
    public Map<String, Object> bulkUpload(MultipartFile file, Long sellerId) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (file.isEmpty()) { result.put("error", "File is empty"); return result; }
        int loaded = 0, errors = 0;
        List<String> imageErrors = new ArrayList<>();
        // Why each row failed, not just how many did. A bare count leaves a seller with a
        // spreadsheet they cannot correct.
        List<String> rowErrors = new ArrayList<>();
        try {
            org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(file.getInputStream());
            org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(r);
                if (row == null) continue;
                try {
                    Product p = new Product();
                    String code = getCell(row, 0);
                    if (code != null && !code.isEmpty()) p.setProductCode(code);
                    p.setName(getCell(row, 1));
                    p.setBrand(getCell(row, 2));
                    String catName = getCell(row, 3);
                    if (catName != null && !catName.isEmpty()) {
                        Optional<Category> catOpt = catRepo.findByName(catName);
                        catOpt.ifPresent(p::setCategory);
                    }
                    String priceStr = getCell(row, 4); if (priceStr != null) p.setPrice(Double.parseDouble(priceStr.replace(",","")));
                    String stockStr = getCell(row, 5); if (stockStr != null) p.setStock(Integer.parseInt(stockStr));
                    p.setDescription(getCell(row, 6)); p.setManufacturer(getCell(row, 7));
                    p.setModelNumber(getCell(row, 8)); p.setBarcode(getCell(row, 9));
                    p.setHsnCode(getCell(row, 10)); p.setCountryOfOrigin(getCell(row, 11));
                    // Packaging decides the GST rate for staples (rice 5% pre-packaged, nil loose),
                    // so the spreadsheet lets the seller answer it instead of leaving the product
                    // unresolved at sale time.
                    String packagedStr = getCell(row, 27);
                    if (packagedStr != null && !packagedStr.isBlank()) {
                        if ("yes".equalsIgnoreCase(packagedStr)) p.setPrePackagedAndLabelled(Boolean.TRUE);
                        else if ("no".equalsIgnoreCase(packagedStr)) p.setPrePackagedAndLabelled(Boolean.FALSE);
                        else throw new RuntimeException(
                                "Pre-Packaged and Labelled must be Yes or No, got '" + packagedStr + "'");
                    }
                    // Optional: the id of the specific published rate line the seller picked for an
                    // HSN taxed at more than one rate (same id the GST rate picker on the product form saves).
                    String rateLineId = getCell(row, 28);
                    if (rateLineId != null && !rateLineId.isBlank()) {
                        try {
                            p.setGstRateSelectionId(Long.parseLong(rateLineId.trim()));
                        } catch (NumberFormatException e) {
                            throw new RuntimeException(
                                    "GST Rate Line ID must be a number, got '" + rateLineId + "'");
                        }
                    }
                    p.setShortDescription(getCell(row, 12)); p.setTechnicalSpecs(getCell(row, 13));
                    p.setWarranty(getCell(row, 14)); p.setReturnPolicy(getCell(row, 15));
                    p.setMetaTitle(getCell(row, 16)); p.setMetaDescription(getCell(row, 17));
                    p.setMetaKeywords(getCell(row, 18));
                    String slug = getCell(row, 19);
                    String name = p.getName();
                    if (slug != null && !slug.isEmpty()) {
                        p.setSlug(slug);
                    } else if (name != null && !name.isEmpty()) {
                        p.setSlug(slugify(name));
                    }
                    String weightStr = getCell(row, 20); if (weightStr != null) p.setWeight(Double.parseDouble(weightStr));
                    p.setDimensions(getCell(row, 21)); p.setShippingClass(getCell(row, 22));
                    String delDays = getCell(row, 23); if (delDays != null) p.setDeliveryDays(Integer.parseInt(delDays));
                    String badgeStr = getCell(row, 24);
                    if (badgeStr != null && !badgeStr.isEmpty() && !"None".equalsIgnoreCase(badgeStr)) {
                        p.setBadges(new ArrayList<>(List.of(badgeStr)));
                    }
                    p.setSellerId(sellerId); p.setActive(true); p.setProductStatus("published");
                    // Bulk rows go straight to "published", so an unchecked code here reaches
                    // real sales. This path used to skip validation entirely, which made the
                    // spreadsheet the easiest way to get a bad HSN into the catalogue.
                    hsnClassificationService.validate(p);
                    hsnClassificationService.assertSellable(p);
                    Product saved = productRepo.save(p);
                    hsnClassificationService.rememberAssignment(saved, authorizationService.getCurrentUserEmail());

                    // Handle image URLs from columns 25 (PrimaryImageURL) and 26 (AdditionalImages)
                    boolean firstImage = true;
                    String primaryUrl = getCell(row, 25);
                    if (primaryUrl != null && !primaryUrl.isEmpty()) {
                        try {
                            downloadAndSaveImage(saved.getId(), primaryUrl, firstImage);
                            firstImage = false;
                        } catch (Exception imgEx) {
                            imageErrors.add("Row " + (r + 1) + " (" + (p.getName() != null ? p.getName() : "unknown") + "): Primary image failed - " + imgEx.getMessage());
                        }
                    }
                    String additionalUrls = getCell(row, 26);
                    if (additionalUrls != null && !additionalUrls.isEmpty()) {
                        String[] urls = additionalUrls.split(",");
                        for (String imgUrl : urls) {
                            imgUrl = imgUrl.trim();
                            if (!imgUrl.isEmpty()) {
                                try {
                                    downloadAndSaveImage(saved.getId(), imgUrl, firstImage);
                                    firstImage = false;
                                } catch (Exception imgEx) {
                                    imageErrors.add("Row " + (r + 1) + " (" + (p.getName() != null ? p.getName() : "unknown") + "): Additional image failed - " + imgEx.getMessage());
                                }
                            }
                        }
                    }

                    loaded++;
                } catch (Exception e) {
                    errors++;
                    String label = getCell(row, 1);
                    rowErrors.add("Row " + (r + 1) + (label != null && !label.isBlank() ? " (" + label + ")" : "")
                            + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                }
            }
            wb.close();
        } catch (Exception e) { result.put("error", "Invalid file format. Download the template."); return result; }
        result.put("total", loaded); result.put("errors", errors);
        if (!rowErrors.isEmpty()) result.put("rowErrors", rowErrors);
        result.put("message", "Uploaded " + loaded + " products" + (errors > 0 ? " (" + errors + " errors)" : ""));
        if (!imageErrors.isEmpty()) {
            result.put("imageErrors", imageErrors);
        }
        return result;
    }

    private String slugify(String text) {
        if (text == null) return null;
        String slug = text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.substring(0, Math.min(slug.length(), 80));
    }

    private String getCell(org.apache.poi.ss.usermodel.Row row, int col) {
        org.apache.poi.ss.usermodel.Cell c = row.getCell(col);
        if (c == null) return null;
        try {
            return switch (c.getCellType()) {
                case STRING -> c.getStringCellValue().trim();
                case NUMERIC -> String.valueOf((long) c.getNumericCellValue());
                default -> c.toString().trim();
            };
        } catch (Exception e) { return c.toString().trim(); }
    }

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private Path getUploadDir() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private void downloadAndSaveImage(Long productId, String imageUrl, boolean isMain) {
        String cleanUrl = imageUrl.trim();
        try {
            java.net.URL url = new java.net.URL(cleanUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.connect();
            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                conn.disconnect();
                throw new RuntimeException("HTTP " + responseCode + " for " + cleanUrl);
            }
            String contentType = conn.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                conn.disconnect();
                throw new RuntimeException("URL does not point to an image (Content-Type: " + contentType + ")");
            }
            Path uploadDir = getUploadDir();
            Files.createDirectories(uploadDir);
            byte[] imageBytes;
            try (java.io.InputStream in = conn.getInputStream()) {
                imageBytes = in.readAllBytes();
            }
            conn.disconnect();

            String uuid = UUID.randomUUID().toString();
            String filename = uuid + ".jpg";
            String thumbFilename = uuid + "_thumb.jpg";

            BufferedImage original = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
            if (original != null) {
                BufferedImage resized = resizeImage(original, 1200);
                writeJpeg(resized, uploadDir.resolve(filename), 0.85f);
                BufferedImage thumb = createSquareThumbnail(original, 400);
                writeJpeg(thumb, uploadDir.resolve(thumbFilename), 0.8f);
            } else {
                String ext = ".jpg";
                if (contentType.contains("png")) ext = ".png";
                else if (contentType.contains("webp")) ext = ".webp";
                filename = uuid + ext;
                Files.write(uploadDir.resolve(filename), imageBytes);
            }

            ProductImage image = new ProductImage();
            Product p = new Product();
            p.setId(productId);
            image.setProduct(p);
            image.setUrl("/uploads/" + filename);
            if (original != null) {
                image.setThumbUrl("/uploads/" + thumbFilename);
            }
            image.setMain(isMain);
            productImageRepo.save(image);
        } catch (Exception e) {
            throw new RuntimeException("Failed to download image: " + e.getMessage());
        }
    }

    @Cacheable(value = "products", key = "#sellerId != null ? 'seller:' + #sellerId : 'all'")
    public List<Product> getAllProducts(Long sellerId) {
        if (sellerId != null) {
            return productRepo.findAll().stream()
                    .filter(p -> sellerId.equals(p.getSellerId()))
                    .toList();
        }
        return getAllProducts();
    }

    @CacheEvict(value = {"products", "categories"}, allEntries = true)
    public Product addProduct(Product product) {
        if (authorizationService.hasRole("SELLER")) {
            Long sellerId = authorizationService.getCurrentUserId();
            product.setSellerId(sellerId);
        }
        if (product.getSlug() == null || product.getSlug().isEmpty()) {
            if (product.getName() != null && !product.getName().isEmpty()) {
                product.setSlug(slugify(product.getName()));
            }
        }
        // A code that is not in the official master resolves to no published rate, so the
        // product would be taxed at the fallback on every sale while its invoice looked normal.
        hsnClassificationService.validate(product);
        hsnClassificationService.assertSellable(product);
        Product saved = productRepo.save(product);
        hsnClassificationService.rememberAssignment(saved, authorizationService.getCurrentUserEmail());
        return saved;
    }

    public Product getProductForAdmin(Long productId) {
        return getProductById(productId);
    }

    public void checkSellerOwnership(Long productId, Long sellerId) {
        Product p = getProductById(productId);
        if (p.getSellerId() != null && !p.getSellerId().equals(sellerId)) {
            throw new RuntimeException("Seller does not own this product");
        }
    }

    public void verifySellerOwnership(Long productId, Long sellerId) {
        Product p = getProductById(productId);
        if (p.getSellerId() != null && !p.getSellerId().equals(sellerId)) {
            throw new AccessDeniedException("Seller does not own this product");
        }
    }

    @CacheEvict(value = {"products", "categories"}, allEntries = true)
    /**
     * Classifies one product, without touching anything else about it.
     *
     * updateProduct can already do this, but only as part of a whole-product save, which means
     * correcting a mistyped HSN carries every other field along with it. Classification is its
     * own decision - often taken by someone reviewing the catalogue rather than editing it -
     * and it is worth being able to make it on its own.
     *
     * The same three guards apply as anywhere else, because a code arriving here is no more
     * trustworthy than one arriving through the product form: it has to exist in the official
     * master, the product has to end up sellable, and where the heading carries more than one
     * published rate the choice between them has to have been made.
     */
    @Transactional
    public Product assignHsnCode(Long productId, String hsnCode, Boolean prePackaged,
                                 Long gstRateSelectionId) {
        Product existing = getProductById(productId);
        if (authorizationService.hasRole("SELLER")) {
            verifySellerOwnership(productId, authorizationService.getCurrentUserId());
        }
        if (hsnCode == null || hsnCode.isBlank()) {
            throw new IllegalArgumentException(
                    "An HSN code is required. To clear a product's classification, unpublish it "
                            + "instead - a published product with no code cannot be invoiced.");
        }

        existing.setHsnCode(hsnCode);
        if (prePackaged != null) existing.setPrePackagedAndLabelled(prePackaged);
        // A rate choice belongs to the code it was made against. Carrying an old selection onto
        // a new heading would apply a rate published for the goods this product no longer is.
        existing.setGstRateSelectionId(gstRateSelectionId);

        hsnClassificationService.validate(existing);
        hsnClassificationService.validateRateSelection(existing);
        hsnClassificationService.assertSellable(existing);
        Product saved = productRepo.save(existing);
        hsnClassificationService.rememberAssignment(saved, authorizationService.getCurrentUserEmail());
        return saved;
    }

    public Product updateProduct(Long productId, Product product) {
        Product existing = getProductById(productId);
        if (authorizationService.hasRole("SELLER")) {
            Long sellerId = authorizationService.getCurrentUserId();
            verifySellerOwnership(productId, sellerId);
        }
          if (product.getName() != null) existing.setName(product.getName());
          if (product.getDescription() != null) existing.setDescription(product.getDescription());
          if (product.getPrice() != null) existing.setPrice(product.getPrice());
          if (product.getStock() != null) existing.setStock(product.getStock());
          if (product.getSellerId() != null) existing.setSellerId(product.getSellerId());
          if (product.getSku() != null) existing.setSku(product.getSku());
          if (product.getBrand() != null) existing.setBrand(product.getBrand());
          if (product.getCategory() != null) existing.setCategory(product.getCategory());
          if (product.isActive() != existing.isActive()) existing.setActive(product.isActive());
          if (product.getProductStatus() != null) existing.setProductStatus(product.getProductStatus());
          if (product.getApprovalStatus() != null) existing.setApprovalStatus(product.getApprovalStatus());
          if (product.getOfferPrice() != null) existing.setOfferPrice(product.getOfferPrice());
          // Both of these decide the GST charged, and neither was previously copied - so a
          // mistyped HSN could never be corrected and the tax stayed wrong for the product's life.
          if (product.getHsnCode() != null) existing.setHsnCode(product.getHsnCode());
          if (product.getPrePackagedAndLabelled() != null) {
              existing.setPrePackagedAndLabelled(product.getPrePackagedAndLabelled());
          }
          if (product.getGstRateSelectionId() != null) {
              existing.setGstRateSelectionId(product.getGstRateSelectionId());
          }
          hsnClassificationService.validate(existing);
          hsnClassificationService.assertSellable(existing);
          Product saved = productRepo.save(existing);
          hsnClassificationService.rememberAssignment(saved, authorizationService.getCurrentUserEmail());
          return saved;
    }

    @Transactional
    @CacheEvict(value = {"products", "categories"}, allEntries = true)
    public boolean deleteProductCascade(Long productId) {
        if (authorizationService.hasRole("SELLER")) {
            Long sellerId = authorizationService.getCurrentUserId();
            verifySellerOwnership(productId, sellerId);
        }

        // Delete only ever truly removes a product with zero order history. Any order
        // history at all - active or cancelled - keeps the product permanently, since
        // even a cancelled order is a record worth preserving. An active order blocks
        // the delete entirely (product left 100% untouched). A cancelled-only order
        // never suspends the product - it just stays/becomes active and available.
        var orderItems = orderItemRepo.findAll().stream()
                .filter(oi -> oi.getProduct() != null && productId.equals(oi.getProduct().getId()))
                .toList();
        var activeOrderItems = orderItems.stream()
                .filter(oi -> oi.getOrder() == null || !"CANCELLED".equalsIgnoreCase(oi.getOrder().getStatus()))
                .toList();
        if (!activeOrderItems.isEmpty()) {
            return false;
        }
        if (!orderItems.isEmpty()) {
            Product p = productRepo.findById(productId).orElse(null);
            if (p != null && !p.isActive()) {
                p.setActive(true);
                productRepo.save(p);
            }
            return false;
        }

        var inv = inventoryRepo.findByProduct_Id(productId);
        if (inv != null) inventoryRepo.delete(inv);
        var cartItems = cartItemRepo.findAll().stream()
                .filter(ci -> ci.getProduct() != null && productId.equals(ci.getProduct().getId()))
                .toList();
        cartItemRepo.deleteAll(cartItems);
        var wishlistItems = wishlistRepo.findAll().stream()
                .filter(wi -> wi.getProduct() != null && productId.equals(wi.getProduct().getId()))
                .toList();
        wishlistRepo.deleteAll(wishlistItems);
        var prodDiscounts = productDiscountRepo.findAll().stream()
                .filter(pd -> pd.getProduct() != null && productId.equals(pd.getProduct().getId()))
                .toList();
        productDiscountRepo.deleteAll(prodDiscounts);

        productRepo.deleteById(productId);
        return true;
    }

    /**
     * Super Admin-only override: deletes a product regardless of order history. Order
     * items that referenced it (active or cancelled) are kept - their price/quantity
     * snapshot and parent order are untouched - but their product link is cleared, so
     * the order still shows correctly with a "Product is no longer available" fallback
     * for that line instead of the order itself breaking or losing history.
     */
    @Transactional
    @CacheEvict(value = {"products", "categories"}, allEntries = true)
    public void forceDeleteProduct(Long productId) {
        var orderItems = orderItemRepo.findAll().stream()
                .filter(oi -> oi.getProduct() != null && productId.equals(oi.getProduct().getId()))
                .toList();
        for (var oi : orderItems) {
            oi.setProduct(null);
        }
        orderItemRepo.saveAll(orderItems);

        var inv = inventoryRepo.findByProduct_Id(productId);
        if (inv != null) inventoryRepo.delete(inv);
        var cartItems = cartItemRepo.findAll().stream()
                .filter(ci -> ci.getProduct() != null && productId.equals(ci.getProduct().getId()))
                .toList();
        cartItemRepo.deleteAll(cartItems);
        var wishlistItems = wishlistRepo.findAll().stream()
                .filter(wi -> wi.getProduct() != null && productId.equals(wi.getProduct().getId()))
                .toList();
        wishlistRepo.deleteAll(wishlistItems);
        var prodDiscounts = productDiscountRepo.findAll().stream()
                .filter(pd -> pd.getProduct() != null && productId.equals(pd.getProduct().getId()))
                .toList();
        productDiscountRepo.deleteAll(prodDiscounts);

        productRepo.deleteById(productId);
    }

    @CacheEvict(value = {"products", "categories"}, allEntries = true)
    public Product suspendProduct(Long productId) {
        Product p = getProductById(productId);
        p.setActive(false);
        return productRepo.save(p);
    }

    @CacheEvict(value = {"products", "categories"}, allEntries = true)
    public Product updateStock(Long productId, Integer stock) {
        Product p = getProductById(productId);
        p.setStock(stock);
        return productRepo.save(p);
    }

    public List<ProductVariant> getVariants(Long productId) {
        Product p = getProductById(productId);
        return p.getVariants();
    }

    @Transactional
    public ProductVariant addVariant(Long productId, ProductVariant variant) {
        Product p = getProductById(productId);
        variant.setProduct(p);
        return variantRepo.save(variant);
    }

    @Transactional
    public ProductVariant updateVariant(Long productId, Long variantId, ProductVariant variant) {
        ProductVariant existing = variantRepo.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Variant not found"));
        if (variant.getVariantType() != null) existing.setVariantType(variant.getVariantType());
        if (variant.getVariantValue() != null) existing.setVariantValue(variant.getVariantValue());
        if (variant.getPrice() != null) existing.setPrice(variant.getPrice());
        if (variant.getStock() != null) existing.setStock(variant.getStock());
        if (variant.getSku() != null) existing.setSku(variant.getSku());
        return variantRepo.save(existing);
    }

    @Transactional
    public void deleteVariant(Long productId, Long variantId) {
        ProductVariant variant = variantRepo.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Variant not found"));
        variantRepo.delete(variant);
    }

    public List<ProductImage> getImages(Long productId) {
        Product p = getProductById(productId);
        return p.getImages();
    }

    // Persists a set of processed image URLs produced by the Node image-service
    // (POST /upload-image -> sharp WebP sizes). url/previewUrl carry the 600px
    // preview (the primary display image), thumbUrl the 150px thumbnail, and
    // zoomUrl/originalUrl the larger sizes used by the product detail view.
    @CacheEvict(value = "products", allEntries = true)
    public ProductImage attachImageUrls(Long productId, String previewUrl, String thumbUrl,
                                        String zoomUrl, String originalUrl) {
        Product p = getProductById(productId);
        for (String candidate : new String[]{previewUrl, thumbUrl, zoomUrl, originalUrl}) {
            if (candidate == null || candidate.isBlank()) continue;
            // Only accept server-relative upload paths; reject traversal and absolute URLs.
            if (!candidate.startsWith("/uploads/") || candidate.contains("..")) {
                throw new IllegalArgumentException("Invalid image URL: " + candidate);
            }
        }
        if (previewUrl == null || previewUrl.isBlank()) {
            throw new IllegalArgumentException("preview_url is required");
        }
        int nextOrder = (int) productImageRepo.countByProduct_Id(productId);
        ProductImage image = new ProductImage();
        image.setProduct(p);
        image.setUrl(previewUrl);
        image.setThumbUrl(thumbUrl);
        image.setPreviewUrl(previewUrl);
        image.setZoomUrl(zoomUrl);
        image.setOriginalUrl(originalUrl);
        image.setSortOrder(nextOrder);
        if (p.getImages().isEmpty()) {
            image.setMain(true);
        }
        return productImageRepo.save(image);
    }

    public ProductImage uploadImage(Long productId, MultipartFile file) {
        Product p = getProductById(productId);
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Only image files are allowed");
        }
        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }
        String uuid = UUID.randomUUID().toString();
        String filename = uuid + ".jpg";
        String thumbFilename = uuid + "_thumb.jpg";
        boolean hasResize = false;
        try {
            Path uploadDir = getUploadDir();
            Files.createDirectories(uploadDir);
            BufferedImage original = ImageIO.read(file.getInputStream());
            if (original != null) {
                hasResize = true;
                BufferedImage resized = resizeImage(original, 1200);
                writeJpeg(resized, uploadDir.resolve(filename), 0.85f);
                BufferedImage thumb = createSquareThumbnail(original, 400);
                writeJpeg(thumb, uploadDir.resolve(thumbFilename), 0.8f);
            } else {
                String ext = ".jpg";
                String origName = file.getOriginalFilename();
                if (origName != null && origName.contains(".")) {
                    ext = origName.substring(origName.lastIndexOf("."));
                }
                filename = uuid + ext;
                Path target = uploadDir.resolve(filename);
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save image file: " + e.getMessage());
        }
        int nextOrder = (int) productImageRepo.countByProduct_Id(productId);
        ProductImage image = new ProductImage();
        image.setProduct(p);
        image.setUrl("/uploads/" + filename);
        if (hasResize) {
            image.setThumbUrl("/uploads/" + thumbFilename);
        }
        image.setSortOrder(nextOrder);
        if (p.getImages().isEmpty()) {
            image.setMain(true);
        }
        return productImageRepo.save(image);
    }

    private BufferedImage resizeImage(BufferedImage img, int maxDim) {
        int w = img.getWidth(), h = img.getHeight();
        if (w <= maxDim && h <= maxDim) return img;
        double scale = Math.min((double) maxDim / w, (double) maxDim / h);
        int nw = (int) (w * scale), nh = (int) (h * scale);
        BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(img, 0, 0, nw, nh, null);
        g.dispose();
        return resized;
    }

    private BufferedImage createSquareThumbnail(BufferedImage img, int size) {
        int w = img.getWidth(), h = img.getHeight();
        int side = Math.min(w, h);
        int x = (w - side) / 2, y = (h - side) / 2;
        BufferedImage cropped = img.getSubimage(x, y, side, side);
        BufferedImage thumb = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(cropped, 0, 0, size, size, null);
        g.dispose();
        return thumb;
    }

    private void writeJpeg(BufferedImage img, Path target, float quality) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        }
        writer.dispose();
    }

    @Transactional
    public void reorderImages(Long productId, List<Long> imageIds) {
        Product p = getProductById(productId);
        Map<Long, ProductImage> map = new HashMap<>();
        for (ProductImage img : p.getImages()) {
            map.put(img.getId(), img);
        }
        for (int i = 0; i < imageIds.size(); i++) {
            ProductImage img = map.get(imageIds.get(i));
            if (img != null) {
                img.setSortOrder(i);
                productImageRepo.save(img);
            }
        }
    }

    @Transactional
    public ProductImage setMainImage(Long productId, Long imageId) {
        Product p = getProductById(productId);
        for (ProductImage img : p.getImages()) {
            img.setMain(img.getId().equals(imageId));
            productImageRepo.save(img);
        }
        return productImageRepo.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Image not found"));
    }

    @Transactional
    public void deleteImage(Long productId, Long imageId) {
        ProductImage img = productImageRepo.findById(imageId).orElse(null);
        if (img != null) {
            Product p = getProductById(productId);
            boolean wasMain = img.isMain();
            String url = img.getUrl();
            if (url != null && url.startsWith("/uploads/")) {
                try {
                    String name = url.substring("/uploads/".length());
                    Path file = getUploadDir().resolve(name);
                    Files.deleteIfExists(file);
                    int dot = name.lastIndexOf(".");
                    if (dot > 0) {
                        Path thumb = getUploadDir().resolve(name.substring(0, dot) + "_thumb.jpg");
                        Files.deleteIfExists(thumb);
                    }
                } catch (Exception ignored) {}
            }
            p.getImages().remove(img);
            productImageRepo.delete(img);
            if (wasMain && p.getImages().size() > 0) {
                p.getImages().get(0).setMain(true);
                productImageRepo.save(p.getImages().get(0));
            }
        }
    }

    @Transactional
    public void backfillThumbnails() {
        List<ProductImage> images = productImageRepo.findAll();
        for (ProductImage img : images) {
            String url = img.getUrl();
            if (url == null || !url.startsWith("/uploads/")) continue;
            String name = url.substring("/uploads/".length());
            int dot = name.lastIndexOf(".");
            if (dot <= 0) continue;
            String thumbName = name.substring(0, dot) + "_thumb.jpg";
            Path uploadDir = getUploadDir();
            try {
                Path thumbPath = uploadDir.resolve(thumbName);
                if (Files.exists(thumbPath)) {
                    if (img.getThumbUrl() == null) {
                        img.setThumbUrl("/uploads/" + thumbName);
                        productImageRepo.save(img);
                    }
                    continue;
                }
                Path mainPath = uploadDir.resolve(name);
                if (!Files.exists(mainPath)) continue;
                BufferedImage original = ImageIO.read(mainPath.toFile());
                if (original == null) continue;
                BufferedImage thumb = createSquareThumbnail(original, 400);
                writeJpeg(thumb, thumbPath, 0.8f);
                img.setThumbUrl("/uploads/" + thumbName);
                productImageRepo.save(img);
            } catch (Exception ignored) {}
        }
    }

    public List<Discount> getDiscounts(Long productId) {
        Product p = getProductById(productId);
        return p.getDiscounts();
    }

    public Discount addDiscount(Long productId, Discount discount) {
        Product p = getProductById(productId);
        discount.setProduct(p);
        return discountRepo.save(discount);
    }

    public Discount getDiscount(Long productId, Long discountId) {
        return discountRepo.findById(discountId).orElseThrow();
    }

    public Discount updateDiscount(Long productId, Long discountId, Discount discount) {
        Discount existing = discountRepo.findById(discountId).orElseThrow();
        if (discount.getType() != null) existing.setType(discount.getType());
        if (discount.getValue() != null) existing.setValue(discount.getValue());
        if (discount.getStartDate() != null) existing.setStartDate(discount.getStartDate());
        if (discount.getEndDate() != null) existing.setEndDate(discount.getEndDate());
        existing.setActive(discount.isActive());
        return discountRepo.save(existing);
    }

    public void deleteDiscount(Long productId, Long discountId) {
        discountRepo.deleteById(discountId);
    }

    @CacheEvict(value = {"products", "categories"}, allEntries = true)
    @Transactional
    public Product cloneProduct(Long productId) {
        Product original = getProductById(productId);
        Product clone = new Product();
        clone.setName(original.getName() + " (Copy)");
        clone.setDescription(original.getDescription());
        clone.setPrice(original.getPrice());
        clone.setStock(original.getStock());
        clone.setActive(true);
        clone.setBrand(original.getBrand());
        clone.setCategory(original.getCategory());
        clone.setSellerId(original.getSellerId());
        clone.setProductStatus("draft");
        return productRepo.save(clone);
    }

    @CacheEvict(value = {"products", "categories"}, allEntries = true)
    @Transactional
    public Map<String, Object> bulkEdit(Map<String, Object> body, Long sellerId) {
        @SuppressWarnings("unchecked")
        List<Integer> idsRaw = (List<Integer>) body.get("productIds");
        List<Long> ids = idsRaw.stream().map(Long::valueOf).toList();
        int updated = 0;
        for (Long id : ids) {
            try {
                if (sellerId != null) verifySellerOwnership(id, sellerId);
                Product p = getProductById(id);
                if (body.containsKey("price")) p.setPrice(Double.valueOf(body.get("price").toString()));
                if (body.containsKey("stock")) p.setStock(Integer.valueOf(body.get("stock").toString()));
                if (body.containsKey("active")) p.setActive(Boolean.valueOf(body.get("active").toString()));
                if (body.containsKey("brand")) p.setBrand((String) body.get("brand"));
                productRepo.save(p);
                updated++;
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", updated);
        result.put("total", ids.size());
        return result;
    }

    public List<Product> getSimilarProducts(Long productId) {
        return Collections.emptyList();
    }

    public List<Product> getFeaturedProducts() {
        return getActiveProducts().stream().limit(10).toList();
    }

    public Map<String, Object> addDiscount(Long productId, Map<String, Object> discount) {
        Product p = getProductById(productId);
        Discount d = new Discount();
        d.setProduct(p);
        d.setType((String) discount.getOrDefault("type", "PERCENTAGE"));
        d.setValue(Double.valueOf(discount.get("value").toString()));
        if (discount.containsKey("startDate")) d.setStartDate(LocalDate.parse((String) discount.get("startDate")));
        if (discount.containsKey("endDate")) d.setEndDate(LocalDate.parse((String) discount.get("endDate")));
        d.setActive((Boolean) discount.getOrDefault("active", true));
        Discount saved = discountRepo.save(d);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("type", saved.getType());
        result.put("value", saved.getValue());
        result.put("active", saved.isActive());
        return result;
    }

    @CacheEvict(value = {"products", "categories"}, allEntries = true)
    public Product updateActiveStatus(Long productId, Boolean active) {
        Product p = getProductById(productId);
        p.setActive(active);
        return productRepo.save(p);
    }

}
