package com.cauverystore.repository;

import com.cauverystore.entities.SellerCompliance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerComplianceRepository extends JpaRepository<SellerCompliance, Long> {
    List<SellerCompliance> findByUserId(Long userId);
    List<SellerCompliance> findByUserIdAndIsCompletedFalse(Long userId);
    Optional<SellerCompliance> findByUserIdAndRequirementType(Long userId, String requirementType);
    long countByUserIdAndIsCompletedTrue(Long userId);
    long countByUserIdAndIsCompletedFalseAndIsMandatoryTrue(Long userId);
    List<SellerCompliance> findByUserIdAndRequirementTypeIn(Long userId, List<String> types);
}
