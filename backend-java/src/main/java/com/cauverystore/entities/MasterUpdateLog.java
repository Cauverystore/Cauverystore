package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Audit trail of every master-data load, so a rate or code change can be traced to a version. */
@Entity
@Table(name = "master_update_logs")
public class MasterUpdateLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "version")
    private String version;

    @Column(name = "download_date")
    private LocalDateTime downloadDate;

    @Column(name = "rows_loaded")
    private Integer rowsLoaded;

    @Column(name = "rows_inserted")
    private Integer rowsInserted;

    @Column(name = "rows_updated")
    private Integer rowsUpdated;

    /** Human-readable summary of what changed vs the previous load. */
    @Column(name = "changes_detected", columnDefinition = "TEXT")
    private String changesDetected;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (downloadDate == null) downloadDate = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public LocalDateTime getDownloadDate() { return downloadDate; }
    public void setDownloadDate(LocalDateTime downloadDate) { this.downloadDate = downloadDate; }

    public Integer getRowsLoaded() { return rowsLoaded; }
    public void setRowsLoaded(Integer rowsLoaded) { this.rowsLoaded = rowsLoaded; }

    public Integer getRowsInserted() { return rowsInserted; }
    public void setRowsInserted(Integer rowsInserted) { this.rowsInserted = rowsInserted; }

    public Integer getRowsUpdated() { return rowsUpdated; }
    public void setRowsUpdated(Integer rowsUpdated) { this.rowsUpdated = rowsUpdated; }

    public String getChangesDetected() { return changesDetected; }
    public void setChangesDetected(String changesDetected) { this.changesDetected = changesDetected; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
