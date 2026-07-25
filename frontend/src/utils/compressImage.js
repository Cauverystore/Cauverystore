export const compressImage = (file, maxDim = 1200, quality = 0.85) => {
  return new Promise((resolve, reject) => {
    if (!file.type.startsWith("image/")) return reject(new Error("Not an image"));
    const img = new Image();
    img.onload = () => {
      let w = img.width, h = img.height;
      if (w <= maxDim && h <= maxDim && file.size < 500 * 1024) {
        return resolve(file);
      }
      const scale = Math.min(maxDim / w, maxDim / h, 1);
      w = Math.round(w * scale);
      h = Math.round(h * scale);
      const canvas = document.createElement("canvas");
      canvas.width = w;
      canvas.height = h;
      const ctx = canvas.getContext("2d");
      ctx.imageSmoothingQuality = "high";
      ctx.drawImage(img, 0, 0, w, h);
      canvas.toBlob(
        (blob) => {
          if (!blob) return reject(new Error("Compression failed"));
          const compressed = new File([blob], file.name.replace(/\.[^.]+$/, ".jpg"), {
            type: "image/jpeg",
            lastModified: Date.now(),
          });
          resolve(compressed);
        },
        "image/jpeg",
        quality
      );
    };
    img.onerror = () => reject(new Error("Failed to load image"));
    const url = URL.createObjectURL(file);
    img.src = url;
  });
};

export const compressImages = async (files, maxDim = 1200, quality = 0.85) => {
  const results = [];
  for (const f of files) {
    try {
      results.push(await compressImage(f, maxDim, quality));
    } catch {
      results.push(f);
    }
  }
  return results;
};
