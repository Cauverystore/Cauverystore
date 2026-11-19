package com.cauverystore.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One step a product took through the approval workflow: who moved it, from where, to where, and why. */
@Getter
@Setter
@Entity
@Table(name = "product_compliance_events",
        indexes = @Index(name = "idx_product_compliance_events_product", columnList = "product_id"))
public class ProductComplianceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(length = 30)
    private String action;
    @Column(length = 30)
    private String fromStatus;
    @Column(length = 30)
    private String toStatus;
    private Integer score;
    @Column(columnDefinition = "TEXT")
    private String note;
    @Column(length = 255)
    private String actor;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
