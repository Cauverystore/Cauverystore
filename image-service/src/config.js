import 'dotenv/config';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

export const config = {
  port: Number(process.env.PORT) || 9092,
  maxFileSizeBytes: (Number(process.env.MAX_FILE_SIZE_MB) || 5) * 1024 * 1024,
  allowedExtensions: ['.png', '.jpg', '.jpeg'],
  uploadDir: path.resolve(process.env.UPLOAD_DIR || path.join(here, '..', 'uploads')),
  uploadSecret: process.env.UPLOAD_SECRET || '',
  maxConcurrentUploads: Number(process.env.MAX_CONCURRENT_UPLOADS) || 2,
  storageDriver: process.env.STORAGE_DRIVER || 'local',
  s3: {
    region: process.env.S3_REGION || '',
    bucket: process.env.S3_BUCKET || '',
    accessKeyId: process.env.S3_ACCESS_KEY_ID || '',
    secretAccessKey: process.env.S3_SECRET_ACCESS_KEY || '',
  },
  publicBaseUrl: (process.env.PUBLIC_BASE_URL || '').replace(/\/+$/, ''),
  databaseUrl: process.env.DATABASE_URL || '',
};
