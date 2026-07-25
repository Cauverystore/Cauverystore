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
        auditRepo.save(log);
    }

    public void logLogin(String email, String role, boolean success) {
        AuditLog log = new AuditLog();
        log.setUserEmail(email);
        log.setAction(success ? "LOGIN_SUCCESS" : "LOGIN_FAILED");
        log.setEntity("User");
        log.setDetails("Login attempt for " + email + " with role " + role + ": " + (success ? "success" : "failed"));
        log.setTimestamp(LocalDateTime.now());
        auditRepo.save(log);
    }

    public void logLogout(String email) {
        AuditLog log = new AuditLog();
        log.setUserEmail(email);
        log.setAction("LOGOUT");
        log.setEntity("User");
        log.setDetails("User logged out: " + email);
        log.setTimestamp(LocalDateTime.now());
        auditRepo.save(log);
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
        auditRepo.save(log);
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
        auditRepo.save(log);
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
}
