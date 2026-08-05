import crypto from 'node:crypto';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { config } from '../config.js';
import { AppError } from '../utils/errors.js';

// Names are server-generated UUIDs; the client's original name is never used
// for a filesystem path, which makes path traversal impossible.
export function buildObjectName(kind) {
  return `${crypto.randomUUID()}_${kind}.webp`;
}

async function saveLocal(name, data) {
  const root = path.resolve(config.uploadDir);
  const target = path.resolve(root, name);
  // Defensive containment check (belt and braces on top of UUID naming).
  if (!target.startsWith(root + path.sep)) {
    throw new AppError(400, 'Invalid file path.', 'INVALID_PATH');
  }
  await fs.mkdir(root, { recursive: true });
  await fs.writeFile(target, data);
  return `${config.publicBaseUrl}/uploads/${name}`;
}

async function saveS3(name, data) {
  const { S3Client, PutObjectCommand } = await import('@aws-sdk/client-s3');
  const client = new S3Client({
    region: config.s3.region,
    credentials: {
      accessKeyId: config.s3.accessKeyId,
      secretAccessKey: config.s3.secretAccessKey,
    },
  });
  await client.send(
    new PutObjectCommand({
      Bucket: config.s3.bucket,
      Key: `uploads/${name}`,
      Body: data,
      ContentType: 'image/webp',
    })
  );
  return `${config.publicBaseUrl}/uploads/${name}`;
}

export async function saveFile(name, data) {
  return config.storageDriver === 's3' ? saveS3(name, data) : saveLocal(name, data);
}

export async function deleteFile(name) {
  if (config.storageDriver === 's3') {
    const { S3Client, DeleteObjectCommand } = await import('@aws-sdk/client-s3');
    const client = new S3Client({
      region: config.s3.region,
      credentials: {
        accessKeyId: config.s3.accessKeyId,
        secretAccessKey: config.s3.secretAccessKey,
      },
    });
    await client.send(new DeleteObjectCommand({ Bucket: config.s3.bucket, Key: `uploads/${name}` }));
    return;
  }
  const root = path.resolve(config.uploadDir);
  const target = path.resolve(root, name);
  if (target.startsWith(root + path.sep)) {
    await fs.unlink(target).catch(() => {});
  }
}
