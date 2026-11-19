package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.ProductCompliance;
import com.cauverystore.entities.ProductWorkflowStatus;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ComplianceBulkServiceTest {

    private static Product product(long id, double price) {
        Product p = new Product();
        p.setId(id);
        p.setName("Egg Storage Box");
        p.setPrice(price);
        p.setApprovalStatus("pending");   // held back, as a bulk-uploaded product is
        return p;
    }

    /** The downloaded sheet, with some cells typed in, as bytes to upload. */
    private static byte[] filledSheet(java.util.function.Consumer<Sheet> fill) throws Exception {
        byte[] template = ComplianceBulkService.write(List.<Object[]>of(
                ComplianceBulkService.rowFor(product(101, 289), null, "MRP, Net quantity"),
                ComplianceBulkService.rowFor(product(102, 189), null, "MRP")));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(template)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill.accept(wb.getSheetAt(0));
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void set(Sheet s, int row, String header, Object value) {
        int col = ComplianceBulkService.HEADERS.indexOf(header);
        Row r = s.getRow(row);
        if (value instanceof Number n) r.createCell(col).setCellValue(n.doubleValue());
        else r.createCell(col).setCellValue(String.valueOf(value));
    }

    @Test
    void downloadedSheetReadsBackWithTheTypedDetails() throws Exception {
        byte[] file = filledSheet(s -> {
            set(s, 1, ComplianceBulkService.MRP, 599);
            set(s, 1, ComplianceBulkService.SKU, "CS-HK-0001");
            set(s, 1, ComplianceBulkService.NET, 2);
            set(s, 1, ComplianceBulkService.UNIT, "pcs");
            set(s, 1, ComplianceBulkService.GROSS, 750);
            set(s, 1, ComplianceBulkService.MFR, "Example Plastics");
            set(s, 1, ComplianceBulkService.MFR_ADDR, "Rajkot, Gujarat");
            set(s, 1, ComplianceBulkService.PACKER_SAME, "Yes");
            set(s, 1, ComplianceBulkService.CARE_PHONE, 9876543210L);
        });
        List<Object> rows = ComplianceBulkService.parse(new ByteArrayInputStream(file));
        assertEquals(2, rows.size());
        ComplianceBulkService.Changes c = ((ComplianceBulkService.RowOf) rows.get(0)).changes();
        assertEquals(101L, c.productId());
        assertEquals(599.0, c.mrp());
        assertEquals("CS-HK-0001", c.sku());
        assertEquals(0, new BigDecimal("2").compareTo(c.net()));
        assertEquals("N (number)", c.unit());
        assertEquals("Example Plastics", c.manufacturer());
        assertEquals(Boolean.TRUE, c.packerSame());
        assertEquals("9876543210", c.carePhone());
        // The second row was left empty: nothing to change.
        assertTrue(((ComplianceBulkService.RowOf) rows.get(1)).changes().empty());
    }

    @Test
    void badCellsBecomeRowErrorsWithoutStoppingOtherRows() throws Exception {
        byte[] file = filledSheet(s -> {
            set(s, 1, ComplianceBulkService.UNIT, "bucket");
            set(s, 2, ComplianceBulkService.MRP, "abc");
        });
        List<Object> rows = ComplianceBulkService.parse(new ByteArrayInputStream(file));
        assertTrue(((ComplianceBulkService.RowError) rows.get(0)).message().contains("Unit"));
        assertTrue(((ComplianceBulkService.RowError) rows.get(1)).message().contains("MRP (Rs) must be a number"));
    }

    @Test
    void aSheetWithoutTheIdColumnIsRefused() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.createSheet().createRow(0).createCell(0).setCellValue("Product Name");
            wb.write(out);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ComplianceBulkService.parse(new ByteArrayInputStream(out.toByteArray())));
            assertTrue(e.getMessage().contains("not the compliance sheet"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> ComplianceBulkService.parse(new ByteArrayInputStream("not excel".getBytes())));
    }

    @Test
    void unitsAreStoredAsTheComplianceFormStoresThem() {
        assertEquals("N (number)", ComplianceBulkService.unitOf("Nos"));
        assertEquals("ml", ComplianceBulkService.unitOf("ML"));
        assertEquals("l", ComplianceBulkService.unitOf("litre"));
        assertEquals("pair", ComplianceBulkService.unitOf("Pairs"));
        assertThrows(IllegalArgumentException.class, () -> ComplianceBulkService.unitOf("box"));
    }

    @Test
    void emptyCellsNeverClearExistingDetails() {
        ProductCompliance c = new ProductCompliance();
        c.setManufacturerName("Kept Ltd");
        c.setNetQuantity(new BigDecimal("4"));
        ComplianceBulkService.apply(new ComplianceBulkService.Changes(1L, null, null, "set", null, null,
                "New address", null, null, null, null, null, null, null, null, null, null, null), c);
        assertEquals("Kept Ltd", c.getManufacturerName());
        assertEquals(0, new BigDecimal("4").compareTo(c.getNetQuantity()));
        assertEquals("set", c.getQuantityUnit());
        assertEquals("New address", c.getManufacturerAddress());
    }

    // ------------------------------------------------------------- saving and publishing

    private record Setup(ComplianceBulkService service, com.cauverystore.repository.ProductRepository products,
                         ProductComplianceService engine, ProductComplianceWorkflowService workflow,
                         ProductService productService) {}

    private static Setup setup() {
        var products = mock(com.cauverystore.repository.ProductRepository.class);
        var engine = mock(ProductComplianceService.class);
        var workflow = mock(ProductComplianceWorkflowService.class);
        var productService = mock(ProductService.class);
        var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        var service = new ComplianceBulkService(products, mock(com.cauverystore.repository.ProductComplianceRepository.class),
                engine, workflow, productService, validator, mock(PlatformTransactionManager.class));
        return new Setup(service, products, engine, workflow, productService);
    }

    private static ProductComplianceService.Result result(boolean publishable) {
        return new ProductComplianceService.Result(101L, "GENERAL", publishable, publishable ? 100 : 60,
                publishable ? "PUBLISHABLE" : "BLOCKED", publishable, List.of(), List.of(), 30, null);
    }

    private static ComplianceBulkService.Changes changes(Double mrp) {
        return new ComplianceBulkService.Changes(101L, mrp, new BigDecimal("1"), "N (number)", new BigDecimal("300"),
                "Maker", "Address", true, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void mrpBelowTheSellingPriceIsRefused() {
        Setup s = setup();
        when(s.products().findById(101L)).thenReturn(Optional.of(product(101, 289)));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> s.service().importRow(changes(199.0), false));
        assertTrue(e.getMessage().contains("below the selling price"));
        verify(s.productService(), never()).updateProduct(anyLong(), any());
    }

    @Test
    void anSkuAnotherProductUsesIsRefused() {
        Setup s = setup();
        when(s.products().findById(101L)).thenReturn(Optional.of(product(101, 289)));
        when(s.products().existsBySkuIgnoreCaseAndIdNot("CS-1", 101L)).thenReturn(true);
        var ch = new ComplianceBulkService.Changes(101L, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, "CS-1");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> s.service().importRow(ch, false));
        assertTrue(e.getMessage().contains("already used"));
        verify(s.productService(), never()).updateProduct(anyLong(), any());
    }

    @Test
    void aNewSkuIsSavedOnTheProduct() {
        Setup s = setup();
        Product p = product(101, 289);
        when(s.products().findById(101L)).thenReturn(Optional.of(p));
        when(s.productService().updateProduct(eq(101L), any())).thenReturn(p);
        when(s.engine().check(101L)).thenReturn(result(false));
        var ch = new ComplianceBulkService.Changes(101L, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, "CS-2");
        s.service().importRow(ch, false);
        verify(s.productService()).updateProduct(eq(101L), argThat(x -> "CS-2".equals(x.getSku()) && x.getMrp() == null));
    }

    @Test
    void aPassingRowIsTakenThroughEveryStepToPublished() {
        Setup s = setup();
        Product p = product(101, 289);
        when(s.products().findById(101L)).thenReturn(Optional.of(p));
        when(s.productService().updateProduct(eq(101L), any())).thenReturn(p);
        ProductCompliance record = new ProductCompliance();
        when(s.engine().recordFor(p)).thenReturn(record);
        when(s.engine().check(101L)).thenReturn(result(true));
        Deque<String> statuses = new ArrayDeque<>(List.of("DRAFT", "SELLER_REVIEW", "ADMIN_REVIEW", "APPROVED", "PUBLISHED"));
        when(s.engine().findRecord(101L)).thenAnswer(inv -> {
            ProductCompliance r = new ProductCompliance();
            r.setStatus(statuses.poll());
            return Optional.of(r);
        });

        s.service().importRow(changes(599.0), true);

        var order = inOrder(s.workflow());
        for (String step : List.of("submit", "confirm", "approve", "publish")) {
            order.verify(s.workflow()).transition(eq(101L), eq(step), anyString());
        }
        assertEquals("Maker", record.getManufacturerName());
    }

    @Test
    void aRowThatStillFailsIsSavedButNotPublished() {
        Setup s = setup();
        Product p = product(101, 289);
        when(s.products().findById(101L)).thenReturn(Optional.of(p));
        when(s.engine().recordFor(p)).thenReturn(new ProductCompliance());
        when(s.engine().check(101L)).thenReturn(result(false));

        ComplianceBulkService.Outcome out = s.service().importRow(changes(null), true);

        assertFalse(out.published());
        verify(s.workflow(), never()).transition(anyLong(), anyString(), any());
        assertEquals(ProductWorkflowStatus.DRAFT, ProductWorkflowStatus.from(null));
    }
}
