package com.cauverystore.repository;

import com.cauverystore.entities.FilingDeadline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FilingDeadlineRepository extends JpaRepository<FilingDeadline, Long> {
    Optional<FilingDeadline> findBySellerGstinAndFormAndPeriod(String sellerGstin, String form, String period);
    List<FilingDeadline> findBySellerGstinOrderByDueDateAsc(String sellerGstin);
    List<FilingDeadline> findBySellerGstinAndStatus(String sellerGstin, String status);
    List<FilingDeadline> findByStatus(String status);
}
