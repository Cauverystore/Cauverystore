import { config } from '../config.js';
import { AppError } from '../utils/errors.js';

// Optional shared-secret gate. When UPLOAD_SECRET is set, the caller must send
// it in the X-Upload-Secret header (nginx proxy_set_header forwards it). This
// stops anonymous callers hitting the service even if its port is exposed.
export function uploadSecret(_req, res, next) {
  if (!config.uploadSecret) return next();
  const supplied = _req.get('x-upload-secret');
  if (!supplied || supplied !== config.uploadSecret) {
    return next(new AppError(403, 'Forbidden', 'UPLOAD_SECRET_MISMATCH'));
  }
  next();
}
