import React, { useState, useEffect, useCallback } from "react";
import api from "../api/axios";
import { confirmAction } from "./ConfirmDialog";

// GST numbers and licences on the seller's Store Profile. Licences are shown to customers on
// the seller's products once the Cauvery Store team has checked them.
const STATUS = {
  VERIFIED: { text: "Checked", color: "#15803d", bg: "#dcfce7" },
  PENDING: { text: "Waiting for check", color: "#b45309", bg: "#fef3c7" },
  REJECTED: { text: "Not accepted", color: "#b91c1c", bg: "#fee2e2" },
  UNVERIFIED: { text: "Not verified", color: "#b45309", bg: "#fef3c7" },
  FAILED: { text: "Verification failed", color: "#b91c1c", bg: "#fee2e2" },
};
const badge = (s) => {
  const st = STATUS[s] || { text: s || "—", color: "#374151", bg: "#f3f4f6" };
  return <span style={{ fontSize: "0.75rem", fontWeight: 600, color: st.color, background: st.bg, padding: "2px 8px", borderRadius: 999 }}>{st.text}</span>;
};
const dateText = (d) => (d ? new Date(d).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "");
const empty = { licenceType: "FSSAI", licenceName: "", licenceNumber: "", validUntil: "" };

const SellerLicences = ({ inputStyle, labelStyle, sectionStyle, btnStyle }) => {
  const [data, setData] = useState({ gstNumbers: [], licences: [], types: [] });
  const [form, setForm] = useState(empty);
  const [editingId, setEditingId] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(() => {
    api.get("/api/seller/licences")
      .then((r) => setData({ gstNumbers: r.data.gstNumbers || [], licences: r.data.licences || [], types: r.data.types || [] }))
      .catch(() => setError("Your GST numbers and licences could not be loaded."));
  }, []);
  useEffect(() => { load(); }, [load]);

  const type = data.types.find((t) => t.code === form.licenceType);

  const startAdd = () => { setForm(empty); setEditingId(null); setShowForm(true); setError(""); };
  const startEdit = (l) => {
    setForm({ licenceType: l.licenceType, licenceName: l.licenceName || "", licenceNumber: l.licenceNumber, validUntil: l.validUntil || "" });
    setEditingId(l.id); setShowForm(true); setError("");
  };

  const save = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      if (editingId) await api.put(`/api/seller/licences/${editingId}`, form);
      else await api.post("/api/seller/licences", form);
      setShowForm(false); setEditingId(null); setError(""); load();
    } catch (err) {
      setError(err.response?.data?.error || "The licence could not be saved.");
    }
    setSaving(false);
  };

  const remove = async (l) => {
    if (!(await confirmAction(`Remove your ${l.label} (${l.licenceNumber})?\n\nIt will stop showing on your products.`, { confirmLabel: "Remove", danger: true }))) return;
    try { await api.delete(`/api/seller/licences/${l.id}`); load(); }
    catch (err) { setError(err.response?.data?.error || "The licence could not be removed."); }
  };

  const cell = { padding: "0.5rem 0.25rem", fontSize: "0.875rem", borderTop: "1px solid #f1f5f9", verticalAlign: "top" };

  return (
    <div style={sectionStyle}>
      <h2 style={{ fontSize: "1.125rem", fontWeight: 700, marginBottom: "0.25rem" }}>GST & Licences</h2>
      <p style={{ fontSize: "0.85rem", color: "#6b7280", margin: "0 0 1rem" }}>
        Customers see your GSTIN and checked licence numbers under "Sold by" on your products.
      </p>
      {error && <div style={{ background: "#fee2e2", color: "#dc2626", padding: "0.6rem 0.8rem", borderRadius: 4, marginBottom: "1rem", fontSize: "0.85rem" }}>{error}</div>}

      <h3 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "0 0 0.5rem" }}>GST numbers</h3>
      {data.gstNumbers.length === 0 ? (
        <p style={{ fontSize: "0.875rem", color: "#6b7280", margin: "0 0 1rem" }}>No GSTIN on file. Add it on your Dashboard under GST information.</p>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse", marginBottom: "1.25rem" }}>
          <tbody>
            {data.gstNumbers.map((g) => (
              <tr key={g.gstin}>
                <td style={{ ...cell, fontFamily: "monospace" }}>{g.gstin}</td>
                <td style={cell}>{g.primary ? "Main" : "Additional"}{g.stateCode ? ` · State ${g.stateCode}` : ""}</td>
                <td style={{ ...cell, textAlign: "right" }}>{badge(g.status)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", margin: "0 0 0.5rem" }}>
        <h3 style={{ fontSize: "0.95rem", fontWeight: 600, margin: 0 }}>Licences</h3>
        {!showForm && <button type="button" onClick={startAdd} style={{ ...btnStyle, padding: "0.35rem 0.9rem" }}>+ Add licence</button>}
      </div>

      {data.licences.length === 0 && !showForm && (
        <p style={{ fontSize: "0.875rem", color: "#6b7280" }}>
          No licences added. Selling food, drinks or spices needs an FSSAI licence; medicines need a drug licence.
        </p>
      )}

      {data.licences.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", marginBottom: "1rem" }}>
          <tbody>
            {data.licences.map((l) => (
              <tr key={l.id}>
                <td style={cell}>
                  <div style={{ fontWeight: 600 }}>{l.label}</div>
                  <div style={{ fontFamily: "monospace" }}>{l.licenceNumber}</div>
                  {l.validUntil && <div style={{ fontSize: "0.78rem", color: "#6b7280" }}>Valid until {dateText(l.validUntil)}</div>}
                  {l.status === "REJECTED" && l.rejectionReason && <div style={{ fontSize: "0.78rem", color: "#b91c1c" }}>{l.rejectionReason}</div>}
                </td>
                <td style={{ ...cell, textAlign: "right", whiteSpace: "nowrap" }}>
                  {badge(l.status)}
                  <div style={{ marginTop: 6 }}>
                    <button type="button" onClick={() => startEdit(l)} style={{ background: "none", border: "none", color: "#2563eb", cursor: "pointer", fontSize: "0.8rem" }}>Edit</button>
                    <button type="button" onClick={() => remove(l)} style={{ background: "none", border: "none", color: "#dc2626", cursor: "pointer", fontSize: "0.8rem" }}>Remove</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {showForm && (
        <form onSubmit={save} style={{ background: "#f9fafb", padding: "1rem", borderRadius: 6 }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "0.75rem" }}>
            <div>
              <label style={labelStyle} htmlFor="lic-type">Licence</label>
              <select id="lic-type" style={inputStyle} value={form.licenceType} onChange={(e) => setForm({ ...form, licenceType: e.target.value })}>
                {data.types.map((t) => <option key={t.code} value={t.code}>{t.label}</option>)}
              </select>
            </div>
            {form.licenceType === "OTHER" && (
              <div>
                <label style={labelStyle} htmlFor="lic-name">Name of licence</label>
                <input id="lic-name" style={inputStyle} maxLength={100} value={form.licenceName} onChange={(e) => setForm({ ...form, licenceName: e.target.value })} required />
              </div>
            )}
            <div>
              <label style={labelStyle} htmlFor="lic-number">Licence number{type?.formatHint ? ` (${type.formatHint})` : ""}</label>
              <input id="lic-number" style={inputStyle} maxLength={60} value={form.licenceNumber} onChange={(e) => setForm({ ...form, licenceNumber: e.target.value })} required />
            </div>
            <div>
              <label style={labelStyle} htmlFor="lic-until">Valid until (optional)</label>
              <input id="lic-until" type="date" style={inputStyle} value={form.validUntil} onChange={(e) => setForm({ ...form, validUntil: e.target.value })} />
            </div>
          </div>
          {type?.whenNeeded && <p style={{ fontSize: "0.8rem", color: "#6b7280", margin: "0.6rem 0 0" }}>{type.whenNeeded}</p>}
          <p style={{ fontSize: "0.8rem", color: "#6b7280", margin: "0.4rem 0 0.8rem" }}>
            Also upload a copy of the licence in your seller registration documents, so the team can check it.
          </p>
          <button type="submit" disabled={saving} style={{ ...btnStyle, opacity: saving ? 0.7 : 1 }}>{saving ? "Saving..." : editingId ? "Save changes" : "Add licence"}</button>
          <button type="button" onClick={() => { setShowForm(false); setEditingId(null); }} style={{ marginLeft: 8, padding: "0.5rem 1rem", background: "#fff", border: "1px solid #d1d5db", borderRadius: 4, cursor: "pointer", fontSize: "0.875rem" }}>Cancel</button>
        </form>
      )}
    </div>
  );
};

export default SellerLicences;
