package com.cauverystore.repository;

import com.cauverystore.entities.HsnMaster;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HsnMasterRepository extends JpaRepository<HsnMaster, String> {

    /**
     * Codes matching a code prefix or a word in the government's description.
     *
     * Ordered by code length first so the broader heading ("1006 Rice") is offered ahead of its
     * eight-digit children - a seller picking by eye should land on the level they can actually
     * justify, not the first alphabetical sub-item.
     */
    @Query("SELECT h FROM HsnMaster h WHERE LOWER(h.hsnCode) LIKE LOWER(CONCAT(:q, '%')) "
            + "OR LOWER(h.description) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "ORDER BY LENGTH(h.hsnCode) ASC, h.hsnCode ASC")
    List<HsnMaster> search(@Param("q") String query, Pageable pageable);
}
