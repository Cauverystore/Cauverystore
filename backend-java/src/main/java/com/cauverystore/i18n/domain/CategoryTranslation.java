package com.cauverystore.i18n.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "category_translations",
       uniqueConstraints = @UniqueConstraint(name = "uq_category_translation", columnNames = {"category_id", "language_code"}))
public class CategoryTranslation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "category_id", nullable = false) private Long categoryId;
    @Column(name = "language_code", nullable = false, length = 10) private String languageCode;
    @Column(nullable = false) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(nullable = false, length = 20) private String status = "DRAFT";
    @Column(nullable = false, length = 20) private String source = "ADMIN";
    @Column(nullable = false) private int version = 1;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();

    protected CategoryTranslation() {}
    public CategoryTranslation(Long categoryId, String languageCode) {
        this.categoryId = categoryId;
        this.languageCode = languageCode;
    }

    @PreUpdate void touch() { this.updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getCategoryId() { return categoryId; }
    public String getLanguageCode() { return languageCode; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public void setSource(String v) { this.source = v; }
    public int getVersion() { return version; }
    public void bumpVersion() { this.version++; }
    public void setUpdatedBy(Long v) { this.updatedBy = v; }
}
