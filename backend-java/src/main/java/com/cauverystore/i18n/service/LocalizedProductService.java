package com.cauverystore.i18n.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Reads product text in a language. If that language has no PUBLISHED translation (or a field is empty),
 * the product's own original text is used, so a half-translated catalogue never shows blanks.
 */
@Service
public class LocalizedProductService {

    private static final String VISIBLE =
            "p.active = true and (p.approval_status is null or lower(p.approval_status) = 'approved')";

    private final JdbcTemplate jdbc;
    private final LanguageService languages;

    public LocalizedProductService(JdbcTemplate jdbc, LanguageService languages) {
        this.jdbc = jdbc;
        this.languages = languages;
    }

    public record LocalizedProduct(long id, String requestedLang, String servedLang, String baseLanguage,
                                   String name, String shortDescription, String description,
                                   String metaTitle, String metaDescription, List<String> availableLanguages) {}

    public record LocalizedName(String name, String shortDescription) {}

    public LocalizedProduct get(long id, String lang) {
        List<Map<String, Object>> base = jdbc.queryForList(
                "select p.name, p.short_description, p.description, p.meta_title, p.meta_description " +
                "from products p where p.id = ? and " + VISIBLE, id);
        if (base.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        Map<String, Object> b = base.get(0);

        List<String> published = jdbc.queryForList(
                "select language_code from product_translations where product_id = ? and status = 'PUBLISHED' order by language_code",
                String.class, id);
        List<String> available = new ArrayList<>();
        available.add(languages.baseLanguage());
        published.stream().filter(l -> !available.contains(l)).forEach(available::add);

        String served = languages.baseLanguage();
        String name = str(b.get("name")), shortD = str(b.get("short_description")), desc = str(b.get("description")),
               metaT = str(b.get("meta_title")), metaD = str(b.get("meta_description"));

        if (!lang.equals(languages.baseLanguage()) && published.contains(lang)) {
            Map<String, Object> t = jdbc.queryForMap(
                    "select name, short_description, description, meta_title, meta_description " +
                    "from product_translations where product_id = ? and language_code = ? and status = 'PUBLISHED'", id, lang);
            served = lang;
            name  = pick(str(t.get("name")), name);
            shortD = pick(str(t.get("short_description")), shortD);
            desc  = pick(str(t.get("description")), desc);
            metaT = pick(str(t.get("meta_title")), metaT);
            metaD = pick(str(t.get("meta_description")), metaD);
        }
        return new LocalizedProduct(id, lang, served, languages.baseLanguage(), name, shortD, desc, metaT, metaD, available);
    }

    /** Names for many products at once (for product grids). Only products with a published translation appear. */
    public Map<Long, LocalizedName> names(List<Long> ids, String lang) {
        Map<Long, LocalizedName> out = new LinkedHashMap<>();
        if (ids.isEmpty() || lang.equals(languages.baseLanguage())) return out;
        List<Long> capped = ids.size() > 100 ? ids.subList(0, 100) : ids;
        String marks = String.join(",", Collections.nCopies(capped.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(lang);
        args.addAll(capped);
        jdbc.query("select product_id, name, short_description from product_translations " +
                   "where language_code = ? and status = 'PUBLISHED' and product_id in (" + marks + ")",
                rs -> { out.put(rs.getLong(1), new LocalizedName(rs.getString(2), rs.getString(3))); },
                args.toArray());
        return out;
    }

    public Map<Long, String> categoryNames(String lang) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (lang.equals(languages.baseLanguage())) return out;
        jdbc.query("select category_id, name from category_translations where language_code = ? and status = 'PUBLISHED'",
                rs -> { out.put(rs.getLong(1), rs.getString(2)); }, lang);
        return out;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static String pick(String translated, String original) {
        return (translated == null || translated.isBlank()) ? original : translated;
    }
}
