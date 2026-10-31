import React, { useCallback, useEffect, useMemo, useState } from "react";
import api from "../../api/axios";
import { promptText } from "../../components/ConfirmDialog";
import { BASIC_FIELDS, BAND_STYLE, STATUS_LABEL, visibleSections, CATEGORY_SECTIONS, SECTIONS } from "./complianceFields";

/*
  Compliance details for one product: the score and what is missing, the product fields the
  shop shows (MRP, origin, manufacturer, warranty, returns), the compliance record, and the
  approval workflow. Used by admins (product compliance page) and by the seller wizard.
*/

const S = {
  card: { background: "#fff", border: "1px solid #CFE8D6", borderRadius: 8, padding: "1.1rem", marginBottom: "1rem" },
  title: { fontSize: "0.95rem", fontWeight: 700, color: "#0B3D2E", margin: "0 0 0.75rem", paddingBottom: "0.4rem", borderBottom: "2px solid #EAF7EE" },
  grid: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "0.75rem" },
  label: { display: "block", fontSize: "0.8rem", fontWeight: 600, color: "#374151", marginBottom: 3 },
  input: { width: "100%", padding: "8px 10px", border: "1px solid #CFE8D6", borderRadius: 6, fontSize: "0.88rem", boxSizing: "border-box", background: "#fff" },
  err: { color: "#dc2626", fontSize: "0.7rem", marginTop: 2 },
  missing: { borderColor: "#f59e0b", background: "#fffbeb" },
  btn: { padding: "7px 14px", border: "none", borderRadius: 6, cursor: "pointer", fontSize: "0.85rem", fontWeight: 600 },
};
const primary = { ...S.btn, background: "#146C43", color: "#fff" };
const secondary = { ...S.btn, background: "#fff", color: "#146C43", border: "1px solid #CFE8D6" };
const danger = { ...S.btn, background: "#fff", color: "#b91c1c", border: "1px solid #fecaca" };

const NUMBER_KEYS = new Set(["netQuantity", "grossQuantity", "minOrderQty", "maxOrderQty"]);
export const ALL_DETAIL_KEYS = [...new Set([
  ...SECTIONS.flatMap((s) => s.fields.map((f) => f.key)),
  ...Object.values(CATEGORY_SECTIONS).flatMap((fs) => fs.map((f) => f.key)),
])];

export const errorText = (e, fallback) => e?.response?.data?.error || e?.response?.data?.message || fallback;

/** The form state for a compliance record from the server (or none). */
export const detailsFromServer = (d) =>
  Object.fromEntries(ALL_DETAIL_KEYS.map((k) => [k, (d || {})[k] ?? (k === "packerSameAsManufacturer" ? false : "")]));

/** The PUT body: every editable field, blanks as null, numbers and yes/no converted. */
export const detailsBody = (details) => {
  const body = {};
  ALL_DETAIL_KEYS.forEach((k) => {
    let v = details[k];
    if (v === "" || v === undefined) v = null;
    if (v !== null && NUMBER_KEYS.has(k)) v = Number(v);
    if (k === "prescriptionRequired" && v !== null) v = v === true || v === "true";
    if (typeof v === "string") v = v.trim();
    body[k] = v;
  });
  return body;
};

/** Client-side format checks for the given fields; returns {key: message}. */
export const checkFields = (fields, values) => {
  const errs = {};
  fields.forEach((f) => {
    if (f.check) { const ok = f.check(String(values[f.key] ?? "")); if (ok !== true) errs[f.key] = ok; }
  });
  return errs;
};

/** One input, styled and marked when the last check found it missing. */
export const ComplianceField = ({ f, value, onChange, missing, error }) => {
  const style = { ...S.input, ...(missing ? S.missing : {}) };
  if (f.type === "checkbox") {
    return (
      <div>
        <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: "0.85rem" }}>
          <input type="checkbox" checked={!!value} onChange={(e) => onChange(e.target.checked)} /> {f.label}
        </label>
      </div>
    );
  }
  let input;
  if (f.options) {
    const opts = f.options.map((o) => (Array.isArray(o) ? o : [o, o]));
    input = (
      <select style={style} value={value === null || value === undefined ? "" : String(value)} onChange={(e) => onChange(e.target.value)}>
        <option value="">Select</option>
        {opts.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
      </select>
    );
  } else if (f.type === "textarea") {
    input = <textarea style={{ ...style, minHeight: 64, resize: "vertical" }} value={value ?? ""} onChange={(e) => onChange(e.target.value)} />;
  } else {
    input = <input style={style} type={f.type || "text"} value={value ?? ""} min={f.type === "number" ? 0 : undefined}
      step={f.type === "number" ? "any" : undefined} onChange={(e) => onChange(e.target.value)} />;
  }
  return (
    <div>
      <label style={S.label}>{f.label}{missing ? " *" : ""}</label>
      {input}
      {error && <div style={S.err}>{error}</div>}
    </div>
  );
};

