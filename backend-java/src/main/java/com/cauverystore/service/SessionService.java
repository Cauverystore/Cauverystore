package com.cauverystore.service;

import com.cauverystore.config.JwtUtil;
import com.cauverystore.entities.Session;
import com.cauverystore.repository.SessionRepository;
import io.jsonwebtoken.Claims;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Multi-device session management. Each login creates a Session row; logout
 * revokes that session; refresh rotates the stored token per session; a
 * background sweep expires stale rows.
 */
@Service
public class SessionService {

    private final SessionRepository sessionRepo;
    private final JwtUtil jwtUtil;

    public SessionService(SessionRepository sessionRepo, JwtUtil jwtUtil) {
        this.sessionRepo = sessionRepo;
        this.jwtUtil = jwtUtil;
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash token", e);
        }
    }

    @Transactional
    public Session createSession(Long userId, String refreshToken, String ipAddress, String userAgent, String deviceLabel) {
        Session session = new Session();
        session.setUserId(userId);
        session.setRefreshTokenHash(hashToken(refreshToken));
        try {
            Claims claims = jwtUtil.extractAllClaims(refreshToken);
            session.setJwtJti(claims.getId());
        } catch (Exception ignored) {
            session.setJwtJti(null);
        }
        session.setIpAddress(ipAddress != null && ipAddress.length() > 45 ? ipAddress.substring(0, 45) : ipAddress);
        session.setUserAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);
        session.setDeviceLabel(deviceLabel);
        session.setStatus("ACTIVE");
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        return sessionRepo.save(session);
    }

    @Transactional
    public Session findActiveByRefreshToken(String refreshToken) {
        return sessionRepo.findByRefreshTokenHash(hashToken(refreshToken))
                .filter(s -> "ACTIVE".equals(s.getStatus()))
                .filter(s -> s.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(null);
    }

    @Transactional
    public void rotateSession(Session session, String newRefreshToken) {
        session.setRefreshTokenHash(hashToken(newRefreshToken));
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        sessionRepo.save(session);
    }

    @Transactional
    public void revokeSession(Session session) {
        if (session != null && "ACTIVE".equals(session.getStatus())) {
            session.setStatus("REVOKED");
            session.setRevokedAt(LocalDateTime.now());
            sessionRepo.save(session);
        }
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        for (Session s : sessionRepo.findByUserIdAndStatus(userId, "ACTIVE")) {
            s.setStatus("REVOKED");
            s.setRevokedAt(LocalDateTime.now());
            sessionRepo.save(s);
        }
    }

    public List<Session> listActive(Long userId) {
        return sessionRepo.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "ACTIVE");
    }

    public boolean isOwner(Long userId, Long sessionId) {
        return sessionRepo.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .isPresent();
    }

    /** Expires sessions whose 7-day lifetime has passed (defensive - refresh validation also enforces expiry). */
    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void expireOldSessions() {
        List<Session> stale = sessionRepo.findByStatusAndExpiresAtBefore("ACTIVE", LocalDateTime.now());
        for (Session s : stale) {
            s.setStatus("EXPIRED");
            sessionRepo.save(s);
        }
    }
}
