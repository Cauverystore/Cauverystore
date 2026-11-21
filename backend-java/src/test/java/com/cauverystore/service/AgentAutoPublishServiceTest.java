package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentAutoPublishServiceTest {

    AgentJobRepository jobs = mock(AgentJobRepository.class);
    SupplierLabelRepository labels = mock(SupplierLabelRepository.class);
    CategoryRepository categories = mock(CategoryRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    ProductService productService = mock(ProductService.class);
    ProductComplianceService compliance = mock(ProductComplianceService.class);
    ProductComplianceRepository records = mock(ProductComplianceRepository.class);
    SupplierPhotoService photos = mock(SupplierPhotoService.class);
    AuditService audit = mock(AuditService.class);
    AgentAutoPublishService svc;
    SupplierLabel label;
    Product saved;

    static final ProductComplianceService.Result PASS = new ProductComplianceService.Result(
            null, "GENERAL", true, 100, "PUBLISHABLE", true, List.of(), List.of(), 30, LocalDateTime.now());

    static ProductComplianceService.Result failing(String what) {
        var issue = new ProductComplianceService.Issue("x", what, "s", false, "MISSING", what);
        return new ProductComplianceService.Result(null, "GENERAL", false, 96, "WARNING", true, List.of("x"), List.of(issue), 30, LocalDateTime.now());
    }

    AgentJob job(String name) {
        AgentJob j = new AgentJob();
        j.setStatus(AgentJob.READY);
        j.setSourceUrl("https://deodap.in/products/box");
        j.setDraftJson("{\"name\":\"" + name + "\",\"sku\":\"10715\",\"categoryId\":3,\"suggestedPrice\":209,"
                + "\"imageUrls\":[\"https://cdn.shopify.com/a.jpg\"]}");
        return j;
    }

    @BeforeEach
    void setUp() {
        svc = new AgentAutoPublishService(jobs, labels, categories, products, productService, compliance, records, photos, audit);
        svc.sellerId = "46";
        label = new SupplierLabel();
        label.setSku("10715");
        label.setHsnCode("39241090");
        label.setMrp(299.0);
        label.setPhotosAllowed(true);
        when(labels.findBySkuIgnoreCase("10715")).thenReturn(Optional.of(label));
        when(categories.findById(3L)).thenReturn(Optional.of(new Category()));
        when(compliance.evaluate(any(Product.class), any(ProductCompliance.class))).thenReturn(PASS);
        when(compliance.check(7L)).thenReturn(PASS);
        when(compliance.recordFor(any())).thenReturn(new ProductCompliance());
        saved = mock(Product.class);
        when(saved.getId()).thenReturn(7L);
        when(saved.getSku()).thenReturn("10715");
        when(productService.addProduct(any())).thenReturn(saved);
        when(products.findById(7L)).thenReturn(Optional.of(saved));
        when(photos.importFor(7L)).thenReturn(Map.of("added", 2));
    }

    @Test void noLabelMeansNothingIsCreated() {
        when(labels.findBySkuIgnoreCase("10715")).thenReturn(Optional.empty());
        svc.live = true;
        AgentJob j = job("Egg Box");
        assertEquals("WAITING", svc.process(j));
        assertTrue(j.getNote().contains("confirmed label"));
        verify(productService, never()).addProduct(any());
    }

    @Test void noPhotoPermissionBlocks() {
        label.setPhotosAllowed(false);
        svc.live = true;
        AgentJob j = job("Egg Box");
        assertEquals("WAITING", svc.process(j));
        assertTrue(j.getNote().contains("permission"));
        verify(productService, never()).addProduct(any());
    }

    @Test void anyComplianceGapBlocksEvenAbove90() {
        when(compliance.evaluate(any(Product.class), any(ProductCompliance.class))).thenReturn(failing("Customer care phone"));
        svc.live = true;
        AgentJob j = job("Egg Box");
        assertEquals("WAITING", svc.process(j));
        assertTrue(j.getNote().contains("Customer care phone"));
        verify(productService, never()).addProduct(any());
    }

    @Test void supplierNameInTitleBlocks() {
        svc.live = true;
        AgentJob j = job("DeoDap Egg Box");
        assertEquals("WAITING", svc.process(j));
        verify(productService, never()).addProduct(any());
    }

    @Test void previewCreatesNothing() {
        AgentJob j = job("Egg Box");
        assertEquals("READY", svc.process(j));
        assertEquals(AgentJob.READY, j.getStatus());
        verify(productService, never()).addProduct(any());
        verify(productService, never()).publishChecked(any());
    }

    @Test void completeProductPublishes() {
        svc.live = true;
        AgentJob j = job("Egg Box");
        assertEquals("PUBLISHED", svc.process(j));
        assertEquals(AgentJob.USED, j.getStatus());
        assertEquals(7L, j.getProductId());
        verify(productService).publishChecked(7L);
    }

    @Test void noPhotosCopiedStaysHiddenAndResumesWithoutDuplicate() {
        svc.live = true;
        when(photos.importFor(7L)).thenReturn(Map.of("added", 0));
        AgentJob j = job("Egg Box");
        assertEquals("WAITING", svc.process(j));
        verify(productService, never()).publishChecked(any());

        when(photos.importFor(7L)).thenReturn(Map.of("added", 1));
        assertEquals("PUBLISHED", svc.process(j));
        verify(productService, times(1)).addProduct(any());
    }
}
