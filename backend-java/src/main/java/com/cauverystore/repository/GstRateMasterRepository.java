package com.cauverystore.repository;

import com.cauverystore.entities.GstRateMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface GstRateMasterRepository extends JpaRepository<GstRateMaster, Long> {

    /**
     * The rate in force for an HSN on a given date. Ordered most-recently-effective first so
     * a later notification supersedes an earlier one even if effectiveTo was never closed off.
     */
    @Query("SELECT r FROM GstRateMaster r WHERE r.hsnCode = :hsn AND r.status = :status "
            + "AND r.effectiveFrom <= :onDate "
            + "AND (r.effectiveTo IS NULL OR r.effectiveTo >= :onDate) "
            + "ORDER BY r.effectiveFrom DESC, r.id DESC")
    List<GstRateMaster> findApplicable(@Param("hsn") String hsn,
                                       @Param("status") String status,
                                       @Param("onDate") LocalDate onDate);

    List<GstRateMaster> findByHsnCodeOrderByEffectiveFromDesc(String hsnCode);

    List<GstRateMaster> findByStatusOrderByHsnCodeAsc(String status);

    long countByStatus(String status);
}
