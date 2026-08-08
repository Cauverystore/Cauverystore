package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A notification the rate table is built from, and when a human last confirmed it is still the
 * latest.
 *
 * The provenance used to be a string constant in the loader. That named the notifications but
 * could not be queried, aged, or checked - so "are these the current rates?" had no answer, and
 * a rate change published months ago would have gone on being missed silently.
 *
 * CbicNotificationDetector now fills these rows automatically from CBIC's own updates feed, and
 * records the SHA-256 of the notification PDF as served. Applying a notification is still a
 * human act: the detector can say one exists, but only a person can say they have read it and
 * that the rates charged here match it. So the verification stamps below remain the point -
 * a check that is 90 days old is a finding in itself, however good the detector is.
 */
@Entity
@Table(name = "gst_rate_sources", uniqueConstraints = {
        @UniqueConstraint(name = "uq_rate_source_notification", columnNames = {"notification_number"})
})
public class GstRateSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. "09/2025-Central Tax (Rate)". */
    @Column(name = "notification_number", nullable = false)
    private String notificationNumber;

    @Column(name = "notification_date")
    private LocalDate notificationDate;

    /** The date the rates in it take effect, which is usually later than the notification date. */
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** True when the seed in this repository reflects this notification. */
    @Column(name = "applied", nullable = false)
    private Boolean applied = Boolean.TRUE;

    /** When someone last checked CBIC and confirmed nothing newer had been published. */
    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Column(name = "last_verified_by")
    private String lastVerifiedBy;

    /**
     * SHA-256 of the notification PDF as CBIC served it, and the name they gave it.
     *
     * This is the anchor the whole chain hangs from. A rate is defensible because it traces to
     * a published document, and that argument is only as good as being able to show the
     * document has not changed since it was read. Recorded when the detector finds the
     * notification, so the hash is of what CBIC served that day rather than of whatever is at
     * the URL by the time anyone asks.
     */
    @Column(name = "document_sha256", length = 64)
    private String documentSha256;

    @Column(name = "document_file_name")
    private String documentFileName;

    /** CBIC's own id for the document, so it can be fetched again on demand. */
    @Column(name = "cbic_document_id")
    private Long cbicDocumentId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public GstRateSource() {
    }

    public GstRateSource(String notificationNumber, LocalDate notificationDate,
                         LocalDate effectiveFrom, String description) {
        this.notificationNumber = notificationNumber;
        this.notificationDate = notificationDate;
        this.effectiveFrom = effectiveFrom;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNotificationNumber() { return notificationNumber; }
    public void setNotificationNumber(String n) { this.notificationNumber = n; }
    public LocalDate getNotificationDate() { return notificationDate; }
    public void setNotificationDate(LocalDate d) { this.notificationDate = d; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate d) { this.effectiveFrom = d; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getApplied() { return applied; }
    public void setApplied(Boolean applied) { this.applied = applied; }
    public LocalDateTime getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(LocalDateTime t) { this.lastVerifiedAt = t; }
    public String getLastVerifiedBy() { return lastVerifiedBy; }
    public void setLastVerifiedBy(String by) { this.lastVerifiedBy = by; }
    public String getDocumentSha256() { return documentSha256; }
    public void setDocumentSha256(String documentSha256) { this.documentSha256 = documentSha256; }

    public String getDocumentFileName() { return documentFileName; }
    public void setDocumentFileName(String documentFileName) { this.documentFileName = documentFileName; }

    public Long getCbicDocumentId() { return cbicDocumentId; }
    public void setCbicDocumentId(Long cbicDocumentId) { this.cbicDocumentId = cbicDocumentId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
}
