package com.cauverystore.i18n.service;

import com.cauverystore.i18n.domain.Language;
import com.cauverystore.i18n.repo.LanguageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class LanguageService {

    private static final Pattern CODE = Pattern.compile("^[a-z]{2,3}$");
    private static final long TTL_MS = 10 * 60 * 1000L;

    private final LanguageRepository repo;
    private final String baseLanguage;

    private volatile List<Language> cache = List.of();
    private volatile long loadedAt = 0;

    public LanguageService(LanguageRepository repo,
                           @Value("${app.i18n.base-language:en}") String baseLanguage) {
        this.repo = repo;
        this.baseLanguage = baseLanguage;
    }

    /** The language the original product text (products.name, description) is written in. */
    public String baseLanguage() { return baseLanguage; }

    /** Active languages, cached for 10 minutes. */
    public List<Language> active() {
        long now = System.currentTimeMillis();
        if (cache.isEmpty() || now - loadedAt > TTL_MS) {
            cache = List.copyOf(repo.findByIsActiveTrueOrderBySortOrder());
            loadedAt = now;
        }
        return cache;
    }

    public void clearCache() { loadedAt = 0; }

    public boolean isActive(String code) {
        return code != null && CODE.matcher(code).matches()
                && active().stream().anyMatch(l -> l.getCode().equals(code));
    }

    /** For write endpoints: reject anything that is not an active language. */
    public String requireActive(String code) {
        if (!isActive(code)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported language");
        return code;
    }

    /** For public read endpoints: an unknown language quietly becomes the base language. */
    public String resolveOrBase(String code) {
        return isActive(code) ? code : baseLanguage;
    }
}
