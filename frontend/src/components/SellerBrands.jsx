import React, { useCallback, useEffect, useState } from "react";
import api from "../api/axios";
import { confirmAction } from "./ConfirmDialog";

// Brands on the seller's Store Profile: apply to sell a brand, with proof, and follow the review.
const STATUS = {
  SUBMITTED: { text: "Submitted", color: "#b45309", bg: "#fef3c7" },
  UNDER_REVIEW: { text: "Under review", color: "#1d4ed8", bg: "#dbeafe" },
  APPROVED: { text: "Approved", color: "#15803d", bg: "#dcfce7" },
  REJECTED: { text: "Not approved", color: "#b91c1c", bg: "#fee2e2" },
};
const badge = (s) => {
  const st = STATUS[s] || { text: s, color: "#374151", bg: "#f3f4f6" };
  return <span style={{ fontSize: "0.75rem", fontWeight: 600, color: st.color, background: st.bg, padding: "2px 8px", borderRadius: 999 }}>{st.text}</span>;
};
const empty = { brandName: "", relationship: "BRAND_OWNER", trademarkNumber: "", notes: "" };

const SellerBrands = ({ inputStyle, labelStyle, sectionStyle, btnStyle }) => {
  const [data, setData] = useState({ applications: [], protectedBrands: [], relationships: [] });
  const [form, setForm] = useState(empty);
  const [file, setFile] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(() => {
    api.get("/api/seller/brands")
      .then((r) => setData({ applications: r.data.applications || [], protectedBrands: r.data.protectedBrands || [], relationships: r.data.relationships || [] }))
      .catch(() => setError("Your brands could not be loaded."));
  }, []);
  useEffect(() => { load(); }, [load]);

  const submit = async (e) => {
    e.preventDefault();
    setSaving(true); setError("");
    try {
      let documentId = null;
      if (file) {
        if (file.size > 5 * 1024 * 1024) throw new Error("The file is too large. The limit is 5 MB.");
        const fd = new FormData();
        fd.append("file", file);
        fd.append("documentType", form.relationship === "BRAND_OWNER" ? "TRADEMARK" : "BRAND_AUTHORIZATION");
        fd.append("documentName", `${form.brandName} - ${form.relationship === "BRAND_OWNER" ? "trademark" : "authorisation"}`);
        const up = await api.post("/api/seller-registration/document/upload", fd);
        documentId = up.data?.document?.id || null;
      }
      await api.post("/api/seller/brands", { ...form, documentId });
      setForm(empty); setFile(null); setShowForm(false); load();
    } catch (err) {
      setError(err.response?.data?.error || err.message || "The application could not be sent.");
    }
    setSaving(false);
  };

  const withdraw = async (a) => {
    if (!(await confirmAction(`Withdraw your application for ${a.brandName}?`, { confirmLabel: "Withdraw", danger: true }))) return;
    try { await api.delete(`/api/seller/brands/${a.id}`); load(); }
    catch (err) { setError(err.response?.data?.error || "Could not withdraw it."); }
  };

  const cell = { padding: "0.5rem 0.25rem", fontSize: "0.875rem", borderTop: "1px solid #f1f5f9", verticalAlign: "top" };

  return (
    <div style={sectionStyle}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.25rem" }}>
        <h2 style={{ fontSize: "1.125rem", fontWeight: 700, margin: 0 }}>Brands</h2>
        {!showForm && <button type="button" onClick={() => { setShowForm(true); setError(""); }} style={{ ...btnStyle, padding: "0.35rem 0.9rem" }}>+ Apply for a brand</button>}
      </div>
      <p style={{ fontSize: "0.85rem", color: "#6b7280", margin: "0 0 1rem" }}>
        Apply for brands you own or are authorised to sell.
        {data.protectedBrands.length > 0 && <> These brands need an approved application before you can list them: <strong>{data.protectedBrands.join(", ")}</strong>.</>}
      </p>
      {error && <div style={{ background: "#fee2e2", color: "#dc2626", padding: "0.6rem 0.8rem", borderRadius: 4, marginBottom: "1rem", fontSize: "0.85rem" }}>{error}</div>}

      {showForm && (
        <form onSubmit={submit} style={{ background: "#f9fafb", padding: "1rem", borderRadius: 6, marginBottom: "1rem" }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "0.75rem" }}>
            <div>
              <label style={labelStyle} htmlFor="br-name">Brand name</label>
              <input id="br-name" required maxLength={120} style={inputStyle} value={form.brandName} onChange={(e) => setForm({ ...form, brandName: e.target.value })} />
            </div>
            <div>
              <label style={labelStyle} htmlFor="br-rel">Your connection to it</label>
              <select id="br-rel" style={inputStyle} value={form.relationship} onChange={(e) => setForm({ ...form, relationship: e.target.value })}>
                {data.relationships.map((r) => <option key={r.code} value={r.code}>{r.label}</option>)}
              </select>
            </div>
            <div>
              <label style={labelStyle} htmlFor="br-tm">Trademark number (if any)</label>
              <input id="br-tm" maxLength={40} style={inputStyle} value={form.trademarkNumber} onChange={(e) => setForm({ ...form, trademarkNumber: e.target.value })} />
            </div>
            <div>
              <label style={labelStyle} htmlFor="br-file">{form.relationship === "BRAND_OWNER" ? "Trademark certificate" : "Authorisation letter"} (PDF, JPG or PNG)</label>
              <input id="br-file" type="file" accept=".pdf,.jpg,.jpeg,.png" onChange={(e) => setFile(e.target.files[0] || null)} style={{ fontSize: "0.85rem" }} />
            </div>
          </div>
          <div style={{ marginTop: "0.75rem" }}>
            <label style={labelStyle} htmlFor="br-notes">Anything the team should know (optional)</label>
            <textarea id="br-notes" rows={2} style={inputStyle} value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
          </div>
          <p style={{ fontSize: "0.8rem", color: "#6b7280", margin: "0.5rem 0 0.8rem" }}>Give a trademark number, a document, or both. Documents are kept private to you and the review team.</p>
          <button type="submit" disabled={saving} style={{ ...btnStyle, opacity: saving ? 0.7 : 1 }}>{saving ? "Sending..." : "Send application"}</button>
          <button type="button" onClick={() => setShowForm(false)} style={{ marginLeft: 8, padding: "0.5rem 1rem", background: "#fff", border: "1px solid #d1d5db", borderRadius: 4, cursor: "pointer", fontSize: "0.875rem" }}>Cancel</button>
        </form>
      )}

      {data.applications.length === 0 && !showForm ? (
        <p style={{ fontSize: "0.875rem", color: "#6b7280", margin: 0 }}>No brand applications yet.</p>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <tbody>
            {data.applications.map((a) => (
              <tr key={a.id}>
                <td style={cell}>
                  <div style={{ fontWeight: 600 }}>{a.brandName}</div>
                  <div style={{ fontSize: "0.8rem", color: "#6b7280" }}>{a.relationshipLabel}{a.trademarkNumber ? ` · TM ${a.trademarkNumber}` : ""}</div>
                  {a.status === "REJECTED" && a.rejectionReason && <div style={{ fontSize: "0.8rem", color: "#b91c1c" }}>{a.rejectionReason}</div>}
                </td>
                <td style={{ ...cell, textAlign: "right", whiteSpace: "nowrap" }}>
                  {badge(a.status)}
                  {(a.status === "SUBMITTED" || a.status === "UNDER_REVIEW") && (
                    <div style={{ marginTop: 6 }}><button type="button" onClick={() => withdraw(a)} style={{ background: "none", border: "none", color: "#dc2626", cursor: "pointer", fontSize: "0.8rem" }}>Withdraw</button></div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default SellerBrands;
