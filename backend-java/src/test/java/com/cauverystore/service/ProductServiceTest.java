package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.exception.AccessDeniedException;
import com.cauverystore.exception.ProductNotFoundException;
import com.cauverystore.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepo;
    @Mock private CategoryRepository catRepo;
    @Mock private ProductImageRepository productImageRepo;
    @Mock private DiscountRepository discountRepo;
    @Mock private AuthorizationService authorizationService;
    @Mock private CartItemRepository cartItemRepo;
    @Mock private WishlistRepository wishlistRepo;
    @Mock private InventoryRepository inventoryRepo;
    @Mock private ProductDiscountRepository productDiscountRepo;
    @Mock private OrderItemRepository orderItemRepo;
    @Mock private HsnClassificationService hsnClassificationService;

    @InjectMocks
    private ProductService productService;

    private Product product;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setPrice(100.0);
        product.setStock(50);
        product.setActive(true);
        product.setSellerId(1L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(Long userId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(userId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void getActiveProducts_shouldReturnAll_whenNotSeller() {
        when(productRepo.findByActiveTrue()).thenReturn(List.of(product));

        List<Product> result = productService.getActiveProducts();

        assertEquals(1, result.size());
        assertEquals("Test Product", result.get(0).getName());
    }

    @Test
    void getActiveProducts_shouldReturnFullCatalog_evenWhenAuthenticatedAsSeller() {
        // Public storefront browsing must never filter by the viewer's own role - a seller
        // shopping as a customer sees the same catalog as everyone else. "My own products"
        // is served exclusively by the seller/admin management endpoints, not this one.
        Product own = new Product(); own.setId(1L); own.setActive(true); own.setSellerId(1L);
        Product other = new Product(); other.setId(2L); other.setActive(true); other.setSellerId(2L);
        when(productRepo.findByActiveTrue()).thenReturn(List.of(own, other));

        List<Product> result = productService.getActiveProducts();

        assertEquals(2, result.size());
    }

    @Test
    void getProductById_shouldReturnProduct_whenExists() {
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        Product result = productService.getProductById(1L);

        assertEquals("Test Product", result.getName());
    }

    @Test
    void getProductById_shouldThrow_whenNotFound() {
        when(productRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> productService.getProductById(999L));
    }

    @Test
    void getProductById_shouldReturnProduct_regardlessOfViewerRole() {
        // Public storefront browsing - viewing any active product's detail page must never be
        // gated by seller ownership; that check belongs to the write-path management methods.
        product.setSellerId(1L);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        Product result = productService.getProductById(1L);

        assertEquals(1L, result.getId());
    }

    @Test
    void addProduct_shouldSetSellerId_whenSeller() {
        setAuthenticatedUser(1L, "SELLER");
        Product newProduct = new Product();
        newProduct.setName("New Product");
        newProduct.setPrice(50.0);
        when(authorizationService.hasRole("SELLER")).thenReturn(true);
        when(authorizationService.getCurrentUserId()).thenReturn(1L);
        when(productRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.addProduct(newProduct);

        assertEquals(1L, result.getSellerId());
    }

    @Test
    void addProduct_shouldNotSetSellerId_whenAdmin() {
        setAuthenticatedUser(1L, "ADMIN");
        Product newProduct = new Product();
        newProduct.setName("New Product");
        when(productRepo.save(any())).thenReturn(newProduct);

        Product result = productService.addProduct(newProduct);

        assertNull(result.getSellerId());
    }

    @Test
    void updateProduct_shouldUpdateFields() {
        Product update = new Product();
        update.setName("Updated Name");
        update.setPrice(200.0);
        update.setStock(100);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(any())).thenReturn(product);

        Product result = productService.updateProduct(1L, update);

        assertEquals("Updated Name", result.getName());
        assertEquals(200.0, result.getPrice());
        assertEquals(100, result.getStock());
    }

    @Test
    void updateProduct_shouldAllowCorrectingTheFieldsThatDecideGst() {
        // Neither of these used to be copied, so a mistyped HSN was permanent and the product
        // kept being taxed at the wrong rate for its whole life.
        product.setHsnCode("8471");
        product.setPrePackagedAndLabelled(true);
        Product update = new Product();
        update.setHsnCode("1006");
        update.setPrePackagedAndLabelled(false);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Product result = productService.updateProduct(1L, update);

        assertEquals("1006", result.getHsnCode());
        assertEquals(Boolean.FALSE, result.getPrePackagedAndLabelled());
    }

    @Test
    void updateProduct_shouldDropTheRateChoice_whenTheHsnItBelongedToChanges() {
        // A rate line is published against one HSN. Correcting the code leaves the old choice
        // describing goods this product no longer is, and validation rejects the save quoting a
        // line the editor never picked. There is no way out of that by hand either - a null in
        // the payload means "unchanged", so the stale id cannot be cleared.
        product.setHsnCode("0901");
        product.setGstRateSelectionId(77L);
        Product update = new Product();
        update.setHsnCode("1006");
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Product result = productService.updateProduct(1L, update);

        assertEquals("1006", result.getHsnCode());
        assertNull(result.getGstRateSelectionId());
    }

    @Test
    void updateProduct_shouldKeepANewRateChoiceMadeAlongsideTheNewHsn() {
        // Changing both at once is the ordinary path through the edit form: pick the code, then
        // answer the question it raises. The incoming choice must survive the clearing above.
        product.setHsnCode("0901");
        product.setGstRateSelectionId(77L);
        Product update = new Product();
        update.setHsnCode("1006");
        update.setGstRateSelectionId(91L);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Product result = productService.updateProduct(1L, update);

        assertEquals(91L, result.getGstRateSelectionId());
    }

    @Test
    void updateProduct_shouldKeepTheRateChoice_whenTheHsnIsResubmittedUnchanged() {
        // The edit form posts the HSN on every save, so an unrelated edit must not be read as a
        // reclassification and throw away an answer somebody already gave.
        product.setHsnCode("1006");
        product.setGstRateSelectionId(77L);
        Product update = new Product();
        update.setHsnCode("1006");
        update.setPrice(250.0);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Product result = productService.updateProduct(1L, update);

        assertEquals(77L, result.getGstRateSelectionId());
    }

    @Test
    void updateProduct_shouldLeaveGstFieldsAlone_whenTheUpdateOmitsThem() {
        // A partial update of, say, the price must not silently reset the packaging flag to
        // null and knock the product onto the fallback rate.
        product.setHsnCode("1006");
        product.setPrePackagedAndLabelled(true);
        Product update = new Product();
        update.setPrice(250.0);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Product result = productService.updateProduct(1L, update);

        assertEquals("1006", result.getHsnCode());
        assertEquals(Boolean.TRUE, result.getPrePackagedAndLabelled());
    }

    @Test
    void updateProduct_shouldEnforceOwnership_whenSeller() {
        setAuthenticatedUser(2L, "SELLER");
        product.setSellerId(1L);
        Product update = new Product();
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(authorizationService.hasRole("SELLER")).thenReturn(true);
        when(authorizationService.getCurrentUserId()).thenReturn(2L);

        assertThrows(AccessDeniedException.class, () -> productService.updateProduct(1L, update));
    }

    @Test
    void deleteProductCascade_shouldLeaveProductUntouched_whenActiveOrderExists() {
        Order activeOrder = new Order();
        activeOrder.setStatus("PLACED");
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setOrder(activeOrder);
        when(orderItemRepo.findAll()).thenReturn(List.of(item));

        boolean result = productService.deleteProductCascade(1L);

        assertFalse(result);
        assertTrue(product.isActive());
        verify(productRepo, never()).save(any());
        verify(productRepo, never()).deleteById(any());
        verify(inventoryRepo, never()).delete(any());
        verify(cartItemRepo, never()).deleteAll(anyList());
        verify(wishlistRepo, never()).deleteAll(anyList());
        verify(productDiscountRepo, never()).deleteAll(anyList());
    }

    @Test
    void deleteProductCascade_shouldStayActive_whenOnlyCancelledOrdersExist() {
        Order cancelledOrder = new Order();
        cancelledOrder.setStatus("CANCELLED");
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setOrder(cancelledOrder);
        when(orderItemRepo.findAll()).thenReturn(List.of(item));

        boolean result = productService.deleteProductCascade(1L);

        assertFalse(result);
        verify(productRepo, never()).deleteById(any());
        verify(orderItemRepo, never()).deleteAll(anyList());
    }

    @Test
    void deleteProductCascade_shouldReactivate_whenSuspendedButOnlyCancelledOrdersExist() {
        product.setActive(false);
        Order cancelledOrder = new Order();
        cancelledOrder.setStatus("CANCELLED");
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setOrder(cancelledOrder);
        when(orderItemRepo.findAll()).thenReturn(List.of(item));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        boolean result = productService.deleteProductCascade(1L);

        assertFalse(result);
        assertTrue(product.isActive());
        verify(productRepo).save(product);
        verify(productRepo, never()).deleteById(any());
    }

    @Test
    void deleteProductCascade_shouldDelete_whenNoOrderHistoryAtAll() {
        boolean result = productService.deleteProductCascade(1L);

        assertTrue(result);
        verify(productRepo).deleteById(1L);
    }

    @Test
    void deleteProductCascade_shouldEnforceOwnership_whenSeller() {
        setAuthenticatedUser(2L, "SELLER");
        product.setSellerId(1L);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(authorizationService.hasRole("SELLER")).thenReturn(true);
        when(authorizationService.getCurrentUserId()).thenReturn(2L);

        assertThrows(AccessDeniedException.class, () -> productService.deleteProductCascade(1L));
        verify(productRepo, never()).deleteById(any());
    }

    @Test
    void getDiscountedPrice_shouldReturnBestDiscount() {
        Discount d1 = new Discount();
        d1.setType("PERCENTAGE"); d1.setValue(10.0); d1.setActive(true);
        d1.setStartDate(LocalDate.now().minusDays(1));
        d1.setEndDate(LocalDate.now().plusDays(1));

        Discount d2 = new Discount();
        d2.setType("FLAT"); d2.setValue(15.0); d2.setActive(true);
        d2.setStartDate(LocalDate.now().minusDays(1));
        d2.setEndDate(LocalDate.now().plusDays(1));

        product.setDiscounts(List.of(d1, d2));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        double price = productService.getDiscountedPriceDouble(1L);

        assertEquals(85.0, price, 0.01);
    }

    @Test
    void getDiscountedPrice_shouldReturnOriginalPrice_whenNoDiscounts() {
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        double price = productService.getDiscountedPriceDouble(1L);

        assertEquals(100.0, price, 0.01);
    }

    @Test
    void suspendProduct_shouldSetActiveFalse() {
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(any())).thenReturn(product);

        Product result = productService.suspendProduct(1L);

        assertFalse(result.isActive());
    }

    @Test
    void updateStock_shouldUpdateStock() {
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(any())).thenReturn(product);

        Product result = productService.updateStock(1L, 25);

        assertEquals(25, result.getStock());
    }

    @Test
    void updateActiveStatus_shouldToggleActive() {
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(any())).thenReturn(product);

        Product result = productService.updateActiveStatus(1L, false);

        assertFalse(result.isActive());
    }

    @Test
    void addDiscount_shouldSaveDiscount() {
        Discount discount = new Discount();
        discount.setType("PERCENTAGE");
        discount.setValue(20.0);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(discountRepo.save(any())).thenReturn(discount);

        Discount result = productService.addDiscount(1L, discount);

        assertEquals(20.0, result.getValue());
        verify(discountRepo).save(discount);
    }

    @Test
    void getSimilarProducts_shouldReturnEmptyList() {
        List<Product> result = productService.getSimilarProducts(1L);
        assertTrue(result.isEmpty());
    }

    @Test
    void getFeaturedProducts_shouldReturnAtMost10() {
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            Product p = new Product();
            p.setId((long) i);
            p.setActive(true);
            products.add(p);
        }
        when(productRepo.findByActiveTrue()).thenReturn(products);

        List<Product> result = productService.getFeaturedProducts();

        assertEquals(10, result.size());
    }

    private MultipartFile sheetWith(String name, String hsnCode) throws Exception {
        return sheetWith(name, hsnCode, null);
    }

    private MultipartFile sheetWithPackaging(String name, String hsnCode, String packaged) throws Exception {
        return sheetWith(name, hsnCode, packaged);
    }

    private MultipartFile sheetWith(String name, String hsnCode, String packaged) throws Exception {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("header");
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
            row.createCell(1).setCellValue(name);
            row.createCell(4).setCellValue("100");
            row.createCell(5).setCellValue("5");
            row.createCell(10).setCellValue(hsnCode);
            if (packaged != null) row.createCell(27).setCellValue(packaged);
            wb.write(out);
            return new org.springframework.mock.web.MockMultipartFile(
                    "file", "products.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    @Test
    void bulkUpload_shouldRejectARowWhoseHsnIsNotInTheOfficialMaster() throws Exception {
        // The spreadsheet used to be the easiest way to get a bad code into the catalogue: it
        // never called addProduct, so it skipped validation entirely - and bulk rows publish
        // straight away, so the bad code reached real sales.
        doThrow(new HsnClassificationService.UnknownHsnException("'123' is not an HSN code"))
                .when(hsnClassificationService).validate(any());

        Map<String, Object> result = productService.bulkUpload(sheetWith("Rice", "123"), 1L);

        assertEquals(0, result.get("total"));
        assertEquals(1, result.get("errors"));
        verify(productRepo, never()).save(any());
    }

    @Test
    void bulkUpload_shouldSayWhichRowFailedAndWhy() throws Exception {
        // A bare error count leaves a seller with a spreadsheet they cannot correct.
        doThrow(new HsnClassificationService.UnknownHsnException("'123' is not an HSN code"))
                .when(hsnClassificationService).validate(any());

        Map<String, Object> result = productService.bulkUpload(sheetWith("Rice", "123"), 1L);

        @SuppressWarnings("unchecked")
        List<String> rowErrors = (List<String>) result.get("rowErrors");
        assertNotNull(rowErrors, "the reasons have to come back, not just the count");
        assertEquals(1, rowErrors.size());
        assertTrue(rowErrors.get(0).contains("Row 2"), "should point at the offending row");
        assertTrue(rowErrors.get(0).contains("Rice"), "should name the product");
        assertTrue(rowErrors.get(0).contains("123"), "should quote the rejected code");
    }

    @Test
    void bulkUpload_shouldRememberTheAssignmentForAcceptedRows() throws Exception {
        when(productRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> result = productService.bulkUpload(sheetWith("Rice", "1006"), 1L);

        assertEquals(1, result.get("total"));
        verify(hsnClassificationService).rememberAssignment(any(), any());
    }

    @Test
    void bulkUpload_shouldTakePrePackagedFromTheTemplate() throws Exception {
        // Rice is 5% pre-packaged and nil loose, so the seller's spreadsheet answer decides the rate.
        when(productRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> result = productService.bulkUpload(sheetWithPackaging("Rice", "1006", "No"), 1L);

        assertEquals(1, result.get("total"));
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepo).save(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getPrePackagedAndLabelled());
    }

    @Test
    void bulkUpload_shouldRejectAPrePackagedColumnThatIsNotYesOrNo() throws Exception {
        Map<String, Object> result = productService.bulkUpload(sheetWithPackaging("Rice", "1006", "Maybe"), 1L);

        assertEquals(0, result.get("total"));
        assertEquals(1, result.get("errors"));
        @SuppressWarnings("unchecked")
        List<String> rowErrors = (List<String>) result.get("rowErrors");
        assertNotNull(rowErrors);
        assertTrue(rowErrors.get(0).contains("Yes or No"), "should explain the bad value");
    }

    @Test
    void updatingAnUntaxableProduct_shouldTakeItOffSaleRatherThanRefuseTheEdit() {
        // The trap: assertSellable threw on every save, so a seller could not correct the
        // price, could not zero the stock, and could not unpublish - and the product stayed
        // published and untaxable, which is the outcome the guard exists to prevent.
        Product existing = new Product();
        existing.setId(9L);
        existing.setName("Unclassified goods");
        existing.setProductStatus("published");
        existing.setPrice(100.0);
        when(productRepo.findById(9L)).thenReturn(java.util.Optional.of(existing));
        when(productRepo.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        when(authorizationService.hasRole("SELLER")).thenReturn(false);
        doThrow(new HsnClassificationService.UnclassifiedProductException(
                        "no rate can be determined for it"))
                .when(hsnClassificationService).assertSellable(any());

        Product update = new Product();
        update.setStock(0);

        Product saved = assertDoesNotThrow(() -> productService.updateProduct(9L, update));

        assertEquals("draft", saved.getProductStatus(),
                "an untaxable product must come off sale, not block the seller");
        verify(productRepo).save(existing);
    }

    @Test
    void updatingATaxableProduct_shouldLeaveItPublished() {
        Product existing = new Product();
        existing.setId(9L);
        existing.setName("Cotton Lungi");
        existing.setProductStatus("published");
        existing.setPrice(450.0);
        when(productRepo.findById(9L)).thenReturn(java.util.Optional.of(existing));
        when(productRepo.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        when(authorizationService.hasRole("SELLER")).thenReturn(false);

        Product update = new Product();
        update.setStock(5);

        Product saved = productService.updateProduct(9L, update);

        assertEquals("published", saved.getProductStatus(),
                "nothing is wrong with this one; it must stay on sale");
    }
}
