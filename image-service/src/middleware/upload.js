import path from 'node:path';
import multer from 'multer';
import { config } from '../config.js';
import { AppError } from '../utils/errors.js';

// Memory storage keeps untrusted bytes off disk until they pass validation.
export const upload = multer({
  storage: multer.memoryStorage(),
  limits: {
    fileSize: config.maxFileSizeBytes,
    files: 1,
    fields: 5,
  },
  fileFilter: (_req, file, cb) => {
    const ext = path.extname(file.originalname || '').toLowerCase();
    if (!config.allowedExtensions.includes(ext)) {
      return cb(new AppError(400, `Only ${config.allowedExtensions.join(', ')} files are allowed.`, 'UNSUPPORTED_FILE_TYPE'));
    }
    cb(null, true);
  },
});
