package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A record that someone classified a product in a given category under a given HSN code.
 *
 * Classifying goods is judgement the platform cannot do for a seller - the code depends on what
 * the thing actually is, which only they know. But it is judgement that repeats: the second bag
 * of rice belongs under the same code as the first. So each assignment is kept and offered back
 * the next time someone adds a product to that category, ranked by how often it has been chosen.
 *
 * This carries no tax rate on purpose. It records classification only; what that classification
 * costs comes from GstRateMaster, which is sourced from the CBIC notifications. Letting a
 * remembered rate creep in here would mean the store eventually taxed itself from its own
 * history rather than from the law.
 */
@Entity
@Table(name = "hsn_assignments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_hsn_assignment", columnNames = {"category_id", "hsn_code"})
}, indexes = {
        @Index(name = "idx_hsn_assignment_category", columnList = "category_id")
})
public class HsnAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null means the assignment was made on a product with no category. */
    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "hsn_code", length = 8, nullable = false)
    private String hsnCode;

    /** How many products in this category have been given this code - the ranking signal. */
    @Column(name = "times_used", nullable = false)
    private Integer timesUsed = 0;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** The last person to choose it, so a questionable suggestion can be traced back. */
    @Column(name = "last_used_by")
    private String lastUsedBy;

    public void recordUse(String by) {
        this.timesUsed = (this.timesUsed == null ? 0 : this.timesUsed) + 1;
        this.lastUsedAt = LocalDateTime.now();
        this.lastUsedBy = by;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }

    public Integer getTimesUsed() { return timesUsed; }
    public void setTimesUsed(Integer timesUsed) { this.timesUsed = timesUsed; }

    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public String getLastUsedBy() { return lastUsedBy; }
    public void setLastUsedBy(String lastUsedBy) { this.lastUsedBy = lastUsedBy; }
}
