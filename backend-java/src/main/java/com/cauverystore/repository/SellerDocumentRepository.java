package com.cauverystore.repository;

import com.cauverystore.entities.SellerDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerDocumentRepository extends JpaRepository<SellerDocument, Long> {
    List<SellerDocument> findByUserId(Long userId);
    List<SellerDocument> findByUserIdAndDocumentType(Long userId, String documentType);
    Optional<SellerDocument> findByUserIdAndDocumentTypeAndStatus(Long userId, String documentType, String status);
    long countByUserIdAndStatus(Long userId, String status);
    List<SellerDocument> findByExpiryDateBeforeAndExpiryAlertSentFalse(java.time.LocalDate date);
}
