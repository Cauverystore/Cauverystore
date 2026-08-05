-- CauveryStore: product_images schema migration
-- Run against the store's Postgres database (PostgreSQL 13+).
--
-- The Spring Boot app's products.id is BIGSERIAL (Long). If you run this
-- against that database, use the BIGINT variant below. The UUID variant
-- matches the image-service spec where products.id is also UUID.

CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- provides gen_random_uuid()

-- ---- UUID variant (products.id is UUID) ----
CREATE TABLE IF NOT EXISTS product_images (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id    UUID NOT NULL REFERENCES products(id),
    original_url  TEXT NOT NULL,
    thumbnail_url TEXT NOT NULL,
    preview_url   TEXT NOT NULL,
    zoom_url      TEXT NOT NULL,
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_product_images_product_id ON product_images (product_id);

-- ---- BIGINT variant (Spring Boot: products.id is BIGSERIAL) ----
-- CREATE TABLE IF NOT EXISTS product_images (
--     id            BIGSERIAL PRIMARY KEY,
--     product_id    BIGINT NOT NULL REFERENCES products(id),
--     original_url  TEXT NOT NULL,
--     thumbnail_url TEXT NOT NULL,
--     preview_url   TEXT NOT NULL,
--     zoom_url      TEXT NOT NULL,
--     created_at    TIMESTAMP DEFAULT NOW()
-- );
