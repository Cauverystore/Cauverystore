-- Append-only GST audit trail.
--
-- Runs automatically (Flyway, after Hibernate - see FlywayAfterHibernateConfig). Hibernate
-- creates the table from GstAuditEvent; the CREATE TABLE here is for a database where it has
-- not, and is a no-op otherwise.
--
-- Each row carries the SHA-256 of the row before it, so editing or deleting a row breaks the
-- chain and GET /api/admin/gst/audit-events/verify reports where. The trigger below goes further
-- and refuses UPDATE, DELETE and TRUNCATE outright, for everyone including the app's own user.
-- Only someone who can drop the trigger (the database owner) can change history, and doing so
-- still shows up in the chain check.

CREATE TABLE IF NOT EXISTS gst_audit_events (
    id           BIGSERIAL PRIMARY KEY,
    occurred_at  TIMESTAMP    NOT NULL,
    event_type   VARCHAR(60)  NOT NULL,
    entity_type  VARCHAR(40)  NOT NULL,
    entity_ref   VARCHAR(100),
    seller_id    BIGINT,
    actor        VARCHAR(255) NOT NULL,
    payload      TEXT,
    prev_hash    VARCHAR(64)  NOT NULL,
    hash         VARCHAR(64)  NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_gst_audit_events_hash ON gst_audit_events (hash);
-- Two writers that read the same last row would both link to it; this makes the second fail
-- (and retry) instead of forking the chain.
CREATE UNIQUE INDEX IF NOT EXISTS ux_gst_audit_events_prev_hash ON gst_audit_events (prev_hash);
CREATE INDEX IF NOT EXISTS ix_gst_audit_events_entity ON gst_audit_events (entity_type, entity_ref);

CREATE OR REPLACE FUNCTION gst_audit_events_refuse_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'gst_audit_events is append-only: % refused', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gst_audit_events_no_update_delete ON gst_audit_events;
CREATE TRIGGER gst_audit_events_no_update_delete
    BEFORE UPDATE OR DELETE ON gst_audit_events
    FOR EACH ROW EXECUTE FUNCTION gst_audit_events_refuse_change();

DROP TRIGGER IF EXISTS gst_audit_events_no_truncate ON gst_audit_events;
CREATE TRIGGER gst_audit_events_no_truncate
    BEFORE TRUNCATE ON gst_audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION gst_audit_events_refuse_change();
