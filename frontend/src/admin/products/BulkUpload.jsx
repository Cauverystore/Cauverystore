import React, { useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../api/axios";

const BulkUpload = () => {
  const navigate = useNavigate();
  const isSeller = window.location.pathname.startsWith("/seller");
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState(null);
  const fileRef = useRef();

  const templateUrl = isSeller
    ? "/api/seller/template.xlsx"
    : "/api/admin/template.xlsx";

  const redirectPath = isSeller ? "/seller/products" : "/admin/products";

  const handleUpload = async () => {
    if (!file) return;
    setUploading(true);
    setResult(null);
    try {
      const fd = new FormData();
      fd.append("file", file);
      const endpoint = isSeller
        ? "/api/seller/products/bulk-upload"
        : "/api/admin/products/bulk-upload";
      const res = await api.post(endpoint, fd);
      const data = res.data;
      const total = data.total || 0;
      const errors = data.errors || 0;
      const imgErrors = data.imageErrors || [];
      let msg = `Uploaded ${total} product${total !== 1 ? "s" : ""} successfully.`;
      if (imgErrors.length > 0) {
        msg += ` (${imgErrors.length} image warning${imgErrors.length !== 1 ? "s" : ""})`;
      }
      setResult({
        type: "success",
        message: msg,
        total,
        errors,
        imageErrors: imgErrors,
      });
    } catch (err) {
      const msg =
        err.response?.data?.error ||
        err.response?.data?.message ||
        "Upload failed. Check file format.";
      setResult({ type: "error", message: msg });
    }
    setUploading(false);
  };

  return (
    <div style={{ maxWidth: "720px", width: "100%", margin: "0 auto", padding: "2rem", boxSizing: "border-box" }}>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 700, color: "#0B3D2E", marginBottom: "0.25rem" }}>
        Bulk Upload Products
      </h1>
      <p style={{ color: "#64748B", marginBottom: "1.5rem", fontSize: "0.9rem" }}>
        Download the template, fill in your products, and upload the file.
      </p>

      <div style={{
        background: "#fff", padding: "2rem", borderRadius: "8px",
        border: "1px solid #CFE8D6", marginBottom: "1.5rem"
      }}>
        <div style={{ marginBottom: "1.5rem" }}>
          <h3 style={{ fontSize: "1rem", fontWeight: 600, marginBottom: "0.5rem", color: "#1F2937" }}>
            Step 1: Download Template
          </h3>
          <a href={templateUrl} download
            style={{
              display: "inline-flex", alignItems: "center", gap: "8px",
              padding: "10px 20px", background: "#EAF7EE", color: "#146C43",
              borderRadius: "6px", textDecoration: "none", fontWeight: 600,
              fontSize: "0.9rem", border: "1px solid #CFE8D6"
            }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3" />
            </svg>
            Download Product Upload Template (.xlsx)
          </a>
          <p style={{ fontSize: "0.8rem", color: "#64748B", marginTop: "8px" }}>
            Includes sample data, dropdowns, and instructions sheet.
          </p>
        </div>

        <div style={{ marginBottom: "1.5rem" }}>
          <h3 style={{ fontSize: "1rem", fontWeight: 600, marginBottom: "0.5rem", color: "#1F2937" }}>
            Step 2: Fill & Upload
          </h3>
          <div
            style={{
              border: "2px dashed #CFE8D6", borderRadius: "8px", padding: "2rem",
              textAlign: "center", background: "#fafdfb", cursor: "pointer",
              marginBottom: "1rem"
            }}
            onClick={() => fileRef.current?.click()}
          >
            <input
              type="file" ref={fileRef} accept=".xlsx,.xls"
              onChange={(e) => setFile(e.target.files[0])}
              style={{ display: "none" }}
            />
            {file ? (
              <div>
                <p style={{ fontWeight: 600, color: "#1F2937", marginTop: "8px" }}>{file.name}</p>
                <p style={{ fontSize: "0.8rem", color: "#64748B" }}>
                  {(file.size / 1024).toFixed(1)} KB
                </p>
                <button
                  onClick={(e) => { e.stopPropagation(); setFile(null); fileRef.current.value = ""; }}
                  style={{
                    marginTop: "8px", padding: "4px 12px", border: "1px solid #CFE8D6",
                    borderRadius: "4px", background: "#fff", cursor: "pointer", fontSize: "0.8rem"
                  }}
                >
                  Remove
                </button>
              </div>
            ) : (
              <div>
                <p style={{ color: "#64748B", marginTop: "8px", fontSize: "0.9rem" }}>
                  Click to select .xlsx file or drag here
                </p>
              </div>
            )}
          </div>

          {result && (
            <div style={{
              padding: "12px 16px", borderRadius: "6px", marginBottom: "1rem",
              background: result.type === "success" ? "#EAF7EE" : "#fef2f2",
              color: result.type === "success" ? "#146C43" : "#dc2626",
              border: `1px solid ${result.type === "success" ? "#CFE8D6" : "#fecaca"}`,
              fontSize: "0.9rem"
            }}>
              {result.type === "success" ? "\u2713 " : "\u2717 "}{result.message}
              {result.imageErrors && result.imageErrors.length > 0 && (
                <div style={{ marginTop: "8px", fontSize: "0.8rem", color: "#b45309" }}>
                  {result.imageErrors.map((e, i) => <div key={i}>{e}</div>)}
                </div>
              )}
            </div>
          )}

          <button
            onClick={handleUpload}
            disabled={!file || uploading}
            style={{
              padding: "10px 28px", width: "100%",
              background: file && !uploading ? "#2E9B57" : "#CFE8D6",
              color: file && !uploading ? "#fff" : "#94a3b8",
              border: "none", borderRadius: "6px",
              cursor: file && !uploading ? "pointer" : "not-allowed",
              fontWeight: 600, fontSize: "0.9rem"
            }}
          >
            {uploading ? "Uploading..." : "Upload Products"}
          </button>
        </div>

        <div>
          <h3 style={{ fontSize: "1rem", fontWeight: 600, marginBottom: "0.5rem", color: "#1F2937" }}>
            Step 3: Review
          </h3>
          <button
            onClick={() => navigate(redirectPath)}
            style={{
              padding: "8px 20px", background: "none", color: "#2E9B57",
              border: "1px solid #2E9B57", borderRadius: "6px",
              cursor: "pointer", fontWeight: 500, fontSize: "0.9rem"
            }}
          >
            Go to Product List
          </button>
        </div>
      </div>
    </div>
  );
};

export default BulkUpload;
