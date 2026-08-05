import { config } from '../config.js';
import { AppError } from '../utils/errors.js';

// Bounds concurrent sharp jobs so a burst of uploads can't spike CPU on the
// image-service instance. When at capacity, further uploads are rejected with
// 429 instead of queued (client shows a retry prompt).
let active = 0;

export function limitConcurrentUploads(_req, _res, next) {
  if (active >= config.maxConcurrentUploads) {
    return next(new AppError(429, 'Too many uploads in progress. Please try again in a moment.', 'UPLOAD_BUSY'));
  }
  active += 1;
  _res.on('finish', () => { active -= 1; });
  next();
}
