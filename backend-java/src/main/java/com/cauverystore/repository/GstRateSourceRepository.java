package com.cauverystore.repository;

import com.cauverystore.entities.GstRateSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GstRateSourceRepository extends JpaRepository<GstRateSource, Long> {
    Optional<GstRateSource> findByNotificationNumber(String notificationNumber);
}
