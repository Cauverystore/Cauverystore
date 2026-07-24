package com.cauverystore.repository;

import com.cauverystore.entities.Faq;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {
    List<Faq> findByActiveTrueOrderBySortOrder();
    List<Faq> findAllByOrderBySortOrder();
}
