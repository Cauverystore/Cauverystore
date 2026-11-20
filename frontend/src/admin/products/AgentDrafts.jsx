import React, { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../api/axios";

/**
 * Agent Drafts: queue supplier product links for the separate Listing Agent, then review what it
 * drafted. A draft never goes on sale by itself: "Open in Add Product" fills the normal form, and
 * the product is only created when an admin presses Create there.
 */
const TABS = [
  { key: "ready", label: "Ready to review" },
  { key: "waiting", label: "Waiting for agent" },
  { key: "failed", label: "Could not draft" },
  { key: "done", label: "Done" },
];

const C = {
  teal: "#00788C", dark: "#003A44", line: "#C4EFE6", soft: "#E6F8F3", grey: "#64748B",
};

const box = { background: "#fff", border: `1px solid ${C.line}`, borderRadius: 8, padding: "1rem 1.25rem", marginBottom: "1rem" };
const btn = (primary) => ({
  padding: "7px 14px", borderRadius: 6, fontSize: "0.85rem", fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : `1px solid ${C.line}`, background: primary ? C.teal : "#fff", color: primary ? "#fff" : C.grey,
});

export default function AgentDrafts() {
  const navigate = useNavigate();
  const [tab, setTab] = useState("ready");
  const [rows, setRows] = useState([]);
  const [counts, setCounts] = useState({});
  const [loading, setLoading] = useState(true);
  const [links, setLinks] = useState("");
  const [cost, setCost] = useState("");
  const [msg, setMsg] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [r, c] = await Promise.all([
        api.get("/api/admin/agent-jobs", { params: { view: tab } }),
        api.get("/api/admin/agent-jobs/counts"),
      ]);
      setRows(Array.isArray(r.data) ? r.data : []);
      setCounts(c.data || {});
    } catch {
      setMsg({ ok: false, text: "Could not load the agent drafts." });
    }
    setLoading(false);
  }, [tab]);

  useEffect(() => { load(); }, [load]);

  const queue = async () => {
    if (!links.trim()) return;
    setBusy(true);
    setMsg(null);
    try {
      const r = await api.post("/api/admin/agent-jobs/queue", { links, supplierCost: cost || null });
      const { added, skipped = [], refused = [] } = r.data;
      let text = `${added} link${added === 1 ? "" : "s"} queued. The Listing Agent will draft ${added === 1 ? "it" : "them"} on its next run.`;
      if (skipped.length) text += ` ${skipped.length} already queued or drafted, so skipped.`;
      if (refused.length) text += ` ${refused.length} not accepted (only DeoDap and Baapstore product links).`;
      setMsg({ ok: true, text });
      setLinks("");
      setCost("");
      load();
    } catch (e) {
      setMsg({ ok: false, text: e.response?.data?.error || "Could not queue the links." });
    }
    setBusy(false);
  };

  const act = async (id, action) => {
    try {
      await api.put(`/api/admin/agent-jobs/${id}/${action}`);
      load();
    } catch (e) {
      setMsg({ ok: false, text: e.response?.data?.error || "That did not work." });
    }
  };

  const open = (row) => navigate("/admin/products/add", { state: { agentJob: { id: row.id, sourceUrl: row.sourceUrl, draft: row.draft } } });

  return (
    <div style={{ maxWidth: 960, margin: "0 auto", padding: "1.5rem", boxSizing: "border-box" }}>
      <h1 style={{ fontSize: "1.4rem", fontWeight: 700, color: C.dark, margin: "0 0 0.25rem" }}>Agent Drafts</h1>
      <p style={{ color: C.grey, fontSize: "0.85rem", margin: "0 0 1rem" }}>
        Paste supplier product links. The Listing Agent checks stock and price, writes the listing and sends it here.
        Nothing goes on sale until you open a draft, check it and press Create.
      </p>

      <div style={box}>
        <label style={{ display: "block", fontWeight: 600, fontSize: "0.85rem", color: "#374151", marginBottom: 4 }}>
          Supplier product links (one per line)
        </label>
        <textarea
          value={links}
          onChange={(e) => setLinks(e.target.value)}
          placeholder={"https://deodap.in/products/...\nhttps://baapstore.com/products/..."}
          style={{ width: "100%", minHeight: 90, padding: "8px 12px", border: `1px solid ${C.line}`, borderRadius: 6, fontSize: "0.85rem", boxSizing: "border-box", fontFamily: "inherit" }}
        />
        <div style={{ display: "flex", gap: 10, alignItems: "flex-end", flexWrap: "wrap", marginTop: 8 }}>
          <div>
            <label style={{ display: "block", fontSize: "0.75rem", color: C.grey, marginBottom: 2 }}>
              Your cost per piece (₹, optional)
            </label>
            <input
              type="number" min="0" step="0.01" value={cost} onChange={(e) => setCost(e.target.value)}
              placeholder="Supplier price used if blank"
              style={{ padding: "7px 10px", border: `1px solid ${C.line}`, borderRadius: 6, fontSize: "0.85rem", width: 220 }}
            />
          </div>
          <button onClick={queue} disabled={busy || !links.trim()} style={{ ...btn(true), opacity: busy || !links.trim() ? 0.6 : 1 }}>
            {busy ? "Queuing..." : "Queue for the agent"}
          </button>
        </div>
        <div style={{ fontSize: "0.72rem", color: C.grey, marginTop: 6 }}>
          Price rule applied by the store: cost × 2, rounded up to end in 9. A cost typed here applies to every link in this batch.
        </div>
      </div>

      {msg && (
        <div style={{ padding: "10px 14px", borderRadius: 6, marginBottom: "1rem", fontSize: "0.85rem",
          background: msg.ok ? C.soft : "#fef2f2", color: msg.ok ? C.teal : "#dc2626", border: `1px solid ${msg.ok ? C.line : "#fecaca"}` }}>
          {msg.text}
        </div>
      )}

      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: "0.75rem" }}>
        {TABS.map((t) => {
          const n = counts[t.key];
          const on = tab === t.key;
          return (
            <button key={t.key} onClick={() => setTab(t.key)}
              style={{ ...btn(on), background: on ? C.dark : "#fff", color: on ? "#fff" : C.grey }}>
              {t.label}{typeof n === "number" ? ` (${n})` : ""}
            </button>
          );
        })}
        <button onClick={load} style={{ ...btn(false), marginLeft: "auto" }}>Refresh</button>
      </div>

      {loading ? (
        <div style={{ color: C.grey, padding: "1rem" }}>Loading...</div>
      ) : rows.length === 0 ? (
        <div style={{ ...box, color: C.grey, textAlign: "center" }}>
          {tab === "ready" ? "No drafts waiting. Queue some links above; the agent runs on its schedule." : "Nothing here."}
        </div>
      ) : rows.map((r) => <Row key={r.id} row={r} onOpen={open} onAct={act} />)}
    </div>
  );
}

