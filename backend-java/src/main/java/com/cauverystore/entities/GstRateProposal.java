package com.cauverystore.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One rate change read from a CBIC notification, waiting for a person to decide on it.
 *
 * Kept apart from gst_rate_master on purpose. A row in that table can be charged to a customer;
 * a row here cannot, whatever it says. The reader that fills this table works by rule from the
 * text of a PDF, and a misread code or schedule has to cost a rejected proposal rather than a
 * wrong invoice - so nothing moves from here to the rate table except by someone accepting it.
 *
 * A proposal with no HSN code is a clause the reader could not turn into a rate at all - a whole
 * schedule omitted, a description reworded. It is listed so that it is not lost, and can only be
 * acknowledged, never accepted.
 */
@Entity
@Table(name = "gst_rate_proposals", indexes = {
        @Index(name = "idx_gst_proposal_status", columnList = "status"),
        @Index(name = "idx_gst_proposal_source", columnList = "source_id")
})
public class GstRateProposal {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    /** Read and dealt with by hand: used for clauses that carry no rate to accept. */
    public static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The gst_rate_sources row for the notification this came from. */
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "notification_number", nullable = false)
    private String notificationNumber;

    /** INSERT, SUBSTITUTE_CODES, OMIT or UNREAD - see CbicNotificationReader.Operation. */
    @Column(name = "operation", length = 20, nullable = false)
    private String operation;

    @Column(name = "schedule_name", length = 10)
    private String schedule;

    @Column(name = "serial_no", length = 40)
    private String serialNo;

    @Column(name = "hsn_code", length = 8)
    private String hsnCode;

    /** The rate to the customer - both halves, not the CGST half the schedule prints. */
    @Column(name = "proposed_rate")
    private Double proposedRate;

    /** What was being charged for this code when the proposal was drafted. */
    @Column(name = "rate_when_drafted")
    private Double rateWhenDrafted;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** The words of the notification this was read from, so it can be checked without the PDF. */
    @Column(name = "clause", columnDefinition = "TEXT")
    private String clause;

    @Column(name = "needs_attention", nullable = false)
    private boolean needsAttention;

    @Column(name = "attention_reason", columnDefinition = "TEXT")
    private String attentionReason;

    @Column(name = "status", length = 20, nullable = false)
    private String status = STATUS_PENDING;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getNotificationNumber() { return notificationNumber; }
    public void setNotificationNumber(String notificationNumber) { this.notificationNumber = notificationNumber; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }
    public Double getProposedRate() { return proposedRate; }
    public void setProposedRate(Double proposedRate) { this.proposedRate = proposedRate; }
    public Double getRateWhenDrafted() { return rateWhenDrafted; }
    public void setRateWhenDrafted(Double rateWhenDrafted) { this.rateWhenDrafted = rateWhenDrafted; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getClause() { return clause; }
    public void setClause(String clause) { this.clause = clause; }
    public boolean isNeedsAttention() { return needsAttention; }
    public void setNeedsAttention(boolean needsAttention) { this.needsAttention = needsAttention; }
    public String getAttentionReason() { return attentionReason; }
    public void setAttentionReason(String attentionReason) { this.attentionReason = attentionReason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
