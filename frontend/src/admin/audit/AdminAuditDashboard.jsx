import React, { useState, useEffect, useCallback } from "react";
import api from "../../api/axios";
import Pagination from "../../components/Pagination";

const EMPTY_FILTERS = { action: "", email: "", from: "", to: "" };

/** Friendlier names for the most common entries; anything else is shown as stored. */
const ACTION_LABELS = {
  LOGIN_SUCCESS: "Signed in",
  LOGIN_FAILED: "Failed sign-in",
  LOGOUT: "Signed out",
  PASSWORD_CHANGED: "Password changed",
  PASSWORD_RESET_REQUESTED: "Reset code requested",
  PASSWORD_RESET_LINK_REQUESTED: "Reset link requested",
  PASSWORD_RESET_COMPLETED: "Password reset",
  TWO_FA_ENABLED: "Two-step sign-in turned on",
  TWO_FA_DISABLED: "Two-step sign-in turned off",
};

export const actionLabel = (action) => ACTION_LABELS[action] || action || "-";

export const isWarning = (action) => /FAILED|SUSPEND|REVOKE|BLOCK|DISABLED/i.test(action || "");

/** Only the filters that are filled in, in the shape the server expects. */
export const toQueryParams = (filters, page) => {
  const params = { page: page - 1, size: 50 };
  Object.entries(filters).forEach(([key, value]) => {
    if (value && String(value).trim()) params[key] = String(value).trim();
  });
  return params;
};

const formatDetails = (details) => {
  if (details == null || details === "") return "";
  return typeof details === "string" ? details : JSON.stringify(details);
};

