package com.cauverystore.controller;

import com.cauverystore.entities.AgentJob;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.AgentJobRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.service.ProductService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "agents.token=watch-test-token", "agents.watch.live=true", "agents.watch.alert-email="})
@ActiveProfiles("test")
@Tag("integration")
class AgentWatchIntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired ProductRepository products;
    @Autowired AgentJobRepository jobs;
    @Autowired ProductService productService;

    private Long listed(double price, double mrp) {
        Product p = new Product();
        p.setName("Watcher test box");
        p.setPrice(price);
        p.setMrp(mrp);
        p.setStock(10);
        p = products.save(p);
        AgentJob j = new AgentJob();
        j.setSourceUrl("https://deodap.in/products/watch-" + p.getId());
        j.setStatus(AgentJob.USED);
        j.setProductId(p.getId());
        jobs.save(j);
        return p.getId();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(Long id, boolean available, Double cost) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Agent-Token", "watch-test-token");
        Map<String, Object> body = new HashMap<>();
        body.put("available", available);
        body.put("cost", cost);
        ResponseEntity<Map> r = http.postForEntity("/api/agent/watch/products/" + id, new HttpEntity<>(body, h), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), String.valueOf(r.getBody()));
        return r.getBody();
    }

    @Test
    void fullCycle() {
        Long id = listed(209, 299);
        productService.getProductById(id); // fills the cache, so a stale read would show below

        assertEquals("BASELINE", read(id, true, 100.0).get("action"));
        assertEquals(209.0, productService.getProductById(id).getPrice());

        assertEquals("PRICE", read(id, true, 110.0).get("action"));
        assertEquals(229.0, productService.getProductById(id).getPrice());

        assertEquals("HIDE", read(id, false, null).get("action"));
        assertFalse(productService.getProductById(id).isActive());

        assertEquals("RELIST", read(id, true, 110.0).get("action"));
        assertTrue(productService.getProductById(id).isActive());

        assertEquals("REVIEW", read(id, true, 140.0).get("action"));
        assertFalse(productService.getProductById(id).isActive());
        assertEquals(229.0, productService.getProductById(id).getPrice());

        assertEquals("NONE", read(id, true, 110.0).get("action"), "a review hold never lifts itself");
        assertFalse(productService.getProductById(id).isActive());
    }

    @Test
    void adminHiddenProductIsLeftAlone() {
        Long id = listed(209, 299);
        productService.updateActiveStatus(id, false);
        assertEquals("NONE", read(id, true, 100.0).get("action"));
        assertFalse(productService.getProductById(id).isActive());
    }

    @Test
    void wrongTokenIsRefused() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Agent-Token", "wrong");
        ResponseEntity<String> r = http.exchange("/api/agent/watch/products", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
    }
}
