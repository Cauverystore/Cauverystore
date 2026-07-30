package com.cauverystore.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseEntity {
    @Column(nullable = false)
    private String email;
    @Column(nullable = false, unique = true)
    private String token;
    private LocalDateTime expiresAt;
    private boolean used = false;
    private LocalDateTime lastRequestedAt;

    public String getEmail() {
        return this.email;
    }

    public String getToken() {
        return this.token;
    }

    public LocalDateTime getExpiresAt() {
        return this.expiresAt;
    }

    public boolean isUsed() {
        return this.used;
    }

    public LocalDateTime getLastRequestedAt() {
        return this.lastRequestedAt;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public void setToken(final String token) {
        this.token = token;
    }

    public void setExpiresAt(final LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setUsed(final boolean used) {
        this.used = used;
    }

    public void setLastRequestedAt(final LocalDateTime lastRequestedAt) {
        this.lastRequestedAt = lastRequestedAt;
    }
}
