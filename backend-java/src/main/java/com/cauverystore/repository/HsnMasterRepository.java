package com.cauverystore.repository;

import com.cauverystore.entities.HsnMaster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HsnMasterRepository extends JpaRepository<HsnMaster, String> {

    List<HsnMaster> findByChapterOrderByHsnCodeAsc(String chapter);

    List<HsnMaster> findByDigitsOrderByHsnCodeAsc(Integer digits);

    @Query("SELECT h FROM HsnMaster h WHERE h.hsnCode LIKE CONCAT(:q, '%') "
            + "OR LOWER(h.description) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY h.hsnCode")
    Page<HsnMaster> search(@Param("q") String q, Pageable pageable);
}
