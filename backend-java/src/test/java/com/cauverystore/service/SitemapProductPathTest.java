package com.cauverystore.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The sitemap must list the same address the product page settles on, or search engines are
 * sent to one address and redirected to another. The expected values here are exactly what
 * frontend/src/utils/productUrl.js produces for the same names.
 */
class SitemapProductPathTest {

    @Test
    void shouldBuildTheSameReadableAddressAsTheStorefront() {
        assertEquals("/product/12-blue-cotton-tee", SitemapService.productPath(12L, "Blue Cotton Tee"));
        assertEquals("/product/12-cafe-creme-t-shirt-xl-and-cap",
                SitemapService.productPath(12L, "Café Crème T-Shirt (XL) & Cap"));
        assertEquals("/product/12-tee", SitemapService.productPath(12L, "தமிழ் Tee"));
        assertEquals("/product/12", SitemapService.productPath(12L, ""));
        assertEquals("/product/12", SitemapService.productPath(12L, null));
        assertEquals("/product/12-a-very-long-product-name-that-keeps-going-on-and-on-beyond",
                SitemapService.productPath(12L,
                        "A very long product name that keeps going on and on beyond sixty characters for sure"));
    }
}
