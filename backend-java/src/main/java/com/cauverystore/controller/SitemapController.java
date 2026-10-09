package com.cauverystore.controller;

import com.cauverystore.service.SitemapService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SitemapController {

    private final SitemapService sitemapService;

    public SitemapController(SitemapService sitemapService) {
        this.sitemapService = sitemapService;
    }

    /**
     * Public, unauthenticated by design - this is what Google/Vercel's rewrite fetches, and a
     * crawler never carries a JWT. Lives at /api/sitemap.xml on the backend; the frontend's
     * vercel.json rewrites https://cauverystore.in/sitemap.xml to this endpoint so it reads as
     * a normal root-level sitemap to search engines.
     */
    @GetMapping(value = "/api/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofHours(1)))
                .body(sitemapService.buildSitemapXml());
    }
}
