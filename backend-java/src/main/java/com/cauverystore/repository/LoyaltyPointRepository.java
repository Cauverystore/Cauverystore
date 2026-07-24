package com.cauverystore.repository;

import com.cauverystore.entities.LoyaltyPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoyaltyPointRepository extends JpaRepository<LoyaltyPoint, Long> {

    List<LoyaltyPoint> findByUserId(Long userId);

    @Query("SELECT COALESCE(SUM(lp.points), 0) FROM LoyaltyPoint lp WHERE lp.user.id = :userId AND lp.type = 'CREDIT'")
    int getTotalCredits(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(lp.points), 0) FROM LoyaltyPoint lp WHERE lp.user.id = :userId AND lp.type = 'DEBIT'")
    int getTotalDebits(@Param("userId") Long userId);
}
