package com.cauverystore.repository;

import com.cauverystore.entities.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

    EmailOtp findByEmail(String email);
}
