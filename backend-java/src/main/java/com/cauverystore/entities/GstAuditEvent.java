package com.cauverystore.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * One entry in the append-only GST audit trail. Never updated or deleted: see
 * V7__gst_audit_events_append_only.sql for the trigger that enforces it, and GstAuditService for
 * the hash chain that makes any change visible.
 */
@Entity
@Immutable
@Table(name = "gst_audit_events", indexes = {
        @Index(name = "ix_gst_audit_events_entity", columnList = "entity_type, entity_ref")
})
public class GstAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_ref", length = 100)
    private String entityRef;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(nullable = false)
    private String actor;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @Column(nullable = false, length = 64)
    private String hash;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityRef() { return entityRef; }
    public void setEntityRef(String entityRef) { this.entityRef = entityRef; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getPrevHash() { return prevHash; }
    public void setPrevHash(String prevHash) { this.prevHash = prevHash; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
}
