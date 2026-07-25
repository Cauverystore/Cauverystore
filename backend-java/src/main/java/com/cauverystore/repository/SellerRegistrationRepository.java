package com.cauverystore.repository;

import com.cauverystore.entities.SellerRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerRegistrationRepository extends JpaRepository<SellerRegistration, Long> {
    Optional<SellerRegistration> findByUserId(Long userId);
    Optional<SellerRegistration> findByBusinessEmail(String email);
    Optional<SellerRegistration> findByGstin(String gstin);
    Optional<SellerRegistration> findByPanNumber(String pan);
    long countByStatus(String status);
}