export const ScoreBadge = ({ score, band }) => {
  const s = BAND_STYLE[band] || BAND_STYLE.BLOCKED;
  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: 6, background: s.bg, color: s.fg, borderRadius: 999, padding: "2px 10px", fontSize: "0.78rem", fontWeight: 700 }}>
      {score ?? "–"}<span style={{ fontWeight: 500 }}>{s.text}</span>
    </span>
  );
};

const ComplianceEditor = ({ productId, isAdmin = false, onChanged }) => {
  const [data, setData] = useState(null);
  const [basics, setBasics] = useState({});
  const [details, setDetails] = useState({});
  const [fieldErrors, setFieldErrors] = useState({});
  const [busy, setBusy] = useState("");
  const [msg, setMsg] = useState(null);

  const load = useCallback(async () => {
    try {
      const r = await api.get(`/api/compliance/products/${productId}`);
      setData(r.data);
      const p = r.data.product || {};
      setBasics(Object.fromEntries(BASIC_FIELDS.map((f) => [f.key, p[f.key] ?? ""])));
      setDetails(detailsFromServer(r.data.details));
    } catch (e) {
      setMsg({ type: "error", text: errorText(e, "Could not load compliance details.") });
    }
  }, [productId]);

  useEffect(() => { load(); }, [load]);

  const result = data?.compliance;
  const missing = useMemo(() => new Set(result?.missingFields || []), [result]);
  const sections = visibleSections(data?.categoryType, basics.countryOfOrigin, details);

  const afterSave = (r, text) => {
    setData((d) => ({ ...d, ...r.data, compliance: r.data.compliance, details: r.data.details ?? d.details, status: r.data.status ?? d.status }));
    setMsg({ type: "success", text });
    onChanged && onChanged(r.data);
  };

  const saveBasics = async () => {
    setBusy("basics"); setMsg(null);
    try {
      const r = await api.put(`/api/compliance/products/${productId}/basics`, { ...basics, mrp: basics.mrp === "" ? null : basics.mrp });
      afterSave(r, "Product details saved.");
    } catch (e) {
      setMsg({ type: "error", text: errorText(e, "Could not save the product details.") });
    }
    setBusy("");
  };

  const saveDetails = async () => {
    const errs = checkFields(sections.flatMap((s) => s.fields), details);
    setFieldErrors(errs);
    if (Object.keys(errs).length) { setMsg({ type: "error", text: "Fix the highlighted fields first." }); return; }
    setBusy("details"); setMsg(null);
    try {
      const r = await api.put(`/api/compliance/products/${productId}`, detailsBody(details));
      afterSave(r, "Compliance details saved.");
    } catch (e) {
      setMsg({ type: "error", text: errorText(e, "Could not save the compliance details.") });
    }
    setBusy("");
  };

  const act = async (action) => {
    let note = null;
    if (action === "reject") {
      note = await promptText("Why is this listing being sent back? The seller sees this note.");
      if (note === null || !note.trim()) return;
    }
    setBusy(action); setMsg(null);
    try {
      const r = await api.post(`/api/compliance/products/${productId}/workflow/${action}`, note ? { note } : {});
      await load();
      setMsg({ type: r.data.status === "DRAFT" && action !== "reject" ? "error" : "success",
        text: r.data.statusNote || `Now: ${STATUS_LABEL[r.data.status] || r.data.status}.` });
      onChanged && onChanged(r.data);
    } catch (e) {
      setMsg({ type: "error", text: errorText(e, "That step could not be completed.") });
    }
    setBusy("");
  };

  if (!data) return <div style={S.card}>{msg ? <div style={S.err}>{msg.text}</div> : "Loading compliance details..."}</div>;

  const status = data.status;
  const field = (f, value, onChange) => (
    <ComplianceField key={f.key} f={f} value={value} onChange={onChange} missing={missing.has(f.key)} error={fieldErrors[f.key]} />
  );

  const critical = (result?.issues || []).filter((i) => i.critical);
  const other = (result?.issues || []).filter((i) => !i.critical);

  return (
    <div>
      <div style={{ ...S.card, display: "flex", flexWrap: "wrap", gap: "1rem", alignItems: "center", justifyContent: "space-between" }}>
        <div>
          <div style={{ fontSize: "0.8rem", color: "#64748B" }}>Compliance score</div>
          <div style={{ fontSize: "1.8rem", fontWeight: 800, color: "#0B3D2E" }}>{result?.score ?? "–"}<span style={{ fontSize: "0.9rem", color: "#64748B" }}>/100</span></div>
          <ScoreBadge score={result?.score} band={result?.band} />
        </div>
        <div style={{ fontSize: "0.85rem" }}>
          <div><strong>Workflow:</strong> {STATUS_LABEL[status] || status}{data.product?.live ? " · on sale" : " · not on sale"}</div>
          <div><strong>Category type:</strong> {data.categoryType}</div>
          {data.details?.statusNote && <div style={{ color: "#b45309", maxWidth: 420 }}>{data.details.statusNote}</div>}
        </div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {status === "DRAFT" && <button style={primary} disabled={!!busy} onClick={() => act("submit")}>Submit for review</button>}
          {status === "SELLER_REVIEW" && <button style={primary} disabled={!!busy} onClick={() => act("confirm")}>Confirm and send to admin</button>}
          {status === "SELLER_REVIEW" && <button style={danger} disabled={!!busy} onClick={() => act("reject")}>Withdraw</button>}
          {isAdmin && status === "ADMIN_REVIEW" && <button style={primary} disabled={!!busy} onClick={() => act("approve")}>Approve</button>}
          {isAdmin && status === "APPROVED" && <button style={primary} disabled={!!busy} onClick={() => act("publish")}>Publish</button>}
          {isAdmin && (status === "ADMIN_REVIEW" || status === "APPROVED") && <button style={danger} disabled={!!busy} onClick={() => act("reject")}>Send back</button>}
        </div>
      </div>

      {msg && (
        <div style={{ ...S.card, background: msg.type === "error" ? "#fef2f2" : "#EAF7EE", color: msg.type === "error" ? "#b91c1c" : "#146C43", borderColor: msg.type === "error" ? "#fecaca" : "#CFE8D6" }}>
          {msg.text}
        </div>
      )}

      {(critical.length > 0 || other.length > 0) && (
        <div style={S.card}>
          <h3 style={S.title}>What is missing</h3>
          {critical.length > 0 && <>
            <div style={{ fontWeight: 600, color: "#b91c1c", fontSize: "0.85rem", marginBottom: 4 }}>Blocks publishing</div>
            <ul style={{ margin: "0 0 0.75rem 1.1rem", fontSize: "0.85rem" }}>{critical.map((i) => <li key={i.field}>{i.message}</li>)}</ul>
          </>}
          {other.length > 0 && <>
            <div style={{ fontWeight: 600, color: "#b45309", fontSize: "0.85rem", marginBottom: 4 }}>Lowers the score</div>
            <ul style={{ margin: "0 0 0 1.1rem", fontSize: "0.85rem" }}>{other.map((i) => <li key={i.field}>{i.message}</li>)}</ul>
          </>}
          <div style={{ fontSize: "0.75rem", color: "#64748B", marginTop: 8 }}>
            HSN, GST rate, price and seller are set on the product form. 100 publishes; 90-99 publishes with a warning; below 90 is blocked.
          </div>
        </div>
      )}

      <div style={S.card}>
        <h3 style={S.title}>Shown on the product page</h3>
        <div style={S.grid}>
          {BASIC_FIELDS.map((f) => field(f, basics[f.key], (v) => setBasics((b) => ({ ...b, [f.key]: v }))))}
        </div>
        <div style={{ marginTop: 10 }}>
          <button style={secondary} disabled={!!busy} onClick={saveBasics}>{busy === "basics" ? "Saving..." : "Save product details"}</button>
        </div>
      </div>

      {sections.map((s) => (
        <div key={s.id} style={S.card}>
          <h3 style={S.title}>{s.title}</h3>
          <div style={S.grid}>
            {s.fields.map((f) => field(f, details[f.key], (v) => setDetails((d) => ({ ...d, [f.key]: v }))))}
          </div>
        </div>
      ))}
      <div style={{ marginBottom: "1rem" }}>
        <button style={primary} disabled={!!busy} onClick={saveDetails}>{busy === "details" ? "Saving..." : "Save compliance details"}</button>
      </div>

      {data.history?.length > 0 && (
        <div style={S.card}>
          <h3 style={S.title}>History</h3>
          <table style={{ width: "100%", fontSize: "0.8rem", borderCollapse: "collapse" }}>
            <tbody>
              {data.history.slice().reverse().map((h) => (
                <tr key={h.id} style={{ borderBottom: "1px solid #EAF7EE" }}>
                  <td style={{ padding: 6, whiteSpace: "nowrap", color: "#64748B" }}>{String(h.createdAt || "").replace("T", " ").slice(0, 16)}</td>
                  <td style={{ padding: 6 }}>{h.action}</td>
                  <td style={{ padding: 6 }}>{STATUS_LABEL[h.fromStatus] || h.fromStatus} → {STATUS_LABEL[h.toStatus] || h.toStatus}</td>
                  <td style={{ padding: 6 }}>{h.score ?? ""}</td>
                  <td style={{ padding: 6, color: "#64748B" }}>{h.actor}</td>
                  <td style={{ padding: 6 }}>{h.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default ComplianceEditor;
