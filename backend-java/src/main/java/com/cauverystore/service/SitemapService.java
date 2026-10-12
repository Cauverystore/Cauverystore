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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Builds the public sitemap.xml from whatever is actually live right now, rather than a
 * hand-maintained list that goes stale the moment a product is added, priced out, or pulled.
 *
 * Scope deliberately matches the launch brief: only canonical URLs a shopper can actually land
 * on (home + static pages with real content, live products, and categories that clear the
 * "not an empty shelf" bar). A lastmod is written only when it is a real date; Google ignores
 * lastmod values that are always "now", so none is better than a made-up one. changefreq and
 * priority are not written because Google ignores them.
 */
@Service
public class SitemapService {

    /** Mirrors the brief's rule for hiding thin/empty category pages - a category with fewer
     *  live listings than this reads as an empty shelf and should not be offered to Google. */
    private static final int MIN_PRODUCTS_TO_LIST_CATEGORY = 10;

    /** The policy pages print "Last updated: 10 October 2026". Update this date whenever the
     *  wording of the privacy, terms, shipping or refund policy changes. */
    private static final LocalDateTime POLICIES_LAST_UPDATED = LocalDateTime.of(2026, 10, 10, 0, 0);

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

        // Every live, publicly-visible product - same definition of "live" the storefront
        // search/listing endpoints already use (Product.active = true).
        List<Product> liveProducts = productRepository.findByActiveTrue().stream()
                .filter(ProductService::isApprovedForShoppers).toList();

        // The newest real product change is the honest lastmod for the pages that show products.
        LocalDateTime newestProductChange = liveProducts.stream()
                .map(p -> p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        // Home + the static pages that have real content. Keep this list to pages that are
        // finished. /offers is left out on purpose: it shows "No offers running right now"
        // (or repeats /products), and empty pages count as soft 404s. Add it back here once
        // sellers are running real offers.
        // /faq is the canonical URL of the Help Center page (/help renders the same page and
        // points its canonical at /faq), so only /faq is listed.
        addUrl(xml, base + "/", newestProductChange);
        addUrl(xml, base + "/about", null);
        addUrl(xml, base + "/contact", null);
        addUrl(xml, base + "/faq", null);
        addUrl(xml, base + "/privacy-policy", POLICIES_LAST_UPDATED);
        addUrl(xml, base + "/terms-and-conditions", POLICIES_LAST_UPDATED);
        addUrl(xml, base + "/shipping-policy", POLICIES_LAST_UPDATED);
        addUrl(xml, base + "/refund-policy", POLICIES_LAST_UPDATED);

        // The all-products listing only counts once it actually lists something.
        if (!liveProducts.isEmpty()) {
            addUrl(xml, base + "/products", newestProductChange);
        }

        // Categories: only ones with enough live stock to not look like a dead shelf.
        List<Category> categories = categoryRepository.findAll();
        for (Category category : categories) {
            long liveCount = productRepository.countByCategoryAndActiveTrue(category);
            if (liveCount >= MIN_PRODUCTS_TO_LIST_CATEGORY) {
                String slug = encode(category.getName());
                LocalDateTime lastmod = category.getUpdatedAt() != null ? category.getUpdatedAt() : category.getCreatedAt();
                addUrl(xml, base + "/category/" + slug, lastmod);
            }
        }

        for (Product product : liveProducts) {
            LocalDateTime lastmod = product.getUpdatedAt() != null ? product.getUpdatedAt() : product.getCreatedAt();
            addUrl(xml, base + "/product/" + product.getId(), lastmod);
        }

        xml.append("</urlset>\n");
        return xml.toString();
    }

    private void addUrl(StringBuilder xml, String loc, LocalDateTime lastmod) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
        if (lastmod != null) {
            xml.append("    <lastmod>").append(lastmod.format(LASTMOD_FORMAT)).append("</lastmod>\n");
        }
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
