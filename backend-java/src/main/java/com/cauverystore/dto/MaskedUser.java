package com.cauverystore.dto;

import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import java.time.LocalDateTime;

public class MaskedUser {
    private Long id;
    private String fullName;
    private String username;
    private String email;
    private String phone;
    private String address;
    private String profilePicture;
    private String status;
    private Role role;
    private boolean active;
    private Boolean mfaEnabled;
    private LocalDateTime lastLoginAt;
    private Integer failedLoginAttempts;

    public static MaskedUser fromUser(User user, boolean mask) {
        MaskedUser dto = new MaskedUser();
        dto.setId(user.getId());
        dto.setFullName(user.getFullName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhone(mask ? "****" : user.getPhone());
        dto.setAddress(mask ? "****" : user.getAddress());
        dto.setProfilePicture(user.getProfilePicture());
        dto.setStatus(user.getStatus());
        dto.setRole(user.getRole());
        dto.setActive(user.isActive());
        dto.setMfaEnabled(user.getMfaEnabled());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setFailedLoginAttempts(user.getFailedLoginAttempts());
        return dto;
    }

    @java.lang.SuppressWarnings("all")
    public Long getId() {
        return this.id;
    }

    @java.lang.SuppressWarnings("all")
    public String getFullName() {
        return this.fullName;
    }

    @java.lang.SuppressWarnings("all")
    public String getUsername() {
        return this.username;
    }

    @java.lang.SuppressWarnings("all")
    public String getEmail() {
        return this.email;
    }

    @java.lang.SuppressWarnings("all")
    public String getPhone() {
        return this.phone;
    }

    @java.lang.SuppressWarnings("all")
    public String getAddress() {
        return this.address;
    }

    @java.lang.SuppressWarnings("all")
    public String getProfilePicture() {
        return this.profilePicture;
    }

    @java.lang.SuppressWarnings("all")
    public String getStatus() {
        return this.status;
    }

    @java.lang.SuppressWarnings("all")
    public Role getRole() {
        return this.role;
    }

    @java.lang.SuppressWarnings("all")
    public boolean isActive() {
        return this.active;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getMfaEnabled() {
        return this.mfaEnabled;
    }

    @java.lang.SuppressWarnings("all")
    public LocalDateTime getLastLoginAt() {
        return this.lastLoginAt;
    }

    @java.lang.SuppressWarnings("all")
    public Integer getFailedLoginAttempts() {
        return this.failedLoginAttempts;
    }

    @java.lang.SuppressWarnings("all")
    public void setId(final Long id) {
        this.id = id;
    }

    @java.lang.SuppressWarnings("all")
    public void setFullName(final String fullName) {
        this.fullName = fullName;
    }

    @java.lang.SuppressWarnings("all")
    public void setUsername(final String username) {
        this.username = username;
    }

    @java.lang.SuppressWarnings("all")
    public void setEmail(final String email) {
        this.email = email;
    }

    @java.lang.SuppressWarnings("all")
    public void setPhone(final String phone) {
        this.phone = phone;
    }

    @java.lang.SuppressWarnings("all")
    public void setAddress(final String address) {
        this.address = address;
    }

    @java.lang.SuppressWarnings("all")
    public void setProfilePicture(final String profilePicture) {
        this.profilePicture = profilePicture;
    }

    @java.lang.SuppressWarnings("all")
    public void setStatus(final String status) {
        this.status = status;
    }

    @java.lang.SuppressWarnings("all")
    public void setRole(final Role role) {
        this.role = role;
    }

    @java.lang.SuppressWarnings("all")
    public void setActive(final boolean active) {
        this.active = active;
    }

    @java.lang.SuppressWarnings("all")
    public void setMfaEnabled(final Boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    @java.lang.SuppressWarnings("all")
    public void setLastLoginAt(final LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    @java.lang.SuppressWarnings("all")
    public void setFailedLoginAttempts(final Integer failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }
}
