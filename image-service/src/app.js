import express from 'express';
import { config } from './config.js';
import uploadRouter from './routes/upload.js';
import { uploadSecret } from './middleware/uploadSecret.js';
import { limitConcurrentUploads } from './middleware/concurrencyLimit.js';
import { errorHandler } from './middleware/errorHandler.js';
import { AppError } from './utils/errors.js';

export const app = express();

app.use(express.json());

app.get('/health', (_req, res) => res.json({ status: 'ok', service: 'image-service' }));

app.use('/uploads', express.static(config.uploadDir));

app.use('/', uploadSecret, limitConcurrentUploads, uploadRouter);

app.use((_req, _res, next) => next(new AppError(404, 'Route not found.', 'NOT_FOUND')));

app.use(errorHandler);
