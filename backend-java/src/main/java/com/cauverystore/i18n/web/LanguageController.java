package com.cauverystore.i18n.web;

import com.cauverystore.i18n.service.LanguageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
public class LanguageController {

    private final LanguageService languages;
    private final JdbcTemplate jdbc;

    public LanguageController(LanguageService languages, JdbcTemplate jdbc) {
        this.languages = languages;
        this.jdbc = jdbc;
    }

    public record LanguageDto(String code, String nameEnglish, String nameNative, String script, boolean isDefault) {}

    /** GET /api/settings/languages  (public: your security rules already allow GET /api/settings/**) */
    @GetMapping("/api/settings/languages")
    public ResponseEntity<List<LanguageDto>> list() {
        var body = languages.active().stream()
                .map(l -> new LanguageDto(l.getCode(), l.getNameEnglish(), l.getNameNative(), l.getScript(), l.isDefault()))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic()).body(body);
    }

    /** GET /api/user/language  -> {"language":"ta"} or {"language":null} */
    @GetMapping("/api/user/language")
    public Map<String, String> mine(Authentication auth) {
        Long userId = userId(auth);
        List<String> rows = userId == null ? List.of() :
                jdbc.queryForList("select language_code from user_language_preferences where user_id = ?", String.class, userId);
        Map<String, String> out = new java.util.HashMap<>();
        out.put("language", rows.isEmpty() ? null : rows.get(0));
        return out;
    }

    public record LanguageRequest(@NotBlank String language) {}

    /** PUT /api/user/language  {"language":"ta"}  - saves the signed-in customer's language. */
    @PutMapping("/api/user/language")
    public Map<String, String> save(@Valid @RequestBody LanguageRequest req, Authentication auth) {
        String lang = languages.requireActive(req.language());
        Long userId = userId(auth);
        if (userId != null) {
            jdbc.update("""
                insert into user_language_preferences (user_id, language_code, updated_at) values (?, ?, now())
                on conflict (user_id) do update set language_code = excluded.language_code, updated_at = now()
                """, userId, lang);
        }
        return Map.of("language", lang);
    }

    /** Your JwtFilter stores the user's id in Authentication.getDetails(). */
    static Long userId(Authentication auth) {
        return auth != null && auth.getDetails() instanceof Long id ? id : null;
    }
}
