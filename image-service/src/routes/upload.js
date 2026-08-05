import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import { upload } from '../middleware/upload.js';
import { validateImage } from '../middleware/validateImage.js';
import { processImage } from '../services/imageProcessor.js';
import { saveFile, deleteFile, buildObjectName } from '../services/storage.js';
import { createProductImage } from '../services/productImageRepo.js';
import { AppError } from '../utils/errors.js';

const router = Router();

// POST /upload-image  (multipart/form-data: image=<file>, product_id=<id>)
router.post('/upload-image', upload.single('image'), validateImage, async (req, res, next) => {
  try {
    const productId = String(req.body.product_id || '').trim();
    if (!productId || productId.length > 64 || !/^[A-Za-z0-9_-]+$/.test(productId)) {
      throw new AppError(400, 'A valid product_id (alphanumeric, max 64 chars) is required.', 'INVALID_PRODUCT_ID');
    }

    // 1. Resize + convert to WebP + strip EXIF.
    const variants = await processImage(req.file.buffer);

    // 2. Persist with UUID names (sanitization): <uuid>_<size>.webp
    const saved = {};
    for (const kind of ['original', 'zoom', 'preview', 'thumbnail']) {
      saved[`${kind}Url`] = await saveFile(buildObjectName(kind), variants[kind]);
    }

    // 3. Record in product_images. On failure roll the files back and error out.
    try {
      await createProductImage({ id: randomUUID(), productId, ...saved });
    } catch (dbErr) {
      for (const url of Object.values(saved)) {
        const match = url.match(/\/uploads\/([^/]+)$/);
        if (match) await deleteFile(match[1]);
      }
      throw new AppError(502, `Failed to persist image record: ${dbErr.message}`, 'DB_ERROR');
    }

    // 4. Respond.
    res.status(201).json({
      product_id: productId,
      thumbnail_url: saved.thumbnailUrl,
      preview_url: saved.previewUrl,
      zoom_url: saved.zoomUrl,
      original_url: saved.originalUrl,
    });
  } catch (err) {
    next(err);
  }
});

export default router;
