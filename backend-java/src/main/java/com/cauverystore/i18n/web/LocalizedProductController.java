package com.cauverystore.i18n.web;

import com.cauverystore.entities.Product;
import com.cauverystore.i18n.service.*;
import com.cauverystore.i18n.service.LocalizedProductService.LocalizedName;
import com.cauverystore.i18n.service.LocalizedProductService.LocalizedProduct;
import com.cauverystore.repository.ProductRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;

/**
 * Public, read-only. All paths sit under /api/products/** and /api/categories/**, which your SecurityConfig
 * already allows for GET. None of them change an existing endpoint.
 */
@RestController
@RequestMapping("/api")
public class LocalizedProductController {

    private final LocalizedProductService localized;
    private final MultilingualSearchService search;
    private final LanguageService languages;
    private final ProductRepository productRepo;
    private final ObjectMapper mapper;

    public LocalizedProductController(LocalizedProductService localized, MultilingualSearchService search,
                                      LanguageService languages, ProductRepository productRepo, ObjectMapper mapper) {
        this.localized = localized;
        this.search = search;
        this.languages = languages;
        this.productRepo = productRepo;
        this.mapper = mapper;
    }

    /** GET /api/products/12/localized?lang=ta */
    @GetMapping("/products/{id}/localized")
    public ResponseEntity<LocalizedProduct> product(@PathVariable long id, @RequestParam(defaultValue = "en") String lang) {
        String l = languages.resolveOrBase(lang);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(2)).cachePublic())
                .body(localized.get(id, l));
    }

    /** GET /api/products/localized-names?ids=1,2,3&lang=ta  ->  {"1":{"name":"...","shortDescription":"..."}} */
    @GetMapping("/products/localized-names")
    public ResponseEntity<Map<Long, LocalizedName>> names(@RequestParam String ids, @RequestParam(defaultValue = "en") String lang) {
        List<Long> list = new ArrayList<>();
        for (String s : ids.split(",")) {
            try { list.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) { /* skip */ }
            if (list.size() >= 100) break;
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(2)).cachePublic())
                .body(localized.names(list, languages.resolveOrBase(lang)));
    }

    /** GET /api/categories/localized-names?lang=ta -> {"3":"..."} */
    @GetMapping("/categories/localized-names")
    public ResponseEntity<Map<Long, String>> categoryNames(@RequestParam(defaultValue = "en") String lang) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(localized.categoryNames(languages.resolveOrBase(lang)));
    }

    /**
     * GET /api/products/search/multilingual?q=பருத்தி விரிப்பு&lang=ta&limit=40
     * Returns the same product objects as /api/products/search (in a "content" list), with the name and
     * descriptions replaced by the translation when one is published.
     */
    @GetMapping("/products/search/multilingual")
    public Map<String, Object> multilingualSearch(@RequestParam String q,
                                                  @RequestParam(defaultValue = "en") String lang,
                                                  @RequestParam(defaultValue = "40") int limit) {
        String l = languages.resolveOrBase(lang);
        var result = search.search(q, l, Math.min(Math.max(limit, 1), 60));

        Map<Long, Product> byId = new HashMap<>();
        for (Product p : productRepo.findAllById(result.ids())) byId.put(p.getId(), p);
        Map<Long, LocalizedName> names = localized.names(result.ids(), l);

        List<Map<String, Object>> content = new ArrayList<>();
        for (Long id : result.ids()) {
            Product p = byId.get(id);
            if (p == null) continue;
            // Convert to a plain map first so the translated text can never be written back to the database.
            Map<String, Object> m = mapper.convertValue(p, new TypeReference<LinkedHashMap<String, Object>>() {});
            LocalizedName n = names.get(id);
            if (n != null) {
                if (n.name() != null && !n.name().isBlank()) m.put("name", n.name());
                if (n.shortDescription() != null && !n.shortDescription().isBlank()) m.put("shortDescription", n.shortDescription());
            }
            content.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", content);
        out.put("totalElements", content.size());
        out.put("totalPages", 1);
        out.put("matchType", result.matchType());
        return out;
    }
}
