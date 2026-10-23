package com.cauverystore.i18n.repo;

import com.cauverystore.i18n.domain.ProductTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductTranslationRepository extends JpaRepository<ProductTranslation, Long> {
    Optional<ProductTranslation> findByProductIdAndLanguageCode(Long productId, String languageCode);
    List<ProductTranslation> findByProductIdOrderByLanguageCode(Long productId);
}
