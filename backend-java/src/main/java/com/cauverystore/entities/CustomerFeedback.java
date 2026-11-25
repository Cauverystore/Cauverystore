package com.cauverystore.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Quick feedback from the "Feedback" tab shown on every shop page. Anyone can send it (no
 * sign-in); name and email are optional. An optional photo is kept in the row itself (shrunk in
 * the browser first, capped at 1 MB here) so a stranger's upload is never served publicly: only
 * staff can fetch it, through /api/admin/feedback/{id}/photo.
 */
@Entity
@Table(name = "customer_feedback")
public class CustomerFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** FEEDBACK, COMPLAINT or SUGGESTION. */
    @Column(nullable = false, length = 20)
    private String type;
    @Column(nullable = false, length = 2000)
    private String message;
    @Column(length = 100)
    private String name;
    @Column(length = 200)
    private String email;
    /** The shop page the customer was on, e.g. /product/12. */
    @Column(length = 500)
    private String pageUrl;
    /** Set when the customer was signed in. */
    private Long userId;
    @JsonIgnore
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "bytea")
    private byte[] photo;
    @Column(length = 20)
    private String photoType;
    /** NEW or DONE. */
    @Column(nullable = false, length = 20)
    private String status = "NEW";
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isHasPhoto() { return photoType != null; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public byte[] getPhoto() { return photo; }
    public void setPhoto(byte[] photo) { this.photo = photo; }
    public String getPhotoType() { return photoType; }
    public void setPhotoType(String photoType) { this.photoType = photoType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
