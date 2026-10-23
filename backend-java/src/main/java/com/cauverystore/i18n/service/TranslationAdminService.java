package com.cauverystore.i18n.service;

import com.cauverystore.i18n.domain.CategoryTranslation;
import com.cauverystore.i18n.domain.ProductTranslation;
import com.cauverystore.i18n.repo.CategoryTranslationRepository;
import com.cauverystore.i18n.repo.ProductTranslationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Admin-side translation work: save a translation, see what is missing, import/export CSV. */
@Service
public class TranslationAdminService {

    private static final Set<String> ALLOWED_STATUS = Set.of("DRAFT", "PUBLISHED");

    private final ProductTranslationRepository products;
    private final CategoryTranslationRepository categories;
    private final LanguageService languages;
    private final TextSanitizer sanitizer;
    private final SearchIndexService searchIndex;
    private final JdbcTemplate jdbc;

    public TranslationAdminService(ProductTranslationRepository products, CategoryTranslationRepository categories,
                                   LanguageService languages, TextSanitizer sanitizer,
                                   SearchIndexService searchIndex, JdbcTemplate jdbc) {
        this.products = products; this.categories = categories; this.languages = languages;
        this.sanitizer = sanitizer; this.searchIndex = searchIndex; this.jdbc = jdbc;
    }

    public record ProductInput(String name, String shortDescription, String description,
                               String metaTitle, String metaDescription, String status) {}

    @Transactional
    public ProductTranslation saveProduct(long productId, String lang, ProductInput in, Long userId, String source) {
        ProductTranslation saved = saveProductRow(productId, lang, in, userId, source);
        searchIndex.reindexProduct(productId);
        return saved;
    }

