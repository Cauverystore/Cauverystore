package com.cauverystore.i18n.repo;

import com.cauverystore.i18n.domain.CategoryTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CategoryTranslationRepository extends JpaRepository<CategoryTranslation, Long> {
    Optional<CategoryTranslation> findByCategoryIdAndLanguageCode(Long categoryId, String languageCode);
}
