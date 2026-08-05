import sharp from 'sharp';

// Sizes are max widths; height follows aspect ratio. No upscaling.
export const IMAGE_SIZES = {
  thumbnail: { width: 150, quality: 80 },
  preview: { width: 600, quality: 80 },
  zoom: { width: 1200, quality: 85 },
  original: { width: null, quality: 90 },
};

async function render(buffer, { width, quality }) {
  let pipeline = sharp(buffer).rotate(); // bake EXIF orientation into pixels
  if (width) {
    pipeline = pipeline.resize({ width, fit: 'inside', withoutEnlargement: true });
  }
  // No withMetadata() call -> EXIF/IPTC/XMP metadata is stripped from the output.
  return pipeline.webp({ quality, effort: 4 }).toBuffer();
}

// Returns a map of size name -> WebP buffer: { thumbnail, preview, zoom, original }.
export async function processImage(buffer) {
  const entries = Object.entries(IMAGE_SIZES);
  const results = await Promise.all(
    entries.map(async ([name, opts]) => ({ name, data: await render(buffer, opts) }))
  );
  return Object.fromEntries(results.map(({ name, data }) => [name, data]));
}
