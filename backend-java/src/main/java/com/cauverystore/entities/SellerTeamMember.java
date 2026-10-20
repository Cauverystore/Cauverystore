package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A person working in a seller's account, with a role.
 *
 * The seller account itself (seller_id, the owner's user id) keeps owning every product, order
 * and invoice exactly as before - a team member acts for it, they do not own anything. That is
 * what keeps existing data untouched. One user can be on several sellers' teams, which is how one
 * login reaches more than one store.
 */
@Entity
@Table(name = "seller_team_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_seller_team_member", columnNames = {"seller_id", "user_id"}),
        indexes = @Index(name = "idx_seller_team_members_user", columnList = "user_id,status"))
public class SellerTeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    /** ACTIVE or REMOVED. Removed rows stay, so the history of who had access remains. */
    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @Column(name = "invited_by")
    private Long invitedBy;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "removed_by")
    private Long removedBy;

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getInvitedBy() { return invitedBy; }
    public void setInvitedBy(Long invitedBy) { this.invitedBy = invitedBy; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    public LocalDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(LocalDateTime removedAt) { this.removedAt = removedAt; }
    public Long getRemovedBy() { return removedBy; }
    public void setRemovedBy(Long removedBy) { this.removedBy = removedBy; }
}
