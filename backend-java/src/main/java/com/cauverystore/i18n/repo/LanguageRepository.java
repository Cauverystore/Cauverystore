package com.cauverystore.i18n.repo;

import com.cauverystore.i18n.domain.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LanguageRepository extends JpaRepository<Language, String> {
    List<Language> findByIsActiveTrueOrderBySortOrder();
}
