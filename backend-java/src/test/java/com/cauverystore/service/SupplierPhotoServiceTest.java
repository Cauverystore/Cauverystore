package com.cauverystore.service;

import com.cauverystore.entities.AgentJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SupplierPhotoServiceTest {

    @Test
    void onlySupplierPhotoLinksAreFetched() {
        assertTrue(SupplierPhotoService.allowed("https://cdn.shopify.com/s/files/1/a.jpg?v=1"));
        assertTrue(SupplierPhotoService.allowed("https://deodap.in/cdn/shop/files/a.jpg"));
        assertFalse(SupplierPhotoService.allowed("http://cdn.shopify.com/a.jpg"), "plain http");
        assertFalse(SupplierPhotoService.allowed("https://evil.example/a.jpg"));
        assertFalse(SupplierPhotoService.allowed("https://cdn.shopify.com.evil.example/a.jpg"));
        assertFalse(SupplierPhotoService.allowed("https://user@cdn.shopify.com/a.jpg"));
        assertFalse(SupplierPhotoService.allowed("https://cdn.shopify.com:8443/a.jpg"));
        assertFalse(SupplierPhotoService.allowed("https://169.254.169.254/latest"));
        assertFalse(SupplierPhotoService.allowed("not a link"));
    }

    @Test
    void readsAtMostFivePhotoLinksFromTheDraft() {
        AgentJob j = new AgentJob();
        j.setDraftJson("{\"imageUrls\":[\"https://cdn.shopify.com/1.jpg\",\"https://evil.example/x.jpg\","
                + "\"https://cdn.shopify.com/2.jpg\",\"https://cdn.shopify.com/2.jpg\",\"https://cdn.shopify.com/3.jpg\","
                + "\"https://cdn.shopify.com/4.jpg\",\"https://cdn.shopify.com/5.jpg\",\"https://cdn.shopify.com/6.jpg\"]}");
        List<String> links = SupplierPhotoService.photoLinks(j);
        assertEquals(5, links.size());
        assertFalse(links.contains("https://evil.example/x.jpg"));
        assertEquals("https://cdn.shopify.com/1.jpg", links.get(0));
    }

    @Test
    void noDraftMeansNoPhotos() {
        assertTrue(SupplierPhotoService.photoLinks(new AgentJob()).isEmpty());
        AgentJob bad = new AgentJob();
        bad.setDraftJson("not json");
        assertTrue(SupplierPhotoService.photoLinks(bad).isEmpty());
    }

    /** Run by hand with -Dlive=true: downloads one real DeoDap photo. */
    @Test
    @EnabledIfSystemProperty(named = "live", matches = "true")
    void downloadsARealSupplierPhoto() throws Exception {
        SupplierPhotoService s = new SupplierPhotoService(null, null, "http://localhost:9092");
        SupplierPhotoService.Photo p = s.download(
                "https://cdn.shopify.com/s/files/1/0782/3284/6646/files/02_egg-box.jpg?v=1759468507");
        assertEquals("image/jpeg", p.type());
        assertTrue(p.bytes().length > 10_000);
        System.out.println("LIVE: downloaded " + p.bytes().length + " bytes, " + p.type());
    }
}
