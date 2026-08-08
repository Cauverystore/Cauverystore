package com.cauverystore.repository;

import com.cauverystore.entities.SacMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SacMasterRepository extends JpaRepository<SacMaster, Long> {

    /**
     * The approved rate for a SAC on a date.
     *
     * Only VERIFIED rows, and only those in force - the same posture as goods. An unapproved
     * service rate is not charged, and a superseded one is not resurrected.
     */
    @Query("SELECT s FROM SacMaster s WHERE s.sacCode = :sac AND s.status = 'VERIFIED' "
            + "AND s.effectiveFrom <= :onDate "
            + "AND (s.effectiveTo IS NULL OR s.effectiveTo >= :onDate) "
            + "ORDER BY s.effectiveFrom DESC")
    List<SacMaster> findApplicable(@Param("sac") String sacCode, @Param("onDate") LocalDate onDate);

    List<SacMaster> findBySacCodeOrderByEffectiveFromDesc(String sacCode);
}
