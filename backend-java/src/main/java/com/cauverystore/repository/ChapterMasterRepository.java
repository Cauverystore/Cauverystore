package com.cauverystore.repository;

import com.cauverystore.entities.ChapterMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChapterMasterRepository extends JpaRepository<ChapterMaster, String> {

    List<ChapterMaster> findAllByOrderByChapterAsc();

    /**
     * Chapters matching a number or a word in the title.
     *
     * Titles carry the tariff's own wording, which is how a seller recognises their trade -
     * "FOOTWEAR, GAITERS AND THE LIKE" is findable by searching "footwear", where the chapter
     * number 64 is not findable by anyone who does not already know it.
     */
    @Query("SELECT c FROM ChapterMaster c WHERE c.chapter LIKE CONCAT(:q, '%') "
            + "OR LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "ORDER BY c.chapter ASC")
    List<ChapterMaster> search(@Param("q") String query);
}
