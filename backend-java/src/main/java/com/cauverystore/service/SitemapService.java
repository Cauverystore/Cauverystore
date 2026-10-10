package com.cauverystore.service;

import com.cauverystore.entities.Category;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.CategoryRepository;
import com.cauverystore.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.net.URLEncoder;
import java.util.List;

/**
 * Builds the public sitemap.xml from whatever is actually live right now, rather than a
 * hand-maintained list that goes stale the moment a product is added, priced out, or pulled.
 *
 * Scope deliberately matches the launch brief: only canonical URLs a shopper can actually land
 * on (home + static pages, live products, and categories that clear the "not an empty shelf"
 * bar), each with a real lastmod date Google can use to decide whether to re-crawl it.
 */
@Service
public class SitemapService {

    /** Mirrors the brief's rule for hiding thin/empty category pages - a category with fewer
     *  live listings than this reads as an empty shelf and should not be offered to Google. */
    private static final int MIN_PRODUCTS_TO_LIST_CATEGORY = 10;

    private static final DateTimeFormatter LASTMOD_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Value("${frontend.url:https://cauverystore.in}")
    private String frontendUrl;

    public SitemapService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public String buildSitemapXml() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        String base = frontendUrl.replaceAll("/+$", "");

        // Home + the static pages every storefront needs, in one place so this file is the
        // only thing that needs updating when a page is added or removed.
        addUrl(xml, base + "/", LocalDateTime.now(), "daily", "1.0");
        addUrl(xml, base + "/about", null, "monthly", "0.5");
        addUrl(xml, base + "/contact", null, "monthly", "0.5");
        addUrl(xml, base + "/faq", null, "monthly", "0.4");
        addUrl(xml, base + "/privacy-policy", null, "monthly", "0.3");
        addUrl(xml, base + "/terms-and-conditions", null, "monthly", "0.3");
        addUrl(xml, base + "/shipping-policy", null, "monthly", "0.3");
        addUrl(xml, base + "/refund-policy", null, "monthly", "0.3");
        addUrl(xml, base + "/offers", LocalDateTime.now(), "daily", "0.6");

        // Categories: only ones with enough live stock to not look like a dead shelf.
        List<Category> categories = categoryRepository.findAll();
        for (Category category : categories) {
            long liveCount = productRepository.countByCategoryAndActiveTrue(category);
            if (liveCount >= MIN_PRODUCTS_TO_LIST_CATEGORY) {
                String slug = encode(category.getName());
                LocalDateTime lastmod = category.getUpdatedAt() != null ? category.getUpdatedAt() : category.getCreatedAt();
                addUrl(xml, base + "/category/" + slug, lastmod, "weekly", "0.7");
            }
        }

        // Every live, publicly-visible product - same definition of "live" the storefront
        // search/listing endpoints already use (Product.active = true).
        List<Product> liveProducts = productRepository.findByActiveTrue();
        for (Product product : liveProducts) {
            LocalDateTime lastmod = product.getUpdatedAt() != null ? product.getUpdatedAt() : product.getCreatedAt();
            addUrl(xml, base + "/product/" + product.getId(), lastmod, "weekly", "0.8");
        }

        xml.append("</urlset>\n");
        return xml.toString();
    }

    private void addUrl(StringBuilder xml, String loc, LocalDateTime lastmod, String changefreq, String priority) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
        if (lastmod != null) {
            xml.append("    <lastmod>").append(lastmod.format(LASTMOD_FORMAT)).append("</lastmod>\n");
        }
        xml.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        xml.append("    <priority>").append(priority).append("</priority>\n");
        xml.append("  </url>\n");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** <, >, & and friends are as fatal inside <loc> as inside any other XML text node - a
     *  product/category name containing one would otherwise produce an unparsable sitemap. */
    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
