package com.cauverystore.repository;

import com.cauverystore.entities.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findByUserIdAndStatus(Long userId, String status);

    Optional<Session> findByRefreshTokenHash(String refreshTokenHash);

    List<Session> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    List<Session> findByStatusAndExpiresAtBefore(String status, LocalDateTime now);

    long countByUserIdAndStatus(Long userId, String status);
}
