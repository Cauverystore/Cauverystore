package com.cauverystore.service;

import com.cauverystore.config.JwtUtil;
import com.cauverystore.entities.ImpersonationSession;
import com.cauverystore.entities.User;
import com.cauverystore.repository.ImpersonationSessionRepository;
import com.cauverystore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImpersonationService {

    private final ImpersonationSessionRepository sessionRepo;
    private final UserRepository userRepo;
    private final AuditService auditService;
    private final JwtUtil jwtUtil;

    private static final long SESSION_DURATION_MINUTES = 30;

    public Map<String, Object> startImpersonation(Long impersonatorId, Long targetUserId, String reason) {
        User target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("Target user not found: " + targetUserId));

        ImpersonationSession session = new ImpersonationSession();
        session.setImpersonatorId(impersonatorId);
        session.setTargetUserId(targetUserId);
        session.setTargetRole(target.getRole() != null ? target.getRole().name() : "CUSTOMER");
        session.setReason(reason);
        session.setStartTime(LocalDateTime.now());
        session.setActive(true);
        session = sessionRepo.save(session);

        String impersonationToken = jwtUtil.generateAccessToken(
                target.getEmail(), target.getId(), target.getUsername(),
                target.getRole() != null ? target.getRole().name() : "CUSTOMER", target.getTokenVersion());

        auditService.log(impersonatorId, "impersonator", "IMPERSONATION_START",
                "ImpersonationSession", session.getId(),
                "Impersonating user " + targetUserId + " (" + target.getEmail() + "): " + reason, null);

        return Map.of(
                "sessionId", session.getId(),
                "impersonationToken", impersonationToken,
                "targetUserId", targetUserId,
                "targetRole", session.getTargetRole(),
                "expiresAt", session.getStartTime().plusMinutes(SESSION_DURATION_MINUTES).toString()
        );
    }

    public void stopImpersonation(Long sessionId) {
        ImpersonationSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Impersonation session not found: " + sessionId));
        session.setActive(false);
        session.setEndTime(LocalDateTime.now());
        sessionRepo.save(session);

        auditService.log(session.getImpersonatorId(), "impersonator", "IMPERSONATION_STOP",
                "ImpersonationSession", session.getId(),
                "Stopped impersonating user " + session.getTargetUserId(), null);
    }

    public List<ImpersonationSession> getActiveSessions() {
        return sessionRepo.findByActiveTrue();
    }

    public List<ImpersonationSession> getImpersonationLog() {
        return sessionRepo.findAll();
    }

    public boolean verifyImpersonationToken(String token) {
        try {
            if (!jwtUtil.validateToken(token)) {
                return false;
            }
            String email = jwtUtil.getEmailFromToken(token);
            Long userId = jwtUtil.getUserIdFromToken(token);
            List<ImpersonationSession> active = sessionRepo.findByActiveTrue();
            return active.stream().anyMatch(s ->
                    s.getTargetUserId().equals(userId) &&
                    s.getStartTime().plusMinutes(SESSION_DURATION_MINUTES).isAfter(LocalDateTime.now()));
        } catch (Exception e) {
            return false;
        }
    }
}
