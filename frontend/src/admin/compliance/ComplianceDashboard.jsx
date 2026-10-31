import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../api/axios";
import { ScoreBadge } from "./ComplianceEditor";
import { CATEGORY_TYPES, STATUS_LABEL } from "./complianceFields";

/*
  Admin compliance dashboard: how much of the catalogue may be published, and what is holding
  the rest back. Every product is checked when the page loads; nothing is saved by looking.
*/

const card = { background: "#fff", border: "1px solid #CFE8D6", borderRadius: 8, padding: "0.9rem 1rem" };
const input = { padding: "7px 10px", border: "1px solid #CFE8D6", borderRadius: 6, fontSize: "0.85rem", background: "#fff" };

const Tile = ({ label, value, color, active, onClick }) => (
  <button onClick={onClick} style={{ ...card, textAlign: "left", cursor: "pointer", outline: active ? "2px solid #146C43" : "none" }}>
    <div style={{ fontSize: "0.75rem", color: "#64748B" }}>{label}</div>
    <div style={{ fontSize: "1.5rem", fontWeight: 800, color: color || "#0B3D2E" }}>{value ?? "–"}</div>
  </button>
);

const QUICK = {
  all: () => true,
  compliant: (r) => r.band === "PUBLISHABLE",
  nonCompliant: (r) => r.band !== "PUBLISHABLE",
  warning: (r) => r.band === "WARNING",
  blocked: (r) => r.band === "BLOCKED",
  gstHsnMissing: (r) => r.gstHsnMissing,
  fssaiPending: (r) => r.fssaiPending,
  bisPending: (r) => r.bisPending,
  liveBlocked: (r) => r.live && !r.publishable,
};

