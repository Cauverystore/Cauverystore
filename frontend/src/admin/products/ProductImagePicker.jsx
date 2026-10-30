import React, { useRef, useState } from "react";
import api from "../../api/axios";
import { compressImages } from "../../utils/compressImage";
import useAltText from "../../hooks/useAltText";

const ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
const MAX_FILE_SIZE = 2 * 1024 * 1024;
export const MAX_IMAGES = 5;

/**
 * Sends photos picked for a new product through the image pipeline
 * (Java passthrough -> Node multer/sharp) and links the processed sizes to the product.
 * The endpoints accept admins, super admins, executives and sellers alike.
 * Returns the names of the files that failed, so the caller can say so.
 */
export async function uploadProductImages(productId, files) {
  const failed = [];
  if (!productId || !files.length) return failed;
  let compressed;
  try {
    compressed = await compressImages(files);
  } catch {
    return files.map((f) => f.name);
  }
  for (const file of compressed) {
    const fd = new FormData();
    fd.append("image", file);
    fd.append("product_id", String(productId));
    try {
      const up = await api.post("/api/uploads/upload-image", fd);
      await api.post(`/api/admin/products/${productId}/images/url`, {
        thumbnail_url: up.data.thumbnail_url,
        preview_url: up.data.preview_url,
        zoom_url: up.data.zoom_url,
        original_url: up.data.original_url,
      });
    } catch {
      failed.push(file.name);
    }
  }
  return failed;
}

/** Photo chooser for the add-product forms: checks type and size, shows previews. */
export default function ProductImagePicker({ images, onChange, sectionStyle, titleStyle }) {
  const altText = useAltText();
  const fileRef = useRef();
  const [errors, setErrors] = useState([]);

  const handleFiles = (files) => {
    const newErrors = [];
    const valid = Array.from(files).filter((f) => {
      if (!ACCEPTED_TYPES.includes(f.type)) { newErrors.push(`${f.name}: Invalid format. Use JPG, PNG, or WebP.`); return false; }
      if (f.size > MAX_FILE_SIZE) { newErrors.push(`${f.name}: Exceeds 2MB limit.`); return false; }
      return true;
    });
    if (valid.length + images.length > MAX_IMAGES) newErrors.push(`Maximum ${MAX_IMAGES} images allowed.`);
    onChange([...images, ...valid].slice(0, MAX_IMAGES));
    setErrors(newErrors);
    // Let the same file be picked again after removing it.
    if (fileRef.current) fileRef.current.value = "";
  };

  const removeImage = (idx) => onChange(images.filter((_, i) => i !== idx));

  return (
    <div style={sectionStyle}>
      <h3 style={titleStyle}>Product Images</h3>
      <div style={{ display: "flex", gap: "10px", alignItems: "center", flexWrap: "wrap", marginBottom: "0.75rem" }}>
        <input
          type="file" ref={fileRef} multiple accept=".jpg,.jpeg,.png,.webp"
          onChange={(e) => handleFiles(e.target.files)}
          style={{ fontSize: "0.85rem" }}
        />
        <span style={{ fontSize: "0.75rem", color: "#64748B" }}>
          Max {MAX_IMAGES} images, JPG/PNG/WebP, 2MB each
        </span>
      </div>
      {errors.length > 0 && (
        <div style={{ marginBottom: "0.5rem" }}>
          {errors.map((err, i) => (
            <div key={i} style={{ fontSize: "0.75rem", color: "#dc2626" }}>{err}</div>
          ))}
        </div>
      )}
      {images.length > 0 ? (
        <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
          {images.map((f, i) => (
            <div key={i} style={{ position: "relative", width: "80px", height: "80px", borderRadius: "6px", overflow: "hidden", border: "1px solid #CFE8D6" }}>
              <img src={URL.createObjectURL(f)} alt={altText.uploadPreview(i + 1)} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
              <button type="button" onClick={() => removeImage(i)} aria-label={`Remove ${f.name}`}
                style={{ position: "absolute", top: "2px", right: "2px", width: "20px", height: "20px", borderRadius: "50%", border: "none", background: "rgba(220,38,38,0.8)", color: "#fff", cursor: "pointer", fontSize: "12px", lineHeight: "20px", padding: 0 }}>
                &times;
              </button>
            </div>
          ))}
          {images.length < MAX_IMAGES && (
            <div onClick={() => fileRef.current?.click()}
              style={{ width: "80px", height: "80px", borderRadius: "6px", border: "2px dashed #CFE8D6", display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer", color: "#94a3b8", fontSize: "1.5rem" }}>
              +
            </div>
          )}
        </div>
      ) : (
        <div onClick={() => fileRef.current?.click()}
          style={{ padding: "1.5rem", textAlign: "center", border: "2px dashed #CFE8D6", borderRadius: "6px", cursor: "pointer", color: "#94a3b8", fontSize: "0.85rem" }}>
          Click to upload product images
        </div>
      )}
    </div>
  );
}
