import React, { useState, useEffect, useCallback } from "react";
import api from "../../api/axios";

const inp = { width: "100%", padding: "0.5rem", border: "1px solid #d1d5db", borderRadius: 6, fontSize: "0.85rem" };
const card = { background: "#fff", border: "1px solid #e5e7eb", borderRadius: 10, padding: "1.25rem" };
const btn = { padding: "0.4rem 0.85rem", border: "none", borderRadius: 6, cursor: "pointer", fontSize: "0.8rem", fontWeight: 500 };

const IMPORTER = process.env.REACT_APP_GST_IMPORTER_URL || "https://gst-importer-api-production.up.railway.app";
const TOKEN = process.env.REACT_APP_GST_IMPORTER_TOKEN || "";

const KINDS = [
  { key: "state", label: "States" },
  { key: "country", label: "Countries" },
  { key: "currency", label: "Currencies" },
  { key: "port", label: "Ports" },
  { key: "uqc", label: "Units" },
];

/**
 * The five GSTN master lists (states, countries, currencies, reported ports,
 * units of measure) as synced into the importer database. Read-only view.
 */
const AdminGstMasters = () => {
  const [active, setActive] = useState("state");
  const [rows, setRows] = useState([]);
  const [counts, setCounts] = useState(null);
  const [lastRun, setLastRun] = useState(null);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const headers = TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {};

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [list, all] = await Promise.all([
        api.get(`${IMPORTER}/masters/${active}`, { headers }),
        api.get(`${IMPORTER}/masters`, { headers }),
      ]);
      setRows((list.data && list.data.rows) || []);
      setCounts((all.data && all.data.tables) || null);
    } catch {
      setError("Could not load the master lists from the importer API.");
    } finally {
      setLoading(false);
    }
  }, [active]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    api.get(`${IMPORTER}/update-gst-master/status`, { headers })
      .then(r => setLastRun(r.data))
      .catch(() => {});
  }, []);

  const q = search.trim().toLowerCase();
  const shown = q
    ? rows.filter(r => `${r.code} ${r.description}`.toLowerCase().includes(q))
    : rows;

  return (
    <div style={{ maxWidth: 1100, margin: "0 auto", padding: "1.5rem" }}>
      <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 16, flexWrap: "wrap", marginBottom: 16 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: "1.25rem" }}>GST Master Lists</h2>
          <div style={{ fontSize: "0.8rem", color: "#64748b", marginTop: 4 }}>
            Synced from the GSTN Master Codes table at 02:00 IST daily
            {lastRun && lastRun.finished
              ? ` \u00b7 last run ${lastRun.finished} (${lastRun.duration_seconds}s, ${lastRun.status})`
              : ""}
          </div>
        </div>
        <input
          style={{ ...inp, width: 260 }}
          placeholder="Search code or description..."
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 14 }}>
        {KINDS.map(k => (
          <button
            key={k.key}
            onClick={() => setActive(k.key)}
            style={{
              ...btn,
              background: active === k.key ? "#0E5C5C" : "#fff",
              color: active === k.key ? "#fff" : "#334155",
              border: `1px solid ${active === k.key ? "#0E5C5C" : "#cbd5e1"}`,
            }}
          >
            {k.label}{counts && counts[k.key] != null ? ` (${counts[k.key]})` : ""}
          </button>
        ))}
      </div>

      {error && (
        <div style={{ ...card, borderColor: "#fecaca", background: "#fef2f2", color: "#b91c1c", marginBottom: 14 }}>
          {error}
        </div>
      )}

      <div style={card}>
        <div style={{ fontSize: "0.8rem", color: "#64748b", marginBottom: 10 }}>
          {shown.length} of {rows.length} rows
        </div>
        {loading ? (
          <div style={{ color: "#94a3b8", fontSize: "0.9rem", padding: "2rem 0", textAlign: "center" }}>Loading...</div>
        ) : (
          <div style={{ maxHeight: 520, overflow: "auto", border: "1px solid #e5e7eb", borderRadius: 8 }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.8rem" }}>
              <thead style={{ position: "sticky", top: 0, background: "#0f172a", color: "#fff", zIndex: 1 }}>
                <tr>
                  <th style={{ textAlign: "left", padding: "0.5rem 0.75rem", width: 120 }}>Code</th>
                  <th style={{ textAlign: "left", padding: "0.5rem 0.75rem" }}>Description</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((r, i) => (
                  <tr key={`${r.code}-${i}`} style={{ background: i % 2 ? "#f8fafc" : "#fff", borderBottom: "1px solid #eef2f7" }}>
                    <td style={{ padding: "0.4rem 0.75rem", fontFamily: "Consolas, monospace", whiteSpace: "nowrap" }}>{r.code}</td>
                    <td style={{ padding: "0.4rem 0.75rem" }}>{r.description}</td>
                  </tr>
                ))}
                {!shown.length && (
                  <tr>
                    <td colSpan={2} style={{ padding: "1.5rem", textAlign: "center", color: "#94a3b8" }}>
                      No rows match
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default AdminGstMasters;