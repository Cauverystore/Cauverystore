-- CauveryStore product compliance - phase 1
-- spring.flyway.enabled=false and spring.jpa.hibernate.ddl-auto=update, so Hibernate creates these
-- tables and columns itself when the new backend starts. This script is the same schema written
-- out, for review, for a database where ddl-auto is off, or to run by hand BEFORE deploying.
-- Safe to run twice (IF NOT EXISTS everywhere). It only ADDS; nothing is changed or deleted.

-- MRP on products (the shop already reads product.mrp; it had no column)
ALTER TABLE products ADD COLUMN IF NOT EXISTS mrp DOUBLE PRECISION;

-- Which category-specific fields a category's products need
ALTER TABLE categories ADD COLUMN IF NOT EXISTS compliance_type VARCHAR(20);

CREATE TABLE IF NOT EXISTS product_compliance (
    id                           BIGSERIAL PRIMARY KEY,
    product_id                   BIGINT NOT NULL,
    -- Legal Metrology
    net_quantity                 NUMERIC(14,3),
    gross_quantity               NUMERIC(14,3),
    quantity_unit                VARCHAR(20),
    -- Inventory
    min_order_qty                INTEGER,
    max_order_qty                INTEGER,
    -- Manufacturer / packer / importer
    manufacturer_name            VARCHAR(255),
    manufacturer_address         TEXT,
    packer_same_as_manufacturer  BOOLEAN,
    packer_name                  VARCHAR(255),
    packer_address               TEXT,
    importer_name                VARCHAR(255),
    importer_address             TEXT,
    -- Origin and customer care
    state_of_origin              VARCHAR(100),
    customer_care_name           VARCHAR(255),
    customer_care_phone          VARCHAR(20),
    customer_care_email          VARCHAR(255),
    customer_care_address        TEXT,
    -- Dates and traceability
    mfg_date                     DATE,
    best_before_date             DATE,
    expiry_date                  DATE,
    batch_number                 VARCHAR(100),
    lot_number                   VARCHAR(100),
    serial_number                VARCHAR(100),
    -- Food
    fssai_licence_number         VARCHAR(20),
    ingredients                  TEXT,
    nutrition_info               TEXT,
    allergens                    TEXT,
    food_preference              VARCHAR(10),
    storage_instructions         TEXT,
    -- Electronics / toys
    service_centre_details       TEXT,
    bis_number                   VARCHAR(30),
    voltage                      VARCHAR(50),
    power_rating                 VARCHAR(50),
    -- Cosmetics
    cosmetic_licence_number      VARCHAR(100),
    -- Medical
    cdsco_registration_number    VARCHAR(40),
    prescription_required        BOOLEAN,
    warnings                     TEXT,
    disclaimer                   TEXT,
    -- Toys
    age_group                    VARCHAR(50),
    safety_warning               TEXT,
    -- Clothing
    fabric                       VARCHAR(255),
    wash_care                    TEXT,
    size_chart                   TEXT,
    -- Last check
    compliance_score             INTEGER,
    missing_fields               TEXT,
    publishable                  BOOLEAN,
    last_checked_at              TIMESTAMP,
    -- Workflow
    status                       VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    status_note                  TEXT,
    -- Audit
    created_by                   VARCHAR(255),
    created_date                 TIMESTAMP,
    updated_by                   VARCHAR(255),
    updated_date                 TIMESTAMP,
    version                      BIGINT,
    deleted_flag                 BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_product_compliance_product UNIQUE (product_id)
);
CREATE INDEX IF NOT EXISTS idx_product_compliance_status ON product_compliance (status);
CREATE INDEX IF NOT EXISTS idx_product_compliance_score  ON product_compliance (compliance_score);

CREATE TABLE IF NOT EXISTS product_compliance_events (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT NOT NULL,
    action      VARCHAR(30),
    from_status VARCHAR(30),
    to_status   VARCHAR(30),
    score       INTEGER,
    note        TEXT,
    actor       VARCHAR(255),
    created_at  TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_product_compliance_events_product ON product_compliance_events (product_id);
