import React, { useState, useEffect, useCallback } from "react";
import api from "../../api/axios";
import { confirmAction, showMessage } from "../../components/ConfirmDialog";

// What the website has sent to the ERP (ERPNext), what is waiting, and what failed and why.
const TYPE_LABELS = {
  ITEM: "Products",
  PURCHASE_BILL: "Purchase bills",
  SALES_INVOICE: "Customer invoices",
  SALES_RETURN: "Credit notes",
  CUSTOMER_PAYMENT: "Payments received",
  CUSTOMER_REFUND: "Refunds paid",
  COMMISSION_INVOICE: "Commission invoices",
  SELLER_PAYOUT: "Seller payouts",
};
const STATUS_LABELS = { SYNCED: "Sent", WAITING: "Waiting", FAILED: "Failed", CANCELLED: "Cancelled" };
const STATUS_COLORS = {
  SYNCED: { bg: "#E6F7EC", color: "#047857" },
  WAITING: { bg: "#FEF3C7", color: "#B45309" },
  FAILED: { bg: "#FEE2E2", color: "#B91C1C" },
  CANCELLED: { bg: "#F1F5F9", color: "#475569" },
};
// What each kind of record is called on the website, to make the number recognisable.
const ENTITY_LABELS = {
  ITEM: "Product #", PURCHASE_BILL: "Purchase order #", SALES_INVOICE: "Invoice #", SALES_RETURN: "Credit note #",
  CUSTOMER_PAYMENT: "Order #", CUSTOMER_REFUND: "Refund #", COMMISSION_INVOICE: "Commission invoice #",
  SELLER_PAYOUT: "Payout #",
};
const when = (d) => (d ? new Date(d).toLocaleString("en-IN", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—");

const th = { padding: "10px 12px", textAlign: "left", fontSize: "0.8rem", color: "#6b7280", fontWeight: 600 };
const td = { padding: "10px 12px", fontSize: "0.9rem", borderTop: "1px solid #f1f5f9", verticalAlign: "top" };
const btn = { padding: "0.5rem 0.9rem", borderRadius: 6, border: "1px solid #d1d5db", background: "#fff", cursor: "pointer", fontSize: "0.88rem" };
const primary = { ...btn, background: "#00788C", color: "#fff", border: "1px solid #00788C" };
const sel = { padding: "0.45rem 0.6rem", border: "1px solid #d1d5db", borderRadius: 6, fontSize: "0.88rem" };

const Badge = ({ status }) => {
  const c = STATUS_COLORS[status] || STATUS_COLORS.CANCELLED;
  return <span style={{ background: c.bg, color: c.color, padding: "2px 8px", borderRadius: 999, fontSize: "0.78rem", fontWeight: 600 }}>{STATUS_LABELS[status] || status}</span>;
};

const AdminErpSync = () => {
  const [status, setStatus] = useState(null);
  const [rows, setRows] = useState([]);
  const [type, setType] = useState("");
  const [state, setState] = useState("");
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [setupReport, setSetupReport] = useState(null);

  const load = useCallback(async () => {
    try {
      const params = {};
      if (type) params.type = type;
      if (state) params.status = state;
      const [s, r] = await Promise.all([
        api.get("/api/admin/erp/status"),
        api.get("/api/admin/erp/records", { params }),
      ]);
      setStatus(s.data);
      setRows(Array.isArray(r.data) ? r.data : []);
      setError("");
    } catch (e) {
      setError(e.response?.data?.error || "The ERP link status could not be loaded.");
    }
  }, [type, state]);

  useEffect(() => { load(); }, [load]);

  const run = async (key, fn, doneText) => {
    setBusy(key);
    try {
      const msg = await fn();
      showMessage(msg || doneText);
      load();
    } catch (e) {
      showMessage(e.response?.data?.error || "That did not work. The ERP may be unreachable; try again in a minute.");
    }
    setBusy("");
  };

  const syncNow = () => run("sync", async () => {
    const r = (await api.post("/api/admin/erp/sync")).data;
    const s = r.store || {};
    if (r.skipped) return r.skipped;
    return `Done. Sent ${(s.synced || 0) + (r.synced || 0)}, waiting ${s.waiting || 0}, failed ${(s.failed || 0) + (r.failed || 0)}.`;
  });

  const retryAll = async () => {
    if (!(await confirmAction("Try every failed record again, including ones that have already used up their automatic attempts?", { confirmLabel: "Retry all" }))) return;
    run("retryAll", async () => { await api.post("/api/admin/erp/sync", null, { params: { force: true } }); }, "Retried. Check the list for anything still failing.");
  };

  const retryOne = (row) => run("row" + row.id, async () => {
    const r = (await api.post(`/api/admin/erp/records/${row.id}/retry`)).data;
    return r.status === "SYNCED" ? "Sent to the ERP." : `Still ${STATUS_LABELS[r.status] || r.status}: ${r.message || ""}`;
  });

  const runSetup = () => run("setup", async () => {
    const r = (await api.post("/api/admin/erp/setup")).data;
    setSetupReport(Array.isArray(r) ? r : []);
    const failed = (r || []).filter((x) => x.result === "failed").length;
    return failed ? `${failed} setup step(s) failed - see the list below.` : "The ERP is set up for the website link.";
  });

  const alignStock = async () => {
    if (!(await confirmAction("Make the ERP's stock count match the website's stock for every store product now? Any difference is posted in the ERP as a stock adjustment.", { confirmLabel: "Match stock" }))) return;
    run("stock", async () => {
      const r = (await api.post("/api/admin/erp/stock/align")).data;
      if (r.skipped) return r.skipped;
      return r.itemsChanged ? `Stock adjusted for ${r.itemsChanged} product(s) (${r.document}).` : "Stock already matches.";
    });
  };

  return (
    <div style={{ padding: 24, maxWidth: 1200 }}>
      <h1 style={{ fontSize: "1.5rem", margin: "0 0 4px" }}>ERP Sync</h1>
      <p style={{ color: "#6b7280", margin: "0 0 16px", fontSize: "0.92rem" }}>
        The website sends its records to the accounts software (ERPNext) every 10 minutes. This page shows what was sent, what is waiting for something else first, and what failed and why.
      </p>

      {error && <div style={{ background: "#FEE2E2", color: "#B91C1C", padding: 12, borderRadius: 8, marginBottom: 16 }}>{error}</div>}

      {status && !status.configured && (
        <div style={{ background: "#FEF3C7", color: "#92400E", padding: 12, borderRadius: 8, marginBottom: 16 }}>
          The ERP link is switched off or not set up on the server, so nothing is being sent.
        </div>
      )}
      {status && status.configured && (status.draftsOnly || !status.totalsMustMatch) && (
        <div style={{ background: "#FEF3C7", color: "#92400E", padding: 12, borderRadius: 8, marginBottom: 16 }}>
          <strong>Test mode.</strong>{" "}
          {status.draftsOnly && "Invoices and payments are sent as drafts, so payments wait until their invoice is submitted in the ERP. "}
          {!status.totalsMustMatch && "Totals are not required to match. "}
          Turn test mode off on the server before using the ERP for real accounts.
        </div>
      )}

      {status && (
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
          {[["Sent", status.synced, "SYNCED"], ["Waiting", status.waiting, "WAITING"], ["Failed", status.failed, "FAILED"]].map(([label, n, key]) => (
            <button key={key} onClick={() => setState(state === key ? "" : key)}
              style={{ flex: "1 1 140px", textAlign: "left", padding: 14, borderRadius: 10, cursor: "pointer",
                border: state === key ? "2px solid #00788C" : "1px solid #e5e7eb", background: "#fff" }}>
              <div style={{ fontSize: "0.8rem", color: "#6b7280" }}>{label}</div>
              <div style={{ fontSize: "1.6rem", fontWeight: 700, color: STATUS_COLORS[key].color }}>{n ?? 0}</div>
            </button>
          ))}
        </div>
      )}

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 16 }}>
        <button style={primary} disabled={!!busy} onClick={syncNow}>{busy === "sync" ? "Sending…" : "Send now"}</button>
        <button style={btn} disabled={!!busy} onClick={retryAll}>{busy === "retryAll" ? "Retrying…" : "Retry all failed"}</button>
        <button style={btn} disabled={!!busy} onClick={alignStock}>{busy === "stock" ? "Matching…" : "Match stock now"}</button>
        <button style={btn} disabled={!!busy} onClick={runSetup}>{busy === "setup" ? "Checking…" : "Check ERP setup"}</button>
        {status?.erpUrl && <a href={status.erpUrl} target="_blank" rel="noreferrer" style={{ ...btn, textDecoration: "none", color: "#111" }}>Open ERP ↗</a>}
      </div>

      {setupReport && (
        <div style={{ border: "1px solid #e5e7eb", borderRadius: 10, padding: 12, marginBottom: 16, background: "#fff" }}>
          <div style={{ fontWeight: 600, marginBottom: 6 }}>ERP setup check</div>
          {setupReport.map((r) => (
            <div key={r.step} style={{ fontSize: "0.88rem", padding: "2px 0" }}>
              <span style={{ color: r.result === "failed" ? "#B91C1C" : "#047857" }}>{r.result === "failed" ? "✗" : "✓"}</span>{" "}
              {r.step} — {r.result}{r.detail ? `: ${r.detail}` : ""}
            </div>
          ))}
        </div>
      )}

      {status?.byType && (
        <div style={{ border: "1px solid #e5e7eb", borderRadius: 10, overflowX: "auto", marginBottom: 16, background: "#fff" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 520 }}>
            <thead><tr><th style={th}>What</th><th style={th}>Sent</th><th style={th}>Waiting</th><th style={th}>Failed</th></tr></thead>
            <tbody>
              {Object.entries(status.byType).map(([t, c]) => (
                <tr key={t} onClick={() => setType(type === t ? "" : t)} style={{ cursor: "pointer", background: type === t ? "#F0FAFB" : undefined }}>
                  <td style={td}>{TYPE_LABELS[t] || t}</td>
                  <td style={td}>{c.SYNCED || 0}</td>
                  <td style={td}>{c.WAITING || 0}</td>
                  <td style={{ ...td, color: c.FAILED ? "#B91C1C" : undefined, fontWeight: c.FAILED ? 600 : undefined }}>{c.FAILED || 0}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginBottom: 8 }}>
        <select style={sel} value={type} onChange={(e) => setType(e.target.value)}>
          <option value="">All kinds</option>
          {Object.entries(TYPE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select style={sel} value={state} onChange={(e) => setState(e.target.value)}>
          <option value="">Any status</option>
          {Object.entries(STATUS_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <span style={{ color: "#6b7280", fontSize: "0.85rem" }}>Latest {rows.length} shown</span>
      </div>

      <div style={{ border: "1px solid #e5e7eb", borderRadius: 10, overflowX: "auto", background: "#fff" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 760 }}>
          <thead>
            <tr><th style={th}>Website record</th><th style={th}>Status</th><th style={th}>In the ERP</th><th style={th}>Details</th><th style={th}>Updated</th><th style={th}></th></tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr><td style={{ ...td, color: "#6b7280" }} colSpan={6}>Nothing here yet. Records appear once the website has something of this kind to send.</td></tr>
            )}
            {rows.map((r) => (
              <tr key={r.id}>
                <td style={td}>
                  <div>{TYPE_LABELS[r.type] || r.type}</div>
                  <div style={{ color: "#6b7280", fontSize: "0.82rem" }}>{(ENTITY_LABELS[r.type] || "#") + r.entityId}</div>
                </td>
                <td style={td}><Badge status={r.status} /></td>
                <td style={td}>
                  {r.erpLink ? <a href={r.erpLink} target="_blank" rel="noreferrer">{r.erpDocName}</a> : (r.erpDocName || "—")}
                </td>
                <td style={{ ...td, maxWidth: 420, color: r.status === "FAILED" ? "#B91C1C" : "#374151", fontSize: "0.85rem", wordBreak: "break-word" }}>
                  {r.message || (r.status === "SYNCED" ? "" : "—")}
                  {r.status === "FAILED" && r.attempts > 0 && <div style={{ color: "#6b7280", marginTop: 2 }}>Tried {r.attempts} time(s)</div>}
                </td>
                <td style={{ ...td, whiteSpace: "nowrap" }}>{when(r.updatedAt)}</td>
                <td style={td}>
                  {(r.status === "FAILED" || r.status === "WAITING") && (
                    <button style={btn} disabled={!!busy} onClick={() => retryOne(r)}>{busy === "row" + r.id ? "…" : "Retry"}</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default AdminErpSync;