const ComplianceDashboard = () => {
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [quick, setQuick] = useState("all");
  const [search, setSearch] = useState("");
  const [type, setType] = useState("");
  const [status, setStatus] = useState("");
  const [live, setLive] = useState("");

  const load = () => {
    setData(null); setError("");
    api.get("/api/compliance/dashboard")
      .then((r) => setData(r.data))
      .catch((e) => setError(e?.response?.data?.error || "Could not load the compliance dashboard."));
  };
  useEffect(load, []);

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (data?.products || []).filter((r) =>
      QUICK[quick](r)
      && (!type || r.categoryType === type)
      && (!status || r.status === status)
      && (!live || (live === "yes") === !!r.live)
      && (!q || [r.name, r.sku, r.brand, r.seller, r.category, String(r.productId)].some((v) => v && String(v).toLowerCase().includes(q))));
  }, [data, quick, search, type, status, live]);

  const t = data?.totals || {};
  const pct = t.total ? Math.round((100 * t.compliant) / t.total) : 0;

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 8, marginBottom: "1rem" }}>
        <div>
          <h1 style={{ fontSize: "1.4rem", fontWeight: 700, margin: 0 }}>Product compliance</h1>
          <div style={{ fontSize: "0.8rem", color: "#64748B" }}>
            {data ? `${pct}% of ${t.total} products fully compliant · ${t.live} on sale` : "Checking every product..."}
            {data && !data.gateEnabled && <span style={{ color: "#b91c1c" }}> · publish block is OFF (compliance.publish_gate)</span>}
            {data?.enforceLive && <span style={{ color: "#b45309" }}> · live enforcement is ON</span>}
          </div>
        </div>
        <button onClick={load} style={{ ...input, cursor: "pointer", fontWeight: 600, color: "#146C43" }}>Re-check</button>
      </div>

      {error && <div style={{ ...card, background: "#fef2f2", color: "#b91c1c", marginBottom: "1rem" }}>{error}</div>}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: "0.6rem", marginBottom: "1rem" }}>
        <Tile label="Total products" value={t.total} active={quick === "all"} onClick={() => setQuick("all")} />
        <Tile label="Compliant (100)" value={t.compliant} color="#146C43" active={quick === "compliant"} onClick={() => setQuick("compliant")} />
        <Tile label="Non-compliant" value={t.nonCompliant} color="#b91c1c" active={quick === "nonCompliant"} onClick={() => setQuick("nonCompliant")} />
        <Tile label="Warning (90-99)" value={t.warning} color="#b45309" active={quick === "warning"} onClick={() => setQuick("warning")} />
        <Tile label="Blocked" value={t.blocked} color="#b91c1c" active={quick === "blocked"} onClick={() => setQuick("blocked")} />
        <Tile label="GST / HSN missing" value={t.gstHsnMissing} color="#b91c1c" active={quick === "gstHsnMissing"} onClick={() => setQuick("gstHsnMissing")} />
        <Tile label="FSSAI pending" value={t.fssaiPending} color="#b45309" active={quick === "fssaiPending"} onClick={() => setQuick("fssaiPending")} />
        <Tile label="BIS pending" value={t.bisPending} color="#b45309" active={quick === "bisPending"} onClick={() => setQuick("bisPending")} />
      </div>

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: "0.75rem" }}>
        <input style={{ ...input, flex: "1 1 220px" }} placeholder="Search name, SKU, brand, seller, category" value={search} onChange={(e) => setSearch(e.target.value)} />
        <select style={input} value={type} onChange={(e) => setType(e.target.value)} aria-label="Category type">
          <option value="">All category types</option>
          {CATEGORY_TYPES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
        </select>
        <select style={input} value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Workflow status">
          <option value="">Any workflow status</option>
          {Object.entries(STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select style={input} value={live} onChange={(e) => setLive(e.target.value)} aria-label="On sale">
          <option value="">On sale or not</option>
          <option value="yes">On sale</option>
          <option value="no">Not on sale</option>
        </select>
        <button style={{ ...input, cursor: "pointer" }} onClick={() => setQuick("liveBlocked")}>On sale but blocked</button>
      </div>

      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.83rem" }}>
          <thead>
            <tr style={{ background: "#F6FBF8", textAlign: "left" }}>
              {["Product", "Seller", "Type", "Workflow", "Score", "Top gaps", ""].map((h) => <th key={h} style={{ padding: "8px 10px" }}>{h}</th>)}
            </tr>
          </thead>
          <tbody>
            {!data && !error && <tr><td colSpan={7} style={{ padding: 16, color: "#64748B" }}>Loading...</td></tr>}
            {data && rows.length === 0 && <tr><td colSpan={7} style={{ padding: 16, color: "#64748B" }}>No products match.</td></tr>}
            {rows.map((r) => (
              <tr key={r.productId} style={{ borderTop: "1px solid #EAF7EE" }}>
                <td style={{ padding: "8px 10px" }}>
                  <div style={{ fontWeight: 600 }}>{r.name}</div>
                  <div style={{ color: "#64748B", fontSize: "0.75rem" }}>#{r.productId}{r.sku ? ` · ${r.sku}` : ""}{r.category ? ` · ${r.category}` : ""}</div>
                </td>
                <td style={{ padding: "8px 10px" }}>{r.seller || (r.sellerId ? `#${r.sellerId}` : <span style={{ color: "#b91c1c" }}>none</span>)}</td>
                <td style={{ padding: "8px 10px" }}>{r.categoryType}</td>
                <td style={{ padding: "8px 10px" }}>{STATUS_LABEL[r.status] || r.status}{r.live ? <span style={{ color: "#146C43" }}> · on sale</span> : ""}</td>
                <td style={{ padding: "8px 10px", whiteSpace: "nowrap" }}><ScoreBadge score={r.score} band={r.band} /></td>
                <td style={{ padding: "8px 10px", color: "#475569", maxWidth: 360 }}>
                  {(r.issues || []).slice().sort((a, b) => b.critical - a.critical).slice(0, 4).map((i) => i.label).join(", ")}
                  {(r.issues || []).length > 4 ? ` +${r.issues.length - 4} more` : ""}
                </td>
                <td style={{ padding: "8px 10px" }}>
                  <button onClick={() => navigate(`/admin/products/${r.productId}/compliance`)}
                    style={{ padding: "4px 10px", border: "1px solid #CFE8D6", borderRadius: 4, background: "#EAF7EE", color: "#146C43", cursor: "pointer", fontSize: "0.75rem", fontWeight: 600 }}>
                    Fix
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {data && <div style={{ fontSize: "0.75rem", color: "#64748B", marginTop: 6 }}>{rows.length} of {t.total} shown</div>}
    </div>
  );
};

export default ComplianceDashboard;
