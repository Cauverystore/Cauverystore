package com.cauverystore.repository;

import com.cauverystore.entities.HsnAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HsnAssignmentRepository extends JpaRepository<HsnAssignment, Long> {

    Optional<HsnAssignment> findByCategoryIdAndHsnCode(Long categoryId, String hsnCode);

    /** Codes already used in this category, most-chosen first. */
    List<HsnAssignment> findByCategoryIdOrderByTimesUsedDescLastUsedAtDesc(Long categoryId);

    /** Fallback when a product has no category: whatever the store uses most overall. */
    List<HsnAssignment> findTop10ByOrderByTimesUsedDescLastUsedAtDesc();
}
