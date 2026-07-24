package com.cauverystore.repository;

import com.cauverystore.entities.PageContent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PageContentRepository extends JpaRepository<PageContent, Long> {
    Optional<PageContent> findBySlug(String slug);
}
