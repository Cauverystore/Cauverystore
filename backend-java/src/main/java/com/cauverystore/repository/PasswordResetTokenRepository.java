package com.cauverystore.repository;

import com.cauverystore.entities.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    PasswordResetToken findByEmail(String email);

    PasswordResetToken findByToken(String token);
}
