package com.cauverystore.repository;

import com.cauverystore.entities.DocumentSeries;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentSeriesRepository extends JpaRepository<DocumentSeries, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DocumentSeries s WHERE s.docType = :docType AND s.prefix = :prefix AND s.period = :period")
    Optional<DocumentSeries> lockSeries(@Param("docType") String docType, @Param("prefix") String prefix,
                                        @Param("period") String period);

    Optional<DocumentSeries> findFirstByDocTypeAndPrefix(String docType, String prefix);
}
