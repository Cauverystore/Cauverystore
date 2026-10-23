package com.cauverystore.i18n.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One product's text in one language. The original text stays on Product; this only holds translations. */
@Entity
@Table(name = "product_translations",
       uniqueConstraints = @UniqueConstraint(name = "uq_product_translation", columnNames = {"product_id", "language_code"}))
public class ProductTranslation {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(nullable = false, length = 500) private String name;
    @Column(length = 520) private String slug;
    @Column(name = "short_description", columnDefinition = "TEXT") private String shortDescription;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "meta_title", length = 255) private String metaTitle;
    @Column(name = "meta_description", columnDefinition = "TEXT") private String metaDescription;

    @Column(nullable = false, length = 20) private String status = DRAFT;
    @Column(nullable = false, length = 20) private String source = "ADMIN";
    @Column(nullable = false) private int version = 1;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();

    protected ProductTranslation() {}
    public ProductTranslation(Long productId, String languageCode) {
        this.productId = productId;
        this.languageCode = languageCode;
    }

    @PreUpdate void touch() { this.updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getLanguageCode() { return languageCode; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getSlug() { return slug; }
    public void setSlug(String v) { this.slug = v; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String v) { this.shortDescription = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getMetaTitle() { return metaTitle; }
    public void setMetaTitle(String v) { this.metaTitle = v; }
    public String getMetaDescription() { return metaDescription; }
    public void setMetaDescription(String v) { this.metaDescription = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public int getVersion() { return version; }
    public void bumpVersion() { this.version++; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long v) { this.updatedBy = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
