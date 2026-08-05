import { config } from '../config.js';
import { AppError } from '../utils/errors.js';

const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
const JPEG_SIGNATURE = [0xff, 0xd8, 0xff];

function hasSignature(buf, sig) {
  if (buf.length < sig.length) return false;
  return sig.every((byte, i) => buf[i] === byte);
}

// Sniff magic bytes so a renamed non-image file (e.g. a script called foo.png)
// is rejected before it reaches the image pipeline.
export function validateImage(req, _res, next) {
  const file = req.file;
  if (!file) {
    return next(new AppError(400, 'No image file uploaded.', 'MISSING_FILE'));
  }
  if (file.size > config.maxFileSizeBytes) {
    return next(new AppError(413, `Image exceeds the ${config.maxFileSizeBytes / (1024 * 1024)}MB limit.`, 'FILE_TOO_LARGE'));
  }
  if (!hasSignature(file.buffer, PNG_SIGNATURE) && !hasSignature(file.buffer, JPEG_SIGNATURE)) {
    return next(new AppError(400, 'Uploaded file is not a valid PNG or JPEG image.', 'INVALID_IMAGE'));
  }
  next();
}
