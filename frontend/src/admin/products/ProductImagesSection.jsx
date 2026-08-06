import React, { useState, useEffect, useRef } from "react";
import api from "../../api/axios";
import { imgUrl } from "../../utils/images";
import { compressImages } from "../../utils/compressImage";

const ProductImagesSection = ({ productId }) => {
  const [images, setImages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [msg, setMsg] = useState(null);
  const [dragIdx, setDragIdx] = useState(null);
  const fileRef = useRef();

  const loadImages = async () => {
    try {
      const res = await api.get(`/api/admin/products/${productId}/images`);
      const sorted = (res.data || []).sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
      setImages(sorted);
    } catch {
      setMsg({ type: "error", text: "Failed to load images" });
    }
    setLoading(false);
  };

  useEffect(() => { loadImages(); }, [productId]);

  const handleUpload = async () => {
    if (selectedFiles.length === 0) return;
    setUploading(true);
    setMsg({ type: "info", text: "Compressing images..." });
    const compressed = await compressImages(selectedFiles);
    let failed = 0;
    for (const file of compressed) {
      const fd = new FormData();
      fd.append("image", file);
      fd.append("product_id", String(productId));
      try {
        // Process via the image pipeline (Java passthrough -> Node multer/sharp).
        const up = await api.post("/api/uploads/upload-image", fd);
        // Link the processed WebP sizes to this product.
        await api.post(`/api/admin/products/${productId}/images/url`, {
          thumbnail_url: up.data.thumbnail_url,
          preview_url: up.data.preview_url,
          zoom_url: up.data.zoom_url,
          original_url: up.data.original_url,
        });
      } catch (err) {
        failed += 1;
        setMsg({ type: "error", text: `Failed to upload ${file.name}` });
      }
    }
    setSelectedFiles([]);
    if (fileRef.current) fileRef.current.value = "";
    await loadImages();
    setUploading(false);
    setMsg({
      type: "success",
      text: failed === 0 ? "Images uploaded" : `${compressed.length - failed} of ${compressed.length} images uploaded`,
    });
  };

  const handleDelete = async (imageId) => {
    if (!window.confirm("Delete this image?")) return;
    try {
      await api.delete(`/api/admin/products/${productId}/images/${imageId}`);
      await loadImages();
      setMsg({ type: "success", text: "Image deleted" });
    } catch {
      setMsg({ type: "error", text: "Failed to delete image" });
    }
  };

  const handleSetMain = async (imageId) => {
    try {
      await api.put(`/api/admin/products/${productId}/images/${imageId}/main`);
      await loadImages();
      setMsg({ type: "success", text: "Main image updated" });
    } catch {
      setMsg({ type: "error", text: "Failed to set main image" });
    }
  };

  const handleDragStart = (idx) => { setDragIdx(idx); };
  const handleDragOver = (e, idx) => {
    e.preventDefault();
    if (dragIdx === null || dragIdx === idx) return;
    const updated = [...images];
    const [moved] = updated.splice(dragIdx, 1);
    updated.splice(idx, 0, moved);
    setImages(updated);
    setDragIdx(idx);
  };
  const handleDragEnd = async () => {
    setDragIdx(null);
    const order = images.map((img) => img.id);
    try {
      await api.put(`/api/admin/products/${productId}/images/reorder`, { order });
    } catch {
      await loadImages();
    }
  };

  const removeSelected = (idx) => {
    setSelectedFiles((prev) => prev.filter((_, i) => i !== idx));
  };

  if (loading) {
    return <div style={{ textAlign: "center", padding: "2rem", color: "#64748B" }}>Loading...</div>;
  }

  return (
    <div>
      {msg && (
        <div style={{
          padding: "10px 14px", borderRadius: "6px", marginBottom: "1rem",
          background: msg.type === "success" ? "#d5f2f0" : msg.type === "info" ? "#dbeafe" : "#fbe2df",
          color: msg.type === "success" ? "#0E5C5C" : msg.type === "info" ? "#2563eb" : "#D93A2A",
          border: `1px solid ${msg.type === "success" ? "#a8e3e0" : msg.type === "info" ? "#93c5fd" : "#f5b8b1"}`,
          fontSize: "0.85rem"
        }}>
          {msg.type === "success" ? "\u2713 " : msg.type === "info" ? "\u2139 " : "\u2717 "}{msg.text}
        </div>
      )}

      {/* Upload area */}
      <div style={{ background: "#fff", borderRadius: "8px", border: "1px solid #e0d9cc", padding: "1.25rem", marginBottom: "1rem" }}>
        <h3 style={{ fontSize: "0.95rem", fontWeight: 700, color: "#0E5C5C", margin: "0 0 0.75rem" }}>Upload Images</h3>
        <div style={{ display: "flex", gap: "10px", alignItems: "center", flexWrap: "wrap" }}>
          <input type="file" ref={fileRef} multiple accept="image/*" onChange={(e) => setSelectedFiles(Array.from(e.target.files))}
            style={{ fontSize: "0.85rem" }} />
          <button onClick={handleUpload} disabled={selectedFiles.length === 0 || uploading}
            style={{
              padding: "8px 20px",
              background: selectedFiles.length > 0 && !uploading ? "#D93A2A" : "#e0d9cc",
              color: selectedFiles.length > 0 && !uploading ? "#fff" : "#94a3b8",
              border: "none", borderRadius: "6px",
              cursor: selectedFiles.length > 0 && !uploading ? "pointer" : "not-allowed",
              fontWeight: 600, fontSize: "0.85rem"
            }}>
            {uploading ? "Uploading..." : `Upload${selectedFiles.length > 0 ? ` (${selectedFiles.length})` : ""}`}
          </button>
        </div>
        {selectedFiles.length > 0 && (
          <div style={{ display: "flex", gap: "8px", flexWrap: "wrap", marginTop: "10px" }}>
            {selectedFiles.map((f, i) => (
              <div key={i} style={{ position: "relative", width: "72px", height: "72px", borderRadius: "6px", overflow: "hidden", border: "1px solid #e0d9cc" }}>
                <img src={URL.createObjectURL(f)} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} />
                <button onClick={() => removeSelected(i)}
                  style={{ position: "absolute", top: "2px", right: "2px", width: "20px", height: "20px", borderRadius: "50%", border: "none", background: "rgba(217,58,42,0.8)", color: "#fff", cursor: "pointer", fontSize: "12px", lineHeight: "20px", padding: 0 }}>
                  &times;
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Image grid with drag-drop */}
      <div style={{ background: "#fff", borderRadius: "8px", border: "1px solid #e0d9cc", padding: "1.25rem" }}>
        <h3 style={{ fontSize: "0.95rem", fontWeight: 700, color: "#0E5C5C", margin: "0 0 0.75rem" }}>
          Product Images ({images.length})
          {images.length > 1 && <span style={{ fontSize: "0.7rem", fontWeight: 400, color: "#94a3b8", marginLeft: "8px" }}>Drag to reorder</span>}
        </h3>
        {images.length === 0 ? (
          <p style={{ color: "#94a3b8", textAlign: "center", padding: "2rem", fontSize: "0.9rem" }}>No images uploaded yet.</p>
        ) : (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(150px, 1fr))", gap: "0.75rem" }}>
            {images.map((img, idx) => (
              <div key={img.id}
                draggable
                onDragStart={() => handleDragStart(idx)}
                onDragOver={(e) => handleDragOver(e, idx)}
                onDragEnd={handleDragEnd}
                style={{
                  border: `2px solid ${img.main ? "#0E5C5C" : "#e0d9cc"}`,
                  borderRadius: "8px", overflow: "hidden", position: "relative", background: "#F5F1EA",
                  cursor: "grab",
                  opacity: dragIdx === idx ? 0.5 : 1,
                  transition: "opacity 0.15s",
                }}>
                <img src={imgUrl(img.url)} alt=""
                  style={{ width: "100%", aspectRatio: "1", objectFit: "cover", display: "block" }}
                  onError={(e) => { e.target.style.display = "none"; }} />
                {img.main && (
                  <span style={{
                    position: "absolute", top: "6px", left: "6px", background: "#0E5C5C", color: "#fff",
                    fontSize: "0.65rem", fontWeight: 700, padding: "2px 8px", borderRadius: "4px"
                  }}>
                    MAIN
                  </span>
                )}
                <div style={{ display: "flex", gap: "4px", padding: "6px" }}>
                  {!img.main && (
                    <button onClick={() => handleSetMain(img.id)}
                      style={{ flex: 1, padding: "4px", fontSize: "0.7rem", border: "1px solid #e0d9cc", borderRadius: "4px", background: "#fff", cursor: "pointer" }}>
                      Set Main
                    </button>
                  )}
                  <button onClick={() => handleDelete(img.id)}
                    style={{
                      flex: img.main ? "1" : "0", padding: "4px 8px", fontSize: "0.7rem",
                      border: "1px solid #f5b8b1", borderRadius: "4px", background: "#fdf1ef",
                      color: "#D93A2A", cursor: "pointer", whiteSpace: "nowrap"
                    }}>
                    Delete
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default ProductImagesSection;
