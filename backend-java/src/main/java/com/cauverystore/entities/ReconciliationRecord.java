package com.cauverystore.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_records")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ReconciliationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sellerGstin;

    @Column(nullable = false)
    private String period;

    @Column(nullable = false)
    private LocalDate generatedDate;

    private Double totalItcAvailable;
    private Double itcClaimed;
    private Double itcReversed;
    private Double itcNotAvailable;
    private Double itcDifference;

    @Column(columnDefinition = "TEXT")
    private String blockedCredits;

    private String status = "OPEN";
    private String sourceFile;
    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSellerGstin() { return sellerGstin; }
    public void setSellerGstin(String sellerGstin) { this.sellerGstin = sellerGstin; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public LocalDate getGeneratedDate() { return generatedDate; }
    public void setGeneratedDate(LocalDate generatedDate) { this.generatedDate = generatedDate; }
    public Double getTotalItcAvailable() { return totalItcAvailable; }
    public void setTotalItcAvailable(Double totalItcAvailable) { this.totalItcAvailable = totalItcAvailable; }
    public Double getItcClaimed() { return itcClaimed; }
    public void setItcClaimed(Double itcClaimed) { this.itcClaimed = itcClaimed; }
    public Double getItcReversed() { return itcReversed; }
    public void setItcReversed(Double itcReversed) { this.itcReversed = itcReversed; }
    public Double getItcNotAvailable() { return itcNotAvailable; }
    public void setItcNotAvailable(Double itcNotAvailable) { this.itcNotAvailable = itcNotAvailable; }
    public Double getItcDifference() { return itcDifference; }
    public void setItcDifference(Double itcDifference) { this.itcDifference = itcDifference; }
    public String getBlockedCredits() { return blockedCredits; }
    public void setBlockedCredits(String blockedCredits) { this.blockedCredits = blockedCredits; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
