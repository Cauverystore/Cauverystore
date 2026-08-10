import React, { useState, useEffect, useCallback, useMemo } from "react";
import { PackageX, Download } from "lucide-react";
import api from "../../utils/axios";

/**
 * Returns Management.
 *
 * <h2>What was wrong with the previous version</h2>
 *
 * It read r.orderId, r.productId and r.userId - MongoDB-shaped names this API has never emitted.
 * The Java entity serialises the relations as order, product and user, so those three columns
 * rendered "-" on every row and the screen could not tell you whose return it was or what was in
 * it. Read defensively now, because both shapes may still be in flight somewhere.
 *
 * Its actions were also a stage short. Refund was offered directly from Received, which skips the
 * quality check - and skipping it is what used to let a credit note be issued for goods nobody
 * had looked at. Received now offers the check itself: pass puts the goods back on the shelf and
 * issues the credit note, fail writes them off and issues nothing.
 */

const STATUS_TABS = ["All", "REQUESTED", "APPROVED", "IN_TRANSIT", "RECEIVED", "COMPLETED", "REFUNDED", "REJECTED"];

const STATUS_LABEL = {
  REQUESTED: "Requested",
  APPROVED: "Approved",
  IN_TRANSIT: "Picked",
  RECEIVED: "Received",
  COMPLETED: "QC Passed",
  REFUNDED: "Refunded",
  REFUND_ISSUED: "Refunded",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
};

const STATUS_COLOR = {
  REQUESTED: { bg: "#FEF3C7", color: "#92400E" },
  APPROVED: { bg: "#DBEAFE", color: "#1E40AF" },
  IN_TRANSIT: { bg: "#E0E7FF", color: "#3730A3" },
  RECEIVED: { bg: "#EDE9FE", color: "#5B21B6" },
  COMPLETED: { bg: "#EAF7EE", color: "#146C43" },
  REFUNDED: { bg: "#EAF7EE", color: "#146C43" },
  REFUND_ISSUED: { bg: "#EAF7EE", color: "#146C43" },
  REJECTED: { bg: "#FEE2E2", color: "#B91C1C" },
  CANCELLED: { bg: "#E5E7EB", color: "#374151" },
};

/** Statuses stored under older names, shown as the stage they actually are. */
const ALIASES = {
  PENDING: "REQUESTED",
  UNDER_REVIEW: "RECEIVED",
  PICKED: "IN_TRANSIT",
  PICKED_UP: "IN_TRANSIT",
  REFUND_INITIATED: "REFUNDED",
};
const canonical = (s) => (s ? ALIASES[s.toUpperCase()] || s.toUpperCase() : "REQUESTED");

