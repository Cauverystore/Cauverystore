package com.cauverystore.controller;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import com.cauverystore.service.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "agents.token=publish-test-token", "agents.publish.live=true", "agents.publish.seller-id=46"})
@ActiveProfiles("test")
@Tag("integration")
class AgentAutoPublishIntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired AgentJobRepository jobs;
    @Autowired SupplierLabelRepository labels;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductService productService;
    @SpyBean ProductComplianceService compliance;
    @MockBean HsnClassificationService hsn;
    @MockBean SupplierPhotoService photos;

    private static final ProductComplianceService.Result PASS = new ProductComplianceService.Result(
            null, "GENERAL", true, 100, "PUBLISHABLE", true, List.of(), List.of(), 30, LocalDateTime.now());

    private AgentJob draft(String sku) {
        Category c = new Category();
        c.setName("Publish test " + sku);
        c = categories.save(c);
        AgentJob j = new AgentJob();
        j.setSourceUrl("https://deodap.in/products/" + sku);
        j.setStatus(AgentJob.READY);
        j.setDraftJson("{\"name\":\"Egg Storage Box " + sku + "\",\"brand\":\"Generic\",\"description\":\"A box.\",\"sku\":\"" + sku
                + "\",\"categoryId\":" + c.getId() + ",\"suggestedPrice\":209,\"uom\":\"NOS\","
                + "\"imageUrls\":[\"https://cdn.shopify.com/a.jpg\"]}");
        return jobs.save(j);
    }

    private void label(String sku) {
        SupplierLabel l = new SupplierLabel();
        l.setSku(sku);
        l.setHsnCode("39241090");
        l.setMrp(299.0);
        l.setCountryOfOrigin("India");
        l.setStateOfOrigin("Gujarat");
        l.setManufacturerName("Test Maker");
        l.setManufacturerAddress("Rajkot");
        l.setNetQuantity(BigDecimal.ONE);
        l.setGrossQuantity(BigDecimal.ONE);
        l.setQuantityUnit("N");
        l.setMfgDate(LocalDate.now().minusMonths(1));
        l.setBatchNumber("B1");
        l.setPhotosAllowed(true);
        labels.save(l);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sweep() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Agent-Token", "publish-test-token");
        ResponseEntity<Map> r = http.postForEntity("/api/agent/publish/sweep", new HttpEntity<>(Map.of(), h), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), String.valueOf(r.getBody()));
        return r.getBody();
    }

    @Test
    void waitsForLabelThenPublishesOnce() {
        doReturn(PASS).when(compliance).evaluate(any(Product.class), any(ProductCompliance.class));
        doReturn(Map.of("found", 1, "added", 1, "failed", List.of())).when(photos).importFor(anyLong());
        AgentJob job = draft("IT-1");
        long before = products.count();

        sweep();
        job = jobs.findById(job.getId()).orElseThrow();
        assertEquals(AgentJob.READY, job.getStatus());
        assertTrue(job.getNote().contains("confirmed label"), job.getNote());
        assertEquals(before, products.count());

        label("IT-1");
        Map<String, Object> result = sweep();
        assertEquals(1, result.get("published"), String.valueOf(result));
        job = jobs.findById(job.getId()).orElseThrow();
        assertEquals(AgentJob.USED, job.getStatus());
        Product p = productService.getProductForShoppers(job.getProductId());
        assertEquals(299.0, p.getMrp());
        assertEquals(209.0, p.getPrice());
        assertEquals("published", p.getProductStatus());

        sweep();
        assertEquals(before + 1, products.count(), "a second sweep must not create a duplicate");
    }
}
