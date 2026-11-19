import React, { useRef, useState } from "react";
import api from "../../api/axios";

/*
  Fill in compliance details for many products from one Excel file: download the sheet (it lists
  the products not on sale yet, with what each still needs), fill it in, upload it. Optionally
  publish every product that then passes all checks.
*/

const box = { background: "#fff", border: "1px solid #C4EFE6", borderRadius: 8, padding: "0.9rem 1rem", marginBottom: "1rem" };
const btn = { padding: "7px 14px", border: "1px solid #00788C", borderRadius: 6, fontSize: "0.85rem", fontWeight: 600, cursor: "pointer" };

export default function ComplianceBulkExcel({ onDone }) {
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);
  const [publish, setPublish] = useState(true);
  const fileRef = useRef();

  const download = async () => {
    setBusy("download"); setError("");
    try {
      const res = await api.get("/api/compliance/bulk/template.xlsx", { params: { scope: "notLive" }, responseType: "blob" });
      const url = URL.createObjectURL(res.data);
      const a = document.createElement("a");
      a.href = url;
      a.download = "compliance-details.xlsx";
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError("Could not download the compliance sheet.");
    } finally {
      setBusy("");
    }
  };

  const upload = async () => {
    const file = fileRef.current?.files?.[0];
    if (!file) { setError("Choose the filled-in Excel file first."); return; }
    setBusy("upload"); setError(""); setResult(null);
    try {
      const fd = new FormData();
      fd.append("file", file);
      const res = await api.post("/api/compliance/bulk/import", fd, { params: { publish } });
      setResult(res.data);
      if (fileRef.current) fileRef.current.value = "";
      onDone?.();
    } catch (e) {
      setError(e?.response?.data?.error || "Could not upload the compliance sheet.");
    } finally {
      setBusy("");
    }
  };

  const rows = result?.rows || [];
  return (
    <div style={box}>
      <div style={{ fontWeight: 700, color: "#003A44", marginBottom: 4 }}>Fill in many products at once</div>
      <div style={{ fontSize: "0.8rem", color: "#475569", marginBottom: 10 }}>
        1. Download the sheet: one row per product not yet on sale, showing what each still needs.
        2. Fill in MRP, quantity, manufacturer and the rest. Empty cells are left as they are.
        3. Upload it here.
      </div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        <button onClick={download} disabled={!!busy} style={{ ...btn, background: "#fff", color: "#00788C" }}>
          {busy === "download" ? "Preparing..." : "Download compliance Excel"}
        </button>
        <input ref={fileRef} type="file" accept=".xlsx" aria-label="Filled-in compliance Excel" style={{ fontSize: "0.8rem" }} />
        <label style={{ fontSize: "0.8rem", display: "flex", alignItems: "center", gap: 4 }}>
          <input type="checkbox" checked={publish} onChange={(e) => setPublish(e.target.checked)} />
          Publish products that pass
        </label>
        <button onClick={upload} disabled={!!busy} style={{ ...btn, background: "#00788C", color: "#fff" }}>
          {busy === "upload" ? "Uploading..." : "Upload"}
        </button>
      </div>
      {error && <div style={{ marginTop: 10, color: "#b91c1c", fontSize: "0.85rem" }}>{error}</div>}
      {result && (
        <div style={{ marginTop: 12, fontSize: "0.83rem" }}>
          <div style={{ fontWeight: 600, marginBottom: 6 }}>
            Saved {result.saved} · published {result.published} · failed {result.failed}
          </div>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "#F5FBFC", textAlign: "left" }}>
                {["Row", "Product", "Score", "Result"].map((h) => <th key={h} style={{ padding: "6px 8px" }}>{h}</th>)}
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.row} style={{ borderTop: "1px solid #E6F8F3" }}>
                  <td style={{ padding: "6px 8px" }}>{r.row}</td>
                  <td style={{ padding: "6px 8px" }}>{r.name || (r.productId ? `#${r.productId}` : "")}</td>
                  <td style={{ padding: "6px 8px" }}>{r.score ?? ""}</td>
                  <td style={{ padding: "6px 8px", color: r.error ? "#b91c1c" : r.live ? "#00788C" : "#475569" }}>
                    {r.error
                      ? r.error
                      : r.live
                        ? "On sale"
                        : r.missing?.length
                          ? `Saved. Still needs: ${r.missing.join(", ")}`
                          : "Saved. Passes all checks."}
                    {r.note ? ` ${r.note}` : ""}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
