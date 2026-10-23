package com.cauverystore.service;

import com.cauverystore.entities.Category;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.CategoryRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.i18n.service.LanguageService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
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
    private final LanguageService languageService;
    private final JdbcTemplate jdbc;

    @Value("${frontend.url:https://cauverystore.in}")
    private String frontendUrl;

    public SitemapService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          LanguageService languageService, JdbcTemplate jdbc) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.languageService = languageService;
        this.jdbc = jdbc;
    }

    public String buildSitemapXml() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">\n");

        String base = frontendUrl.replaceAll("/+$", "");

        // Languages the storefront offers besides English (English has no URL prefix, e.g. /product/12;
        // the others do, e.g. /ta/product/12). Pages whose text is fully translated are listed in every
        // language; a product only in the languages that have a published translation of it.
        String baseLang = languageService.baseLanguage();
        List<String> langs = new ArrayList<>();
        for (var l : languageService.active()) {
            if (!l.getCode().equals(baseLang)) langs.add(l.getCode());
        }
        Map<Long, List<String>> productLangs = publishedProductLanguages(langs);

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
        addUrl(xml, base, "/", newestProductChange, langs);
        addUrl(xml, base, "/about", null, langs);
        addUrl(xml, base, "/contact", null, langs);
        addUrl(xml, base, "/faq", null, langs);
        addUrl(xml, base, "/privacy-policy", POLICIES_LAST_UPDATED, langs);
        addUrl(xml, base, "/terms-and-conditions", POLICIES_LAST_UPDATED, langs);
        addUrl(xml, base, "/shipping-policy", POLICIES_LAST_UPDATED, langs);
        addUrl(xml, base, "/refund-policy", POLICIES_LAST_UPDATED, langs);

        // The all-products listing only counts once it actually lists something.
        if (!liveProducts.isEmpty()) {
            addUrl(xml, base, "/products", newestProductChange, langs);
        }

        // Categories: only ones with enough live stock to not look like a dead shelf.
        List<Category> categories = categoryRepository.findAll();
        for (Category category : categories) {
            long liveCount = productRepository.countByCategoryAndActiveTrue(category);
            if (liveCount >= MIN_PRODUCTS_TO_LIST_CATEGORY) {
                String slug = encode(category.getName());
                LocalDateTime lastmod = category.getUpdatedAt() != null ? category.getUpdatedAt() : category.getCreatedAt();
                addUrl(xml, base, "/category/" + slug, lastmod, langs);
            }
        }

        for (Product product : liveProducts) {
            LocalDateTime lastmod = product.getUpdatedAt() != null ? product.getUpdatedAt() : product.getCreatedAt();
            addUrl(xml, base, "/product/" + product.getId(), lastmod,
                    productLangs.getOrDefault(product.getId(), List.of()));
        }

        xml.append("</urlset>\n");
        return xml.toString();
    }

    /**
     * Writes the English URL plus one URL per language in {@code langs}. Every one of them carries the
     * full set of hreflang alternates (each other, itself, and x-default = English), which is what tells
     * Google these are translations of one page and not duplicates.
     */
    private void addUrl(StringBuilder xml, String base, String path, LocalDateTime lastmod, List<String> langs) {
        List<String> all = new ArrayList<>();
        all.add(languageService.baseLanguage());
        all.addAll(langs);
        for (String lang : all) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(escapeXml(localizedUrl(base, path, lang))).append("</loc>\n");
            if (lastmod != null) {
                xml.append("    <lastmod>").append(lastmod.format(LASTMOD_FORMAT)).append("</lastmod>\n");
            }
            if (!langs.isEmpty()) {
                for (String alt : all) {
                    xml.append("    <xhtml:link rel=\"alternate\" hreflang=\"").append(alt)
                       .append("\" href=\"").append(escapeXml(localizedUrl(base, path, alt))).append("\"/>\n");
                }
                xml.append("    <xhtml:link rel=\"alternate\" hreflang=\"x-default\" href=\"")
                   .append(escapeXml(localizedUrl(base, path, languageService.baseLanguage()))).append("\"/>\n");
            }
            xml.append("  </url>\n");
        }
    }

    /** English: /product/5. Any other language: /ta/product/5 (home is /ta, with no trailing slash). */
    private String localizedUrl(String base, String path, String lang) {
        if (lang.equals(languageService.baseLanguage())) return base + path;
        return base + "/" + lang + ("/".equals(path) ? "" : path);
    }

    /** product id -> the active non-English languages that have a PUBLISHED translation of it. */
    private Map<Long, List<String>> publishedProductLanguages(List<String> langs) {
        Map<Long, List<String>> out = new HashMap<>();
        if (langs.isEmpty()) return out;
        try {
            jdbc.query("select product_id, language_code from product_translations "
                            + "where status = 'PUBLISHED' order by language_code",
                    rs -> {
                        String code = rs.getString("language_code");
                        if (langs.contains(code)) {
                            out.computeIfAbsent(rs.getLong("product_id"), k -> new ArrayList<>()).add(code);
                        }
                    });
        } catch (Exception e) {
            // No translations table or query trouble: the sitemap still works, English only for products.
        }
        return out;
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