const AdminAuditDashboard = () => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [actions, setActions] = useState([]);
  const [draft, setDraft] = useState(EMPTY_FILTERS);
  const [filters, setFilters] = useState(EMPTY_FILTERS);

  useEffect(() => {
    api.get("/api/admin/audit-logs/actions")
      .then((res) => setActions(Array.isArray(res.data) ? res.data : []))
      .catch(() => setActions([]));
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const res = await api.get("/api/admin/audit-logs", { params: toQueryParams(filters, page) });
      setLogs(res.data.content || []);
      setTotalPages(res.data.totalPages || 1);
      setTotalElements(res.data.totalElements || 0);
    } catch (err) {
      setLogs([]);
      setError(err.response?.data?.error || "Could not load the audit log.");
    }
    setLoading(false);
  }, [filters, page]);

  useEffect(() => { load(); }, [load]);

  const apply = (e) => {
    e.preventDefault();
    setPage(1);
    setFilters(draft);
  };

  const clear = () => {
    setDraft(EMPTY_FILTERS);
    setFilters(EMPTY_FILTERS);
    setPage(1);
  };

  const showFailedSignIns = () => {
    const next = { ...EMPTY_FILTERS, action: "LOGIN_FAILED" };
    setDraft(next);
    setFilters(next);
    setPage(1);
  };

  const hasFilters = Object.values(filters).some(Boolean);

  return (
    <div>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 600, marginBottom: "0.5rem" }}>Audit Logs</h1>
      <p style={{ color: "#64748b", fontSize: "0.875rem", marginTop: 0, marginBottom: "1rem" }}>
        Sign-ins, password changes and account actions, newest first.
      </p>

      <form onSubmit={apply} style={filterBar} aria-label="Filter the audit log">
        <label style={fieldLabel}>
          Action
          <select value={draft.action} onChange={(e) => setDraft({ ...draft, action: e.target.value })} style={input}>
            <option value="">All actions</option>
            {actions.map((a) => <option key={a} value={a}>{actionLabel(a)}</option>)}
          </select>
        </label>
        <label style={fieldLabel}>
          Email contains
          <input type="search" value={draft.email} onChange={(e) => setDraft({ ...draft, email: e.target.value })} placeholder="name@example.com" style={input} />
        </label>
        <label style={fieldLabel}>
          From
          <input type="date" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} style={input} />
        </label>
        <label style={fieldLabel}>
          To
          <input type="date" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} style={input} />
        </label>
        <div style={{ display: "flex", gap: "0.5rem", alignItems: "flex-end", flexWrap: "wrap" }}>
          <button type="submit" style={primaryBtn}>Apply</button>
          <button type="button" onClick={showFailedSignIns} style={secondaryBtn}>Failed sign-ins</button>
          {hasFilters && <button type="button" onClick={clear} style={secondaryBtn}>Clear</button>}
        </div>
      </form>

      {error && <div role="alert" style={{ color: "#b91c1c", marginBottom: "1rem" }}>{error}</div>}

      {loading ? (
        <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>
      ) : (
        <>
          <div style={{ fontSize: "0.8rem", color: "#64748b", marginBottom: "0.5rem" }}>
            {totalElements} {totalElements === 1 ? "entry" : "entries"}
          </div>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem" }}>
              <thead>
                <tr style={{ background: "#f8fafc" }}>
                  <th style={thStyle}>Time</th>
                  <th style={thStyle}>User</th>
                  <th style={thStyle}>Action</th>
                  <th style={thStyle}>Address</th>
                  <th style={thStyle}>Details</th>
                </tr>
              </thead>
              <tbody>
                {logs.length === 0 && (
                  <tr><td colSpan={5} style={{ ...tdStyle, textAlign: "center", color: "#64748b", padding: "2rem" }}>No entries match these filters.</td></tr>
                )}
                {logs.map((l, i) => {
                  const warn = isWarning(l.action);
                  return (
                    <tr key={l.id || i} style={warn ? { background: "#fef2f2" } : undefined}>
                      <td style={{ ...tdStyle, whiteSpace: "nowrap" }}>{l.timestamp ? new Date(l.timestamp).toLocaleString() : "-"}</td>
                      <td style={tdStyle}>{l.userEmail || (l.userId ? `User #${l.userId}` : "-")}</td>
                      <td style={{ ...tdStyle, color: warn ? "#b91c1c" : undefined, fontWeight: warn ? 600 : undefined }} title={l.action}>{actionLabel(l.action)}</td>
                      <td style={{ ...tdStyle, fontFamily: "monospace", fontSize: "0.8rem" }}>{l.ipAddress || "-"}</td>
                      <td style={{ ...tdStyle, color: "#475569", maxWidth: "28rem", overflowWrap: "anywhere" }}>{formatDetails(l.details)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Pagination page={page} totalPages={totalPages} onPage={setPage} />
        </>
      )}
    </div>
  );
};

const filterBar = { display: "flex", flexWrap: "wrap", gap: "0.75rem", alignItems: "flex-end", marginBottom: "1rem", padding: "0.75rem", background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "8px" };
const fieldLabel = { display: "flex", flexDirection: "column", gap: "0.25rem", fontSize: "0.75rem", fontWeight: 600, color: "#475569" };
const input = { padding: "0.4rem 0.5rem", border: "1px solid #cbd5e1", borderRadius: "6px", fontSize: "0.85rem", minWidth: "10rem" };
const primaryBtn = { padding: "0.45rem 0.9rem", background: "var(--color-primary, #00788C)", color: "#fff", border: "none", borderRadius: "6px", cursor: "pointer", fontSize: "0.85rem" };
const secondaryBtn = { padding: "0.45rem 0.9rem", background: "#fff", color: "#334155", border: "1px solid #cbd5e1", borderRadius: "6px", cursor: "pointer", fontSize: "0.85rem" };
const thStyle = { padding: "0.75rem", borderBottom: "1px solid #e2e8f0", textAlign: "left", fontWeight: 600 };
const tdStyle = { padding: "0.5rem 0.75rem", borderBottom: "1px solid #e2e8f0", verticalAlign: "top" };
export default AdminAuditDashboard;
