import pg from 'pg';
import { config } from '../config.js';

// When DATABASE_URL is set, image records persist to Postgres. Without it the
// service still works (files saved, DB write skipped with a warning) so local
// smoke tests don't need a database.
const pool = config.databaseUrl ? new pg.Pool({ connectionString: config.databaseUrl }) : null;

export async function createProductImage({ id, productId, originalUrl, thumbnailUrl, previewUrl, zoomUrl }) {
  if (!pool) {
    console.warn('[image-service] DATABASE_URL not set; skipping product_images insert.');
    return null;
  }
  const { rows } = await pool.query(
    `INSERT INTO product_images (id, product_id, original_url, thumbnail_url, preview_url, zoom_url, created_at)
     VALUES ($1, $2, $3, $4, $5, $6, NOW())
     RETURNING id`,
    [id, productId, originalUrl, thumbnailUrl, previewUrl, zoomUrl]
  );
  return rows[0];
}
