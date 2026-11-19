package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A GST notification or circular that changes the rules rather than the rates.
 *
 * Rate notifications go to GstRateSource, because they change what is charged. Everything else
 * CBIC publishes - Central Tax notifications (rules, returns, e-invoicing), their Integrated and
 * Union Territory twins, cess notifications and circulars - lands here, so that someone reads
 * each one and records whether the store has to do anything about it. Nothing here changes how
 * the store behaves; the record is the point.
 */
@Entity
@Table(name = "gst_rule_updates", uniqueConstraints = {
        @UniqueConstraint(name = "uq_rule_update_document", columnNames = {"kind", "cbic_document_id"})
})
public class GstRuleUpdate {

    public static final String KIND_NOTIFICATION = "NOTIFICATION";
    public static final String KIND_CIRCULAR = "CIRCULAR";
    /**
     * A GSTN advisory from gst.gov.in "News and Updates": portal and procedure changes (new
     * e-invoice supply types, return forms opening, Council FAQs). Not law, but often the first
     * notice of a change the store's systems must follow. Its id is GSTN's news id, kept in
     * cbic_document_id; the kind keeps it apart from CBIC ids.
     */
    public static final String KIND_ADVISORY = "ADVISORY";

    /** Found, not yet read. */
    public static final String STATUS_NEW = "NEW";
    /** Read; the store needs to change something. */
    public static final String STATUS_ACTION_NEEDED = "ACTION_NEEDED";
    /** Read; the store already complies or has been changed. */
    public static final String STATUS_REVIEWED = "REVIEWED";
    /** Read; does not apply to this business. */
    public static final String STATUS_NOT_RELEVANT = "NOT_RELEVANT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NOTIFICATION, CIRCULAR or ADVISORY - each comes from a different place. */
    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    /** The portal's category, e.g. "Central Tax" or "Circulars CGST". */
    @Column(name = "category")
    private String category;

    /** e.g. "05/2026-Central Tax" or "257/03/2026-GST". */
    @Column(name = "number", nullable = false)
    private String number;

    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    /** CBIC's own id, used to fetch the PDF again. */
    @Column(name = "cbic_document_id", nullable = false)
    private Long cbicDocumentId;

    @Column(name = "document_path")
    private String documentPath;

    /** Advisories only: the advisory's text, as plain text, so it can be read on the screen. */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_NEW;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getIssuedOn() { return issuedOn; }
    public void setIssuedOn(LocalDate issuedOn) { this.issuedOn = issuedOn; }
    public Long getCbicDocumentId() { return cbicDocumentId; }
    public void setCbicDocumentId(Long cbicDocumentId) { this.cbicDocumentId = cbicDocumentId; }
    public String getDocumentPath() { return documentPath; }
    public void setDocumentPath(String documentPath) { this.documentPath = documentPath; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(LocalDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
