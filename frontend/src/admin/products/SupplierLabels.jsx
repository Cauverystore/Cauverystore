import React, { useCallback, useEffect, useState } from "react";
import api from "../../api/axios";

/**
 * Supplier Labels: type in what the supplier's pack label or written reply says. A product goes on
 * sale by itself only when this is complete, including the supplier's permission to use photos.
 */
const EMPTY = {
  sku: "", mrp: "", hsnCode: "", gstRateSelectionId: "", countryOfOrigin: "", stateOfOrigin: "",
  manufacturerName: "", manufacturerAddress: "", packerName: "", packerAddress: "",
  importerName: "", importerAddress: "", netQuantity: "", grossQuantity: "", quantityUnit: "",
  mfgDate: "", batchNumber: "", photosAllowed: false,
};

const FIELDS = [
  ["sku", "Supplier item code *"], ["mrp", "MRP on the pack (₹) *"],
  ["hsnCode", "HSN code *"], ["gstRateSelectionId", "GST rate line (only if asked)"],
  ["countryOfOrigin", "Country of origin *"], ["stateOfOrigin", "State of origin (if India)"],
  ["manufacturerName", "Manufacturer name *"], ["manufacturerAddress", "Manufacturer address *"],
  ["packerName", "Packer name (if different)"], ["packerAddress", "Packer address (if different)"],
  ["importerName", "Importer name (if imported)"], ["importerAddress", "Importer address (if imported)"],
  ["netQuantity", "Net quantity *"], ["grossQuantity", "Gross quantity *"],
  ["quantityUnit", "Unit, e.g. N, g, ml *"], ["mfgDate", "Month/year of manufacture *"],
  ["batchNumber", "Batch or lot number *"],
];

const C = { teal: "#00788C", dark: "#003A44", line: "#C4EFE6", grey: "#64748B" };
const box = { background: "#fff", border: `1px solid ${C.line}`, borderRadius: 8, padding: "1rem 1.25rem", marginBottom: "1rem" };
const input = { width: "100%", boxSizing: "border-box", padding: "7px 9px", border: `1px solid ${C.line}`, borderRadius: 6, fontSize: "0.875rem" };
const btn = (primary) => ({
  padding: "7px 14px", borderRadius: 6, fontSize: "0.85rem", fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : `1px solid ${C.line}`, background: primary ? C.teal : "#fff", color: primary ? "#fff" : C.grey,
});

const toForm = (row) => {
  const f = { ...EMPTY };
  Object.keys(EMPTY).forEach((k) => { if (row[k] !== null && row[k] !== undefined) f[k] = row[k]; });
  return f;
};

export default function SupplierLabels() {
  const [data, setData] = useState({ labels: [], waiting: [] });
  const [form, setForm] = useState(EMPTY);
  const [msg, setMsg] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const r = await api.get("/api/admin/supplier-labels");
      setData({ labels: r.data.labels || [], waiting: r.data.waiting || [] });
    } catch {
      setMsg({ ok: false, text: "Could not load the supplier labels." });
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const pick = (row) => {
    const saved = data.labels.find((l) => l.sku?.toLowerCase() === String(row.sku || "").toLowerCase());
    setForm(saved ? toForm(saved) : toForm(row));
    setMsg(null);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const save = async () => {
    setBusy(true);
    setMsg(null);
    try {
      const r = await api.put("/api/admin/supplier-labels", form);
      const s = r.data.sweep || {};
      const text = s.busy ? "Saved. The drafts are already being checked."
        : s.published ? `Saved. ${s.published} product(s) went on sale.`
          : s.ready ? `Saved. ${s.ready} product(s) are ready, but preview mode is on, so nothing was published.`
            : "Saved. See below for anything still needed.";
      setMsg({ ok: true, text });
      setForm(EMPTY);
      load();
    } catch (e) {
      setMsg({ ok: false, text: e.response?.data?.error || "Could not save this label." });
    }
    setBusy(false);
  };

  return (
    <div style={{ maxWidth: 980 }}>
      <h1 style={{ fontSize: "1.4rem", fontWeight: 700, color: C.dark, margin: "0 0 0.25rem" }}>Supplier Labels</h1>
      <p style={{ color: C.grey, fontSize: "0.875rem", marginTop: 0 }}>
        Enter only what the supplier's pack label or written reply says. Never guess these details.
      </p>
      {msg && <div role="status" style={{ ...box, color: msg.ok ? C.teal : "#b91c1c" }}>{msg.text}</div>}

      <div style={box}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "0.75rem" }}>
          {FIELDS.map(([key, label]) => (
            <label key={key} style={{ fontSize: "0.8rem", color: C.dark, fontWeight: 600 }}>
              {label}
              <input
                style={{ ...input, marginTop: 3, fontWeight: 400 }}
                type={key === "mfgDate" ? "date" : "text"}
                value={form[key]}
                onChange={(e) => setForm({ ...form, [key]: e.target.value })}
              />
            </label>
          ))}
        </div>
        <label style={{ display: "flex", gap: 8, alignItems: "center", margin: "0.9rem 0", fontSize: "0.875rem", color: C.dark }}>
          <input type="checkbox" checked={form.photosAllowed} onChange={(e) => setForm({ ...form, photosAllowed: e.target.checked })} />
          The supplier has agreed in writing that we may use their photos of this product.
        </label>
        <button style={btn(true)} disabled={busy || !form.sku} onClick={save}>{busy ? "Saving..." : "Save label"}</button>{" "}
        <button style={btn(false)} onClick={() => setForm(EMPTY)}>Clear</button>
      </div>

      <h2 style={{ fontSize: "1.05rem", color: C.dark }}>Drafts waiting ({data.waiting.length})</h2>
      {data.waiting.length === 0 && <div style={{ ...box, color: C.grey }}>No drafts are waiting.</div>}
      {data.waiting.map((row) => (
        <div key={row.jobId} style={{ ...box, display: "flex", gap: 12, justifyContent: "space-between", flexWrap: "wrap" }}>
          <div style={{ flex: "1 1 320px", minWidth: 0 }}>
            <div style={{ fontWeight: 600, color: C.dark }}>{row.name || "Untitled draft"}</div>
            <div style={{ fontSize: "0.8rem", color: C.grey, overflowWrap: "anywhere" }}>{row.sku || "No item code"}</div>
            {row.note && <div style={{ fontSize: "0.8rem", color: "#b45309", marginTop: 4 }}>{row.note}</div>}
          </div>
          <button style={btn(false)} disabled={!row.sku} onClick={() => pick(row)}>Enter label</button>
        </div>
      ))}

      <h2 style={{ fontSize: "1.05rem", color: C.dark }}>Saved labels ({data.labels.length})</h2>
      {data.labels.map((l) => (
        <div key={l.id} style={{ ...box, display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
          <div style={{ minWidth: 0, overflowWrap: "anywhere" }}>
            <strong style={{ color: C.dark }}>{l.sku}</strong>
            <div style={{ fontSize: "0.8rem", color: C.grey }}>
              {l.manufacturerName || "No manufacturer"} · Photos {l.photosAllowed ? "allowed" : "not confirmed"}
            </div>
          </div>
          <button style={btn(false)} onClick={() => pick(l)}>Edit</button>
        </div>
      ))}
    </div>
  );
}