const AdminReturns = () => {
  const [returns, setReturns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [statusTab, setStatusTab] = useState("All");
  const [reasonFilter, setReasonFilter] = useState("All");
  const [acting, setActing] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const { data } = await api.get("/api/admin/returns");
      setReturns(Array.isArray(data) ? data : data?.content || []);
    } catch (err) {
      setError(err.response?.data?.error || "Failed to load returns");
      setReturns([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  // Both shapes. The relation names are what this API sends; the *Id names are what the previous
  // version expected, kept so a stale payload from anywhere still renders something useful.
  const orderOf = (r) => r.order || r.orderId || null;
  const productOf = (r) => r.product || r.productId || null;
  const userOf = (r) => r.user || r.userId || null;
  const nameOf = (o) => (o && typeof o === "object" ? o.name || o.fullName || o.email : null);

  const move = async (id, status, confirmText) => {
    if (confirmText && !window.confirm(confirmText)) return;
    setActing(id);
    setMessage("");
    setError("");
    try {
      await api.put(`/api/admin/returns/${id}/status`, { status });
      setMessage(`Return RET-${id} moved to ${STATUS_LABEL[status] || status}.`);
      await load();
    } catch (err) {
      // The refusal explains itself in terms of the parcel, so it is shown as written.
      setError(err.response?.data?.error || "Failed to update this return");
    } finally {
      setActing(null);
    }
  };

  /**
   * What can be done from here, following the lifecycle the backend enforces.
   *
   * Offering anything else produces a refusal the admin cannot act on, so the buttons and the
   * rules are kept deliberately identical.
   */
  const actionsFor = (r) => {
    const id = r.id || r._id;
    switch (canonical(r.status)) {
      case "REQUESTED":
        return [
          { label: "Approve", color: "#2563EB", run: () => move(id, "APPROVED") },
          { label: "Reject", color: "#dc2626", run: () => move(id, "REJECTED") },
        ];
      case "APPROVED":
        return [
          { label: "Mark Picked", color: "#4F46E5", run: () => move(id, "IN_TRANSIT") },
          { label: "Mark Received", color: "#7C3AED", run: () => move(id, "RECEIVED") },
          { label: "Reject", color: "#dc2626", run: () => move(id, "REJECTED") },
        ];
      case "IN_TRANSIT":
        return [{ label: "Mark Received", color: "#7C3AED", run: () => move(id, "RECEIVED") }];
      case "RECEIVED":
        // The quality check. Passing is what restocks the goods and issues the credit note, so
        // both buttons say what they will actually cause.
        return [
          {
            label: "QC Pass — restock & credit",
            color: "#2E9B57",
            run: () => move(id, "COMPLETED",
              "Pass this return?\n\nThe goods go back into sellable stock and a credit note is issued against the original invoice."),
          },
          {
            label: "QC Fail — write off",
            color: "#dc2626",
            run: () => move(id, "REJECTED",
              "Fail this return?\n\nThe goods are marked unsellable and are NOT restocked. No credit note is issued and no refund follows."),
          },
        ];
      case "COMPLETED":
        return [{
          label: "Mark Refunded", color: "#146C43",
          run: () => move(id, "REFUNDED", "Confirm the refund has been paid to the customer?"),
        }];
      default:
        return [];
    }
  };

  const reasons = useMemo(() => {
    const set = new Set(returns.map((r) => r.reason).filter(Boolean));
    return ["All", ...Array.from(set)];
  }, [returns]);

  const visible = useMemo(() => returns.filter((r) => {
    if (statusTab !== "All" && canonical(r.status) !== statusTab) return false;
    if (reasonFilter !== "All" && r.reason !== reasonFilter) return false;
    return true;
  }), [returns, statusTab, reasonFilter]);

  /**
   * Pass and fail rates, counted only over returns that reached the check.
   *
   * Including the ones still in transit would dilute both figures with returns nobody has
   * inspected yet, and a quality figure that moves when a parcel is posted is not a quality
   * figure.
   */
  const qc = useMemo(() => {
    const passed = returns.filter((r) => r.qualityCheckStatus === "PASSED").length;
    const failed = returns.filter((r) => r.qualityCheckStatus === "FAILED").length;
    const decided = passed + failed;
    return { passed, failed, decided, passRate: decided ? Math.round((passed / decided) * 100) : null };
  }, [returns]);

  /** Exported from what is on screen, so a filtered view exports the same rows it shows. */
  const exportCsv = () => {
    const headers = ["Return ID", "Order", "Product", "Customer", "Qty", "Reason", "Status", "QC", "Condition", "Refund Amount", "Requested"];
    const rows = visible.map((r) => {
      const id = r.id || r._id;
      return [
        `RET-${id}`,
        orderOf(r)?.id ?? orderOf(r) ?? "",
        nameOf(productOf(r)) ?? "",
        nameOf(userOf(r)) ?? "",
        r.quantity ?? "",
        r.reason ?? "",
        canonical(r.status),
        r.qualityCheckStatus ?? "",
        r.condition ?? "",
        r.refundAmount ?? "",
        r.createdAt ? new Date(r.createdAt).toLocaleDateString("en-IN") : "",
      ];
    });
    const csv = [headers, ...rows]
      // Quotes doubled and every field wrapped, so a reason containing a comma cannot shift
      // every later column into the wrong one.
      .map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(","))
      .join("\n");
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8;" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = `returns-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const badge = (status) => {
    const c = STATUS_COLOR[canonical(status)] || { bg: "#E5E7EB", color: "#374151" };
    return {
      background: c.bg, color: c.color, padding: "3px 10px", borderRadius: "12px",
      fontSize: "0.75rem", fontWeight: 600, whiteSpace: "nowrap",
    };
  };

  if (loading) {
    return (
      <div>
        <h1 style={{ fontSize: "1.5rem", fontWeight: 700, marginBottom: "1.5rem" }}>Returns</h1>
        <div className="admin-skeleton-row">
          {[1, 2, 3, 4].map((i) => <div key={i} className="admin-skeleton-card" style={{ height: "48px" }} />)}
        </div>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem", flexWrap: "wrap", gap: "0.75rem" }}>
        <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: 0 }}>Returns</h1>
        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
          {qc.decided > 0 && (
            <span style={{ fontSize: "0.8rem", color: "#6b7280" }}>
              QC pass rate <strong>{qc.passRate}%</strong> ({qc.passed} passed / {qc.failed} failed)
            </span>
          )}
          <button className="admin-btn admin-btn-sm admin-btn-outline" onClick={exportCsv} disabled={visible.length === 0}>
            <Download size={14} style={{ verticalAlign: "middle" }} /> Export CSV
          </button>
        </div>
      </div>

      {message && <div className="admin-alert success" style={{ marginBottom: "0.75rem" }}>{message}</div>}
      {error && <div className="admin-alert error" style={{ marginBottom: "0.75rem" }}>{error}</div>}

      <div style={{ display: "flex", gap: "4px", marginBottom: "0.75rem", borderBottom: "2px solid #EAF7EE", overflowX: "auto" }}>
        {STATUS_TABS.map((s) => (
          <button
            key={s}
            onClick={() => setStatusTab(s)}
            style={{
              padding: "8px 14px", border: "none", cursor: "pointer", fontWeight: 600,
              fontSize: "0.82rem", whiteSpace: "nowrap",
              background: statusTab === s ? "#EAF7EE" : "transparent",
              color: statusTab === s ? "#146C43" : "#64748B",
              borderBottom: statusTab === s ? "2px solid #2E9B57" : "2px solid transparent",
            }}
          >
            {s === "All" ? "All" : STATUS_LABEL[s] || s}
          </button>
        ))}
      </div>

      <div className="admin-table-wrapper">
        <div className="admin-filter-bar">
          <select className="admin-filter-select" value={reasonFilter} onChange={(e) => setReasonFilter(e.target.value)}>
            {reasons.map((r) => <option key={r} value={r}>{r === "All" ? "All reasons" : r}</option>)}
          </select>
          <span style={{ fontSize: "0.8rem", color: "#6b7280", marginLeft: "auto" }}>
            {visible.length} of {returns.length}
          </span>
        </div>

        {visible.length === 0 ? (
          <div className="admin-empty-state">
            <div className="admin-empty-state-icon"><PackageX size={32} /></div>
            <div className="admin-empty-state-text">No returns match this view</div>
          </div>
        ) : (
          <table className="admin-table">
            <thead>
              <tr>
                <th>Return ID</th><th>Order</th><th>Product</th><th>Customer</th>
                <th>Qty</th><th>Reason</th><th>Status</th><th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((r) => {
                const id = r.id || r._id;
                const order = orderOf(r);
                const acts = actionsFor(r);
                return (
                  <tr key={id}>
                    <td style={{ fontFamily: "monospace", fontSize: "0.8rem" }}>RET-{id}</td>
                    <td style={{ fontSize: "0.82rem" }}>
                      {order?.orderNumber || (order?.id ?? (typeof order === "number" ? order : "-"))}
                    </td>
                    <td>{nameOf(productOf(r)) || <span style={{ color: "#9ca3af" }}>Whole order</span>}</td>
                    <td style={{ fontSize: "0.82rem" }}>{nameOf(userOf(r)) || "-"}</td>
                    <td>{r.quantity ?? "-"}</td>
                    <td style={{ fontSize: "0.82rem", maxWidth: "200px" }}>{r.reason || "-"}</td>
                    <td>
                      <span style={badge(r.status)}>{STATUS_LABEL[canonical(r.status)] || r.status}</span>
                      {r.qualityCheckStatus && (
                        <div style={{ fontSize: "0.7rem", color: "#6b7280", marginTop: "3px" }}>
                          QC {r.qualityCheckStatus.toLowerCase()}
                          {r.condition ? ` · ${r.condition.toLowerCase()}` : ""}
                        </div>
                      )}
                    </td>
                    <td>
                      <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
                        {acts.map((a) => (
                          <button
                            key={a.label}
                            disabled={acting === id}
                            onClick={a.run}
                            style={{
                              padding: "5px 10px", background: "#fff", color: a.color,
                              border: `1px solid ${a.color}`, borderRadius: "4px",
                              cursor: acting === id ? "not-allowed" : "pointer",
                              fontSize: "0.75rem", fontWeight: 600, opacity: acting === id ? 0.5 : 1,
                            }}
                          >
                            {a.label}
                          </button>
                        ))}
                        {acts.length === 0 && <span style={{ color: "#9ca3af", fontSize: "0.78rem" }}>—</span>}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};

export default AdminReturns;
