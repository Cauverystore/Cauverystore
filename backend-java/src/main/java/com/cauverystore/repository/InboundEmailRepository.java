package com.cauverystore.repository;

import com.cauverystore.entities.InboundEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InboundEmailRepository extends JpaRepository<InboundEmail, Long> {
    boolean existsByResendEmailId(String resendEmailId);
    Page<InboundEmail> findAllByOrderByReceivedAtDesc(Pageable pageable);
    long countByReadFalse();
}