    /** Saves without touching the search index (CSV import rebuilds the index once at the end). */
    private ProductTranslation saveProductRow(long productId, String lang, ProductInput in, Long userId, String source) {
        languages.requireActive(lang);
        if (lang.equals(languages.baseLanguage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The base language is edited on the product itself, not as a translation");
        }
        Integer exists = jdbc.queryForObject("select count(*) from products where id = ?", Integer.class, productId);
        if (exists == null || exists == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");

        String name = sanitizer.plain(in.name());
        if (name == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        if (name.length() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is too long");
        String status = in.status() == null ? "DRAFT" : in.status().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUS.contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be DRAFT or PUBLISHED");

        ProductTranslation t = products.findByProductIdAndLanguageCode(productId, lang)
                .orElseGet(() -> new ProductTranslation(productId, lang));
        boolean isNew = t.getId() == null;
        t.setName(name);
        t.setShortDescription(sanitizer.plain(in.shortDescription()));
        t.setDescription(sanitizer.rich(in.description()));
        String metaTitle = sanitizer.plain(in.metaTitle());
        t.setMetaTitle(metaTitle != null && metaTitle.length() > 255 ? metaTitle.substring(0, 255) : metaTitle);
        t.setMetaDescription(sanitizer.plain(in.metaDescription()));
        t.setStatus(status);
        t.setSource(source);
        t.setUpdatedBy(userId);
        if (!isNew) t.bumpVersion();
        return products.save(t);
    }

    public List<ProductTranslation> forProduct(long productId) {
        return products.findByProductIdOrderByLanguageCode(productId);
    }

    @Transactional
    public void saveCategory(long categoryId, String lang, String name, String description, String status, Long userId) {
        languages.requireActive(lang);
        String n = sanitizer.plain(name);
        if (n == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        String st = status == null ? "DRAFT" : status.toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUS.contains(st)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be DRAFT or PUBLISHED");
        Integer exists = jdbc.queryForObject("select count(*) from categories where id = ?", Integer.class, categoryId);
        if (exists == null || exists == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");

        CategoryTranslation t = categories.findByCategoryIdAndLanguageCode(categoryId, lang)
                .orElseGet(() -> new CategoryTranslation(categoryId, lang));
        boolean isNew = t.getId() == null;
        t.setName(n);
        t.setDescription(sanitizer.plain(description));
        t.setStatus(st);
        t.setSource("ADMIN");
        t.setUpdatedBy(userId);
        if (!isNew) t.bumpVersion();
        categories.save(t);
    }

    // ------------------------------------------------------------------ reports

    public List<Map<String, Object>> missing(String lang, int limit) {
        languages.requireActive(lang);
        return jdbc.queryForList("""
            select p.id as product_id, p.name, p.product_code
              from products p
             where p.active = true
               and not exists (select 1 from product_translations t
                                where t.product_id = p.id and t.language_code = ? and t.status = 'PUBLISHED')
             order by p.id limit ?
            """, lang, Math.min(Math.max(limit, 1), 500));
    }

    public Map<String, Object> dashboard() {
        Integer total = jdbc.queryForObject("select count(*) from products where active = true", Integer.class);
        List<Map<String, Object>> perLang = jdbc.queryForList("""
            select l.code as language_code, l.name_english,
                   count(t.id) filter (where t.status = 'PUBLISHED') as published,
                   count(t.id) filter (where t.status <> 'PUBLISHED') as drafts
              from languages l
              left join product_translations t on t.language_code = l.code
             where l.is_active and l.is_default = false
             group by l.code, l.name_english, l.sort_order
             order by l.sort_order
            """);
        return Map.of("activeProducts", total == null ? 0 : total, "languages", perLang);
    }

    // ------------------------------------------------------------------ CSV

    private static final List<String> EXPORT_HEADER = List.of("product_id", "language", "source_name",
            "source_short_description", "source_description", "name", "short_description", "description",
            "meta_title", "meta_description", "status");

    /** One row per active product for this language, with the original text beside the translation. */
    public byte[] exportCsv(String lang) {
        languages.requireActive(lang);
        StringBuilder sb = new StringBuilder("﻿");          // BOM so Excel opens Tamil/Hindi text correctly
        sb.append(String.join(",", EXPORT_HEADER)).append("\r\n");
        jdbc.query("""
            select p.id, p.name, p.short_description, p.description,
                   t.name, t.short_description, t.description, t.meta_title, t.meta_description, t.status
              from products p
              left join product_translations t on t.product_id = p.id and t.language_code = ?
             where p.active = true order by p.id
            """, rs -> {
                sb.append(rs.getLong(1)).append(',').append(lang);
                for (int c = 2; c <= 10; c++) sb.append(',').append(csv(rs.getString(c)));
                sb.append("\r\n");
            }, lang);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public record ImportResult(int saved, int skipped, List<String> errors) {}

    /** Reads the same layout as the export. Rows with an empty "name" are skipped. */
    public ImportResult importCsv(byte[] data, String defaultLang, Long userId) {
        String text = new String(data, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1);
        List<List<String>> rows = parseCsv(text);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty");

        List<String> header = rows.get(0).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        int iId = header.indexOf("product_id"), iName = header.indexOf("name");
        if (iId < 0 || iName < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Columns product_id and name are required");
        int iLang = header.indexOf("language"), iShort = header.indexOf("short_description"),
            iDesc = header.indexOf("description"), iMt = header.indexOf("meta_title"),
            iMd = header.indexOf("meta_description"), iSt = header.indexOf("status");

        int saved = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        if (rows.size() > 5001) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import at most 5000 rows at a time");

        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String name = cell(row, iName);
            if (name == null || name.isBlank()) { skipped++; continue; }
            try {
                long id = Long.parseLong(cell(row, iId).trim());
                String lang = iLang >= 0 && cell(row, iLang) != null && !cell(row, iLang).isBlank()
                        ? cell(row, iLang).trim() : defaultLang;
                saveProductRow(id, lang, new ProductInput(name, cell(row, iShort), cell(row, iDesc),
                        cell(row, iMt), cell(row, iMd), cell(row, iSt)), userId, "IMPORT");
                saved++;
            } catch (Exception e) {
                skipped++;
                if (errors.size() < 50) errors.add("Row " + (r + 1) + ": " + e.getMessage());
            }
        }
        if (saved > 0) searchIndex.reindexAll();
        return new ImportResult(saved, skipped, errors);
    }

    private static String cell(List<String> row, int i) { return i >= 0 && i < row.size() ? row.get(i) : null; }

    private static String csv(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;   // stops spreadsheet formula injection
        return "\"" + v + "\"";
    }

    /** Small RFC 4180 reader: quoted fields, doubled quotes, commas and line breaks inside quotes. */
    static List<List<String>> parseCsv(String s) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inQ) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQ = false;
                } else cur.append(c);
            } else if (c == '"') inQ = true;
            else if (c == ',') { row.add(cur.toString()); cur.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
                row.add(cur.toString()); cur.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(row);
                row = new ArrayList<>();
            } else cur.append(c);
        }
        if (cur.length() > 0 || !row.isEmpty()) {
            row.add(cur.toString());
            rows.add(row);
        }
        return rows;
    }
}
