package com.cauverystore.repository;

import com.cauverystore.entities.GstConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GstConfigurationRepository extends JpaRepository<GstConfiguration, Long> {
    Optional<GstConfiguration> findByGstin(String gstin);
    Optional<GstConfiguration> findBySellerId(Long sellerId);
    List<GstConfiguration> findByIsActiveTrue();
    List<GstConfiguration> findByStateCode(String stateCode);
}
