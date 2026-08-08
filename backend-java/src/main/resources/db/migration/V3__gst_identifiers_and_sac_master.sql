-- GST identifiers on customers and addresses, plus the SAC rate master.
--
-- This application runs with spring.jpa.hibernate.ddl-auto=update and Flyway disabled, so
-- Hibernate creates these from the entities at startup and this file does not execute. It is
-- kept because "what does the schema look like" should be answerable without reading Java, and
-- because a DBA applying the change by hand on a restricted database needs the statements.
--
-- Written to be safe to run against a database Hibernate has already updated: every statement
-- is IF NOT EXISTS.

-- Customers -----------------------------------------------------------------------------

-- Optional. A registered buyer's PAN is already inside their GSTIN at characters 3-12, so this
-- is for buyers who hold a PAN but no GST registration.
ALTER TABLE users ADD COLUMN IF NOT EXISTS pan_number VARCHAR(10);

-- Notification 34/2023, effective 01-10-2023: lets a person supplying goods intra-state through
-- an e-commerce operator trade without full registration while under the threshold. It is an
-- alternative to a GSTIN, not an addition.
ALTER TABLE users ADD COLUMN IF NOT EXISTS enrolment_number VARCHAR(20);

-- Addresses -----------------------------------------------------------------------------

-- Held per address rather than per customer, because GST registration is per state: a company
-- trading in three states holds three GSTINs, and which applies depends on the address billed.
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS gstin VARCHAR(15);

-- Invoices ------------------------------------------------------------------------------

-- Snapshotted at issue, like every other party identifier on an invoice, so a customer editing
-- their profile later cannot rewrite who an issued invoice was made out to.
ALTER TABLE gst_invoices ADD COLUMN IF NOT EXISTS buyer_pan VARCHAR(10);
ALTER TABLE gst_invoices ADD COLUMN IF NOT EXISTS buyer_enrolment_number VARCHAR(20);

-- Services ------------------------------------------------------------------------------

-- Separate from gst_rate_master on purpose. Goods rates are value-banded, packaging-split and
-- resolved by walking chapter to tariff item; none of that applies to a SAC, which carries one
-- rate. Sharing a table would make every goods lookup step over service rows.
CREATE TABLE IF NOT EXISTS sac_master (
    id              BIGSERIAL PRIMARY KEY,
    sac_code        VARCHAR(10)  NOT NULL,
    description     TEXT,
    gst_rate        DECIMAL(5,2) NOT NULL,
    effective_from  DATE         NOT NULL,
    effective_to    DATE,
    source          TEXT,
    -- VERIFIED or UNVERIFIED. An unapproved service rate is not charged, for the same reason an
    -- unapproved goods rate is not.
    status          VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    notes           TEXT
);

CREATE INDEX IF NOT EXISTS idx_sac_master_effective ON sac_master (sac_code, effective_from);

-- No unique constraint on sac_code: a rate that changes produces a second row for the same code
-- with its own effective dates, and the old one must survive so last year's invoices still
-- resolve to last year's rate.

-- On foreign keys ------------------------------------------------------------------------
--
-- The brief asked for FK relationships from products to HSN and services to SAC. Deliberately
-- not added:
--
--   products.hsn_code -> hsn_master.hsn_code would be reasonable in isolation, but the
--   catalogue already holds codes such as '123' and '1212' that are not in the master. A
--   foreign key would make those rows unsaveable, so a seller could not correct the bad code
--   without a DBA deleting their product first. The application already refuses to publish or
--   invoice an unknown code - HsnClassificationService.validate - which stops the harm without
--   trapping anybody.
--
--   Services are not rows in a table here; a SAC appears on an invoice line. There is nothing
--   to point a key from.
