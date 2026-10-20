package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * An emailed invitation to join a seller's team.
 *
 * Only a hash of the token is stored, so the database alone cannot be used to accept one.
 * It works once, for seven days, and only for someone signed in with the invited email address -
 * the store's sign-up already proves they own that address.
 */
@Entity
@Table(name = "seller_invitations", indexes = {
        @Index(name = "idx_seller_invitations_seller", columnList = "seller_id,status"),
        @Index(name = "idx_seller_invitations_token", columnList = "token_hash", unique = true)
})
public class SellerInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** PENDING, ACCEPTED or REVOKED. An expired one is PENDING past its expiry. */
    @Column(name = "status", length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "invited_by", nullable = false)
    private Long invitedBy;

    @Column(name = "accepted_user_id")
    private Long acceptedUserId;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getInvitedBy() { return invitedBy; }
    public void setInvitedBy(Long invitedBy) { this.invitedBy = invitedBy; }
    public Long getAcceptedUserId() { return acceptedUserId; }
    public void setAcceptedUserId(Long acceptedUserId) { this.acceptedUserId = acceptedUserId; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
