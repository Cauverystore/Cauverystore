package com.cauverystore.i18n.web;

import com.cauverystore.i18n.domain.ProductTranslation;
import com.cauverystore.i18n.service.LanguageService;
import com.cauverystore.i18n.service.SearchIndexService;
import com.cauverystore.i18n.service.TranslationAdminService;
import com.cauverystore.i18n.service.TranslationAdminService.ImportResult;
import com.cauverystore.i18n.service.TranslationAdminService.ProductInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Everything under /api/admin/translations/** is already limited by your SecurityConfig to ADMIN, EXECUTIVE and
 * SUPER_ADMIN. Changing data is further limited to ADMIN and SUPER_ADMIN here.
 */
@RestController
@RequestMapping("/api/admin/translations")
public class TranslationAdminController {

    private final TranslationAdminService service;
    private final SearchIndexService searchIndex;
    private final LanguageService languages;

    public TranslationAdminController(TranslationAdminService service, SearchIndexService searchIndex, LanguageService languages) {
        this.service = service;
        this.searchIndex = searchIndex;
        this.languages = languages;
    }

    public record TranslationView(String language, String name, String shortDescription, String description,
                                  String metaTitle, String metaDescription, String status, String source, int version) {
        static TranslationView of(ProductTranslation t) {
            return new TranslationView(t.getLanguageCode(), t.getName(), t.getShortDescription(), t.getDescription(),
                    t.getMetaTitle(), t.getMetaDescription(), t.getStatus(), t.getSource(), t.getVersion());
        }
    }

    public record ProductBody(@NotBlank @Size(max = 500) String name,
                              @Size(max = 2000) String shortDescription,
                              @Size(max = 20000) String description,
                              @Size(max = 255) String metaTitle,
                              @Size(max = 1000) String metaDescription,
                              @Size(max = 20) String status) {}

    public record CategoryBody(@NotBlank @Size(max = 255) String name, @Size(max = 2000) String description,
                               @Size(max = 20) String status) {}

    /** GET /api/admin/translations/products/12 */
    @GetMapping("/products/{id}")
    public List<TranslationView> forProduct(@PathVariable long id) {
        return service.forProduct(id).stream().map(TranslationView::of).toList();
    }

    /** PUT /api/admin/translations/products/12/ta  {"name":"...","status":"PUBLISHED"} */
    @PutMapping("/products/{id}/{lang}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public TranslationView saveProduct(@PathVariable long id, @PathVariable String lang,
                                       @Valid @RequestBody ProductBody b, Authentication auth) {
        ProductTranslation t = service.saveProduct(id, lang,
                new ProductInput(b.name(), b.shortDescription(), b.description(), b.metaTitle(), b.metaDescription(), b.status()),
                LanguageController.userId(auth), "ADMIN");
        return TranslationView.of(t);
    }

    /** PUT /api/admin/translations/categories/3/ta */
    @PutMapping("/categories/{id}/{lang}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Map<String, String> saveCategory(@PathVariable long id, @PathVariable String lang,
                                            @Valid @RequestBody CategoryBody b, Authentication auth) {
        service.saveCategory(id, lang, b.name(), b.description(), b.status(), LanguageController.userId(auth));
        return Map.of("status", "saved");
    }

    /** GET /api/admin/translations/missing?lang=ta&limit=100 */
    @GetMapping("/missing")
    public List<Map<String, Object>> missing(@RequestParam String lang, @RequestParam(defaultValue = "100") int limit) {
        return service.missing(lang, limit);
    }

    /** GET /api/admin/translations/dashboard */
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return service.dashboard();
    }

    /** POST /api/admin/translations/reindex  - rebuild the search index now (otherwise it runs every 30 min). */
    @PostMapping("/reindex")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Map<String, Object> reindex() {
        languages.clearCache();
        return Map.of("indexed", searchIndex.reindexAll());
    }

    /** GET /api/admin/translations/export?lang=ta  -> CSV you can give to a translator. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String lang) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"translations-" + lang.replaceAll("[^a-z]", "") + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(service.exportCsv(lang));
    }

    /** POST /api/admin/translations/import?lang=ta  (multipart form, field "file") */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ImportResult importCsv(@RequestParam String lang, @RequestParam("file") MultipartFile file, Authentication auth) {
        languages.requireActive(lang);
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a CSV file under 5 MB");
        }
        try {
            return service.importCsv(file.getBytes(), lang, LanguageController.userId(auth));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the file");
        }
    }
}
