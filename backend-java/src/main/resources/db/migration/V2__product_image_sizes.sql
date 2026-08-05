-- CauveryStore: add processed-image size columns to product_images.
-- The Node image-service (POST /upload-image) produces WebP sizes via sharp:
--   thumbnail_url (150px), preview_url (600px), zoom_url (1200px), original_url (full-res).
-- These are stored on the existing product_images row created by the Java attach
-- endpoint (POST /api/admin/products/{id}/images/url), which maps:
--   url          <- preview_url   (primary display image)
--   thumb_url    <- thumbnail_url
-- Run manually if Hibernate ddl-auto is not set to update.

ALTER TABLE product_images ADD COLUMN IF NOT EXISTS preview_url TEXT;
ALTER TABLE product_images ADD COLUMN IF NOT EXISTS zoom_url TEXT;
ALTER TABLE product_images ADD COLUMN IF NOT EXISTS original_url TEXT;
