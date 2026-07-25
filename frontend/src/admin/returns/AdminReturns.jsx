import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const statusFilters = ["All", "PENDING", "APPROVED", "REJECTED", "RECEIVED", "REFUNDED"];

const statusColors = {
  PENDING: { bg: "#FEF3C7", color: "#D97706" },
  APPROVED: { bg: "#DBEAFE", color: "#2563EB" },
  REJECTED: { bg: "#FEE2E2", color: "#dc2626" },
  RECEIVED: { bg: "#E6F7EC", color: "#2E9B57" },
  REFUNDED: { bg: "#E0E7FF", color: "#4F46E5" },
};

const styles = {
  container: { padding: "24px" },
  header: { fontSize: "28px", fontWeight: "700", color: "#0B3D2E", marginBottom: "24px" },
  filtersRow: { display: "flex", gap: "8px", marginBottom: "24px", flexWrap: "wrap" },
  filterBtn: (active) => ({
    background: active ? "#0B3D2E" : "#fff",
    color: active ? "#fff" : "#0B3D2E",
    border: "1px solid #CFE8D6",
    padding: "8px 18px",
    borderRadius: "20px",
    fontSize: "13px",
    fontWeight: "600",
    cursor: "pointer",
  }),
  table: { width: "100%", borderCollapse: "collapse", background: "#fff", border: "1px solid #CFE8D6", borderRadius: "8px", overflow: "hidden" },
  th: { textAlign: "left", padding: "12px 16px", background: "#0B3D2E", color: "#fff", fontSize: "13px", fontWeight: "600", textTransform: "uppercase", letterSpacing: "0.5px" },
  td: { padding: "12px 16px", borderBottom: "1px solid #CFE8D6", fontSize: "14px", color: "#333" },
  statusBadge: (status) => {
    const c = statusColors[status] || { bg: "#E5E7EB", color: "#374151" };
    return { background: c.bg, color: c.color, padding: "4px 10px", borderRadius: "12px", fontSize: "12px", fontWeight: "600", display: "inline-block" };
  },
  actionBtn: (color) => ({
    background: color,
    color: "#fff",
    border: "none",
    padding: "6px 12px",
    borderRadius: "4px",
    fontSize: "12px",
    fontWeight: "600",
    cursor: "pointer",
    marginRight: "4px",
    marginBottom: "4px",
  }),
  loading: { textAlign: "center", padding: "40px", color: "#666" },
  errorMsg: { background: "#FEE2E2", color: "#dc2626", padding: "12px 16px", borderRadius: "6px", marginBottom: "16px", fontSize: "14px" },
  successMsg: { background: "#E6F7EC", color: "#2E9B57", padding: "12px 16px", borderRadius: "6px", marginBottom: "16px", fontSize: "14px" },
};

export default function AdminReturns() {
  const [returns, setReturns] = useState([]);
  const [filter, setFilter] = useState("All");
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchReturns();
  }, []);

  const fetchReturns = async () => {
    try {
      setLoading(true);
      const { data } = await api.get("/api/admin/returns");
      setReturns(data.returns || data || []);
      setError(null);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to fetch returns");
    } finally {
      setLoading(false);
    }
  };

  const updateStatus = async (id, newStatus) => {
    try {
      setError(null);
      setMessage(null);
      await api.put(`/api/admin/returns/${id}/status`, { status: newStatus });
      setMessage(`Return status updated to ${newStatus}`);
      fetchReturns();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to update status");
    }
  };

  const returnList = Array.isArray(returns) ? returns : [];
  const filteredReturns = filter === "All" ? returnList : returnList.filter((r) => r.status === filter);

  const availableActions = (status) => {
    const actions = [];
    if (status === "PENDING") {
      actions.push({ label: "Approve", value: "APPROVED", color: "#2563EB" });
      actions.push({ label: "Reject", value: "REJECTED", color: "#dc2626" });
    } else if (status === "APPROVED") {
      actions.push({ label: "Mark Received", value: "RECEIVED", color: "#2E9B57" });
    } else if (status === "RECEIVED") {
      actions.push({ label: "Refund", value: "REFUNDED", color: "#4F46E5" });
    }
    return actions;
  };

  if (loading) return <div style={styles.loading}>Loading returns...</div>;

  return (
    <div style={styles.container}>
      <h1 style={styles.header}>Returns Management</h1>

      {message && <div style={styles.successMsg}>{message}</div>}
      {error && <div style={styles.errorMsg}>{error}</div>}

      <div style={styles.filtersRow}>
        {statusFilters.map((s) => (
          <button key={s} style={styles.filterBtn(filter === s)} onClick={() => setFilter(s)}>
            {s}
          </button>
        ))}
      </div>

      <table style={styles.table}>
        <thead>
          <tr>
            <th style={styles.th}>Order ID</th>
            <th style={styles.th}>Product</th>
            <th style={styles.th}>User</th>
            <th style={styles.th}>Quantity</th>
            <th style={styles.th}>Reason</th>
            <th style={styles.th}>Status</th>
            <th style={styles.th}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {filteredReturns.length === 0 ? (
            <tr><td style={styles.td} colSpan={7}>No returns found.</td></tr>
          ) : (
            filteredReturns.map((r) => {
              const id = r._id || r.id;
              return (
                <tr key={id}>
                  <td style={styles.td}>{r.orderId?.orderNumber || r.orderId?.toString().slice(-6) || "-"}</td>
                  <td style={styles.td}>{r.productId?.name || (typeof r.productId === "string" ? r.productId.slice(-6) : "-")}</td>
                  <td style={styles.td}>{r.userId?.name || r.userId?.email || (typeof r.userId === "string" ? r.userId.slice(-6) : "-")}</td>
                  <td style={styles.td}>{r.quantity || "-"}</td>
                  <td style={styles.td}>{r.reason || "-"}</td>
                  <td style={styles.td}><span style={styles.statusBadge(r.status)}>{r.status || "PENDING"}</span></td>
                  <td style={styles.td}>
                    {availableActions(r.status).map((action) => (
                      <button key={action.value} style={styles.actionBtn(action.color)} onClick={() => updateStatus(id, action.value)}>
                        {action.label}
                      </button>
                    ))}
                    {availableActions(r.status).length === 0 && <span style={{ color: "#999", fontSize: "13px" }}>-</span>}
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
}