function Row({ row, onOpen, onAct }) {
  const d = row.draft || {};
  const warnings = Array.isArray(d.warnings) ? d.warnings : [];
  return (
    <div style={box}>
      <div style={{ display: "flex", gap: 12, alignItems: "flex-start", flexWrap: "wrap" }}>
        {Array.isArray(d.imageUrls) && d.imageUrls[0] && (
          <img src={d.imageUrls[0]} alt={d.name ? `Supplier photo of ${d.name}` : "Supplier product photo"} style={{ width: 72, height: 72, objectFit: "cover", borderRadius: 6, border: `1px solid ${C.line}` }} />
        )}
        <div style={{ flex: 1, minWidth: 220 }}>
          <div style={{ fontWeight: 700, color: C.dark, fontSize: "0.95rem" }}>{d.name || row.sourceUrl}</div>
          <a href={row.sourceUrl} target="_blank" rel="noopener noreferrer" style={{ fontSize: "0.75rem", color: C.teal, wordBreak: "break-all" }}>
            {row.sourceUrl}
          </a>
          {row.status === "READY" && (
            <div style={{ fontSize: "0.8rem", color: "#374151", marginTop: 6 }}>
              {d.suggestedPrice ? <>Price <b>₹{d.suggestedPrice}</b> (cost ₹{d.supplierCost}) · </> : null}
              {d.hsnCode ? <>HSN {d.hsnCode} · </> : null}
              {d.shortDescription}
            </div>
          )}
          {row.note && <div style={{ fontSize: "0.8rem", color: "#b45309", marginTop: 6 }}>{row.note}</div>}
          {warnings.length > 0 && (
            <ul style={{ margin: "6px 0 0", paddingLeft: 18, fontSize: "0.75rem", color: "#b45309" }}>
              {warnings.map((w, i) => <li key={i}>{w}</li>)}
            </ul>
          )}
          {row.status === "USED" && row.productId && (
            <div style={{ fontSize: "0.8rem", color: C.teal, marginTop: 6 }}>Created as product #{row.productId}</div>
          )}
          {row.status === "REJECTED" && <div style={{ fontSize: "0.8rem", color: C.grey, marginTop: 6 }}>Rejected</div>}
          {(row.status === "QUEUED" || row.status === "WORKING") && (
            <div style={{ fontSize: "0.8rem", color: C.grey, marginTop: 6 }}>
              {row.status === "WORKING" ? "The agent is working on this now." : "Waiting for the agent's next run."}
            </div>
          )}
        </div>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {row.status === "READY" && <button style={btn(true)} onClick={() => onOpen(row)}>Open in Add Product</button>}
          {(row.status === "READY" || row.status === "FAILED") && <button style={btn(false)} onClick={() => onAct(row.id, "reject")}>Reject</button>}
          {(row.status === "FAILED" || row.status === "REJECTED") && <button style={btn(false)} onClick={() => onAct(row.id, "requeue")}>Try again</button>}
        </div>
      </div>
    </div>
  );
}
