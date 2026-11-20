package com.cauverystore.service;

import com.cauverystore.entities.AuditLog;
import com.cauverystore.entities.Role;
import com.cauverystore.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {
    private final AuditLogRepository auditRepo;

    public AuditService(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    public void log(Long userId, String userEmail, String action, String entity, Long entityId, String details, String ipAddress) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setUserEmail(userEmail);
        log.setAction(action);
        log.setEntity(entity);
        log.setEntityId(entityId);
        log.setDetails(details);
        log.setIpAddress(ipAddress);
        log.setTimestamp(LocalDateTime.now());
        save(log);
    }

    public void logLogin(String email, String role, boolean success) {
        AuditLog log = new AuditLog();
        log.setUserEmail(email);
        log.setAction(success ? "LOGIN_SUCCESS" : "LOGIN_FAILED");
        log.setEntity("User");
        log.setDetails("Login attempt for " + email + " with role " + role + ": " + (success ? "success" : "failed"));
        log.setTimestamp(LocalDateTime.now());
        save(log);
    }

    public void logLogout(String email) {
        AuditLog log = new AuditLog();
        log.setUserEmail(email);
        log.setAction("LOGOUT");
        log.setEntity("User");
        log.setDetails("User logged out: " + email);
        log.setTimestamp(LocalDateTime.now());
        save(log);
    }

    public void logSuspend(String performedBy, Long actorId, Long targetUserId, String targetRole, String targetEmail) {
        AuditLog log = new AuditLog();
        log.setUserId(actorId);
        log.setUserEmail(performedBy);
        log.setAction("USER_SUSPENDED");
        log.setEntity("User");
        log.setEntityId(targetUserId);
        log.setDetails("User " + targetEmail + " (" + targetRole + ") suspended by " + performedBy);
        log.setTimestamp(LocalDateTime.now());
        save(log);
    }

    public void logRevoke(String performedBy, Long actorId, Long targetUserId, String targetRole, String targetEmail) {
        AuditLog log = new AuditLog();
        log.setUserId(actorId);
        log.setUserEmail(performedBy);
        log.setAction("USER_REVOKED");
        log.setEntity("User");
        log.setEntityId(targetUserId);
        log.setDetails("User " + targetEmail + " (" + targetRole + ") suspension revoked by " + performedBy);
        log.setTimestamp(LocalDateTime.now());
        save(log);
    }

    public void logRoleChange(String changedBy, Long targetUserId, Role oldRole, Role newRole) {
        AuditLog log = new AuditLog();
        log.setUserId(targetUserId);
        log.setUserEmail(changedBy);
        log.setAction("ROLE_CHANGE");
        log.setEntity("User");
        log.setEntityId(targetUserId);
        log.setDetails("Role changed from " + oldRole + " to " + newRole + " by " + changedBy);
        log.setTimestamp(LocalDateTime.now());
        save(log);
    }

    public void logAccountAction(String action, String performedBy, Long targetUserId) {
        AuditLog log = new AuditLog();
        log.setUserId(targetUserId);
        log.setUserEmail(performedBy);
        log.setAction(action);
        log.setEntity("User");
        log.setEntityId(targetUserId);
        log.setDetails(action + " performed on user " + targetUserId + " by " + performedBy);
        log.setTimestamp(LocalDateTime.now());
        save(log);
    }

    public List<AuditLog> getAll() {
        return auditRepo.findAllByOrderByTimestampDesc();
    }

    public List<AuditLog> getByDateRange(LocalDateTime from, LocalDateTime to) {
        return auditRepo.findByTimestampBetweenOrderByTimestampDesc(from, to);
    }

    public Page<AuditLog> getPage(Pageable pageable) {
        return auditRepo.findAll(pageable);
    }

    public List<AuditLog> getByUser(Long userId) {
        return auditRepo.findByUserIdOrderByTimestampDesc(userId);
    }

    /**
     * One page of the audit log, newest first, narrowed by any of: exact action, part of the
     * email (any case), and a date range (both days included). Blank filters are ignored.
     */
    public Page<AuditLog> search(String action, String email, java.time.LocalDate from,
                                 java.time.LocalDate to, int page, int size) {
        org.springframework.data.jpa.domain.Specification<AuditLog> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> where = new java.util.ArrayList<>();
            if (action != null && !action.isBlank()) {
                where.add(cb.equal(root.get("action"), action.trim()));
            }
            if (email != null && !email.isBlank()) {
                where.add(cb.like(cb.lower(root.get("userEmail")),
                        "%" + email.trim().toLowerCase().replace("%", "").replace("_", "") + "%"));
            }
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from.atStartOfDay()));
            }
            if (to != null) {
                where.add(cb.lessThan(root.get("timestamp"), to.plusDays(1).atStartOfDay()));
            }
            return cb.and(where.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        int safeSize = Math.min(Math.max(size, 1), 200);
        return auditRepo.findAll(spec, org.springframework.data.domain.PageRequest.of(Math.max(page, 0), safeSize,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "timestamp")));
    }

    public List<String> listActions() {
        return auditRepo.findDistinctActions();
    }

    /** Every entry records the visitor's address when it was made during a web request. */
    private void save(AuditLog log) {
        if (log.getIpAddress() == null) {
            try {
                log.setIpAddress(com.cauverystore.util.ClientIp.current());
            } catch (RuntimeException ignored) {
                // Outside a request (scheduled jobs) there is no address to record.
            }
        }
        auditRepo.save(log);
    }
}
