import React, { useState, useEffect, useCallback } from "react";
import api from "../../api/axios";
import Pagination from "../../components/Pagination";

const STATUS_FLOW = ["PLACED", "CONFIRMED", "PACKED", "SHIPPED", "DELIVERED"];
const CANCELLED = "CANCELLED";

const AdminOrders = () => {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [statusFilter, setStatusFilter] = useState("");
  const [assignModal, setAssignModal] = useState(null);
  const [courier, setCourier] = useState("");
  const [tracking, setTracking] = useState("");

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/admin/orders", { params: { page: page - 1, status: statusFilter } });
      const data = res.data.content || res.data.orders || res.data || [];
      setOrders(Array.isArray(data) ? data : []);
      setTotalPages(res.data.totalPages || 1);
    } catch (err) { console.error(err); }
    setLoading(false);
  }, [page, statusFilter]);

  useEffect(() => { fetch(); }, [fetch]);

  const updateStatus = async (orderId, status) => {
    try {
      await api.put(`/api/admin/orders/${orderId}/status`, { status });
      fetch();
    } catch (err) { alert("Failed to update status"); }
  };

  const downloadLabel = async (orderId) => {
    try {
      const res = await api.get(`/api/admin/orders/${orderId}/shipping-label/pdf`, { responseType: "blob" });
      const url = window.URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }));
      const a = document.createElement("a");
      a.href = url;
      a.download = `shipping-label-${orderId}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (err) { alert("Failed to download shipping label"); }
  };

  const assignCourier = async (orderId) => {
    if (!courier.trim()) return alert("Enter courier name");
    try {
      await api.put(`/api/admin/orders/${orderId}/assign-courier`, { courier: courier.trim(), trackingNumber: tracking.trim() });
      setAssignModal(null); setCourier(""); setTracking("");
      fetch();
    } catch (err) { alert("Failed to assign courier"); }
  };

  const getNextStatus = (current) => {
    const idx = STATUS_FLOW.indexOf(current);
    if (idx >= 0 && idx < STATUS_FLOW.length - 1) return STATUS_FLOW[idx + 1];
    return null;
  };

  const thStyle = { padding: "0.6rem 0.75rem", borderBottom: "1px solid #e2e8f0", textAlign: "left", fontWeight: 600, fontSize: "0.8rem", color: "#475569", whiteSpace: "nowrap" };
  const tdStyle = { padding: "0.5rem 0.75rem", borderBottom: "1px solid #e2e8f0", fontSize: "0.85rem" };

  const statusBadge = (status) => {
    const colors = {
      PLACED: { bg: "#fef3c7", color: "#92400e" },
      CONFIRMED: { bg: "#e0f2fe", color: "#075985" },
      PACKED: { bg: "#ede9fe", color: "#5b21b6" },
      SHIPPED: { bg: "#dbeafe", color: "#1e40af" },
      DELIVERED: { bg: "#dcfce7", color: "#166534" },
      CANCELLED: { bg: "#fee2e2", color: "#991b1b" },
    };
    const c = colors[status] || { bg: "#f1f5f9", color: "#475569" };
    return <span style={{ padding: "0.2rem 0.5rem", borderRadius: 999, fontSize: "0.7rem", fontWeight: 600, background: c.bg, color: c.color }}>{status}</span>;
  };

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  return (
    <div>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 600, marginBottom: "1.5rem" }}>Orders</h1>
      <div style={{ marginBottom: "1rem", display: "flex", gap: "0.75rem", flexWrap: "wrap", alignItems: "center" }}>
        <select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(1); }} style={{ padding: "0.4rem 0.75rem", border: "1px solid #e2e8f0", borderRadius: 6, fontSize: "0.85rem" }}>
          <option value="">All</option>
          {[...STATUS_FLOW, CANCELLED].map(s => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>

      <div style={{ overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem" }}>
          <thead>
            <tr style={{ background: "#f8fafc" }}>
              <th style={thStyle}>Order #</th>
              <th style={thStyle}>Customer</th>
              <th style={thStyle}>Amount</th>
              <th style={thStyle}>Payment</th>
              <th style={thStyle}>Status</th>
              <th style={thStyle}>Courier</th>
              <th style={thStyle}>Tracking</th>
              <th style={thStyle}>Date</th>
              <th style={thStyle}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {orders.map((o) => {
              const oid = o.id || o._id;
              const next = getNextStatus(o.status);
              return (
                <tr key={oid}>
                  <td style={tdStyle}>{o.orderId || oid?.toString().slice(-8)}</td>
                  <td style={tdStyle}>{o.user?.name || o.shippingAddress?.name || "N/A"}</td>
                  <td style={tdStyle}>&#8377;{(o.totalAmount || o.total || 0).toFixed(2)}</td>
                  <td style={tdStyle}>
                    <span style={{ fontSize: "0.8rem" }}>{o.paymentMethod || "COD"}</span>
                    {o.paymentStatus && (
                      <span style={{
                        display: "inline-block", marginLeft: "4px", fontSize: "0.65rem", padding: "1px 5px",
                        borderRadius: 999, background: o.paymentStatus === "PAID" || o.paymentStatus === "COMPLETED" ? "#dcfce7" : "#fef3c7",
                        color: o.paymentStatus === "PAID" || o.paymentStatus === "COMPLETED" ? "#166534" : "#92400e", fontWeight: 600
                      }}>{o.paymentStatus}</span>
                    )}
                  </td>
                  <td style={tdStyle}>{statusBadge(o.status)}</td>
                  <td style={{ ...tdStyle, fontSize: "0.8rem" }}>{o.courier || "-"}</td>
                  <td style={{ ...tdStyle, fontSize: "0.8rem" }}>{o.trackingNumber || "-"}</td>
                  <td style={tdStyle}>{new Date(o.createdAt).toLocaleDateString()}</td>
                  <td style={tdStyle}>
                    <div style={{ display: "flex", gap: "0.35rem", flexWrap: "wrap", alignItems: "center" }}>
                      {next && o.status !== CANCELLED && (
                        <button onClick={() => updateStatus(oid, next)} style={{
                          padding: "0.25rem 0.5rem", border: "none", borderRadius: 4, cursor: "pointer",
                          background: "#2563eb", color: "#fff", fontSize: "0.7rem", fontWeight: 500, whiteSpace: "nowrap"
                        }}>
                          {next}
                        </button>
                      )}
                      {(o.status === "PACKED" || o.status === "SHIPPED") && (!o.courier || !o.trackingNumber) && (
                        <button onClick={() => setAssignModal(oid)} style={{
                          padding: "0.25rem 0.5rem", border: "1px solid #d1d5db", borderRadius: 4, cursor: "pointer",
                          background: "#fff", fontSize: "0.7rem", fontWeight: 500, whiteSpace: "nowrap"
                        }}>
                          Assign Courier
                        </button>
                      )}
                      <select value={o.status} onChange={(e) => updateStatus(oid, e.target.value)} style={{ padding: "0.2rem", fontSize: "0.7rem", borderRadius: 4 }}>
                        {[...STATUS_FLOW, CANCELLED].map(s => <option key={s} value={s}>{s}</option>)}
                      </select>
                      <button onClick={() => downloadLabel(oid)} style={{
                        padding: "0.25rem 0.5rem", border: "1px solid #0E5C5C", borderRadius: 4, cursor: "pointer",
                        background: "#fff", color: "#0E5C5C", fontSize: "0.7rem", fontWeight: 500, whiteSpace: "nowrap"
                      }}>
                        Label
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
            {orders.length === 0 && (
              <tr><td colSpan={9} style={{ padding: "2rem", textAlign: "center", color: "#94a3b8" }}>No orders found</td></tr>
            )}
          </tbody>
        </table>
      </div>

      <Pagination page={page} totalPages={totalPages} onPage={setPage} />

      {assignModal && (
        <div style={{
          position: "fixed", inset: 0, background: "rgba(0,0,0,0.4)", display: "flex",
          alignItems: "center", justifyContent: "center", zIndex: 1000
        }} onClick={() => { setAssignModal(null); setCourier(""); setTracking(""); }}>
          <div style={{
            background: "#fff", borderRadius: 12, padding: "1.5rem", width: 380, maxWidth: "90vw"
          }} onClick={(e) => e.stopPropagation()}>
            <h3 style={{ margin: "0 0 1rem", fontWeight: 600 }}>Assign Courier</h3>
            <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem", marginBottom: "1rem" }}>
              <div>
                <label style={{ display: "block", fontSize: "0.8rem", fontWeight: 500, marginBottom: "4px" }}>Courier Name</label>
                <input value={courier} onChange={(e) => setCourier(e.target.value)} placeholder="e.g. Delhivery, Blue Dart"
                  style={{ width: "100%", padding: "0.5rem 0.75rem", border: "1px solid #d1d5db", borderRadius: 6, fontSize: "0.9rem", boxSizing: "border-box" }} />
              </div>
              <div>
                <label style={{ display: "block", fontSize: "0.8rem", fontWeight: 500, marginBottom: "4px" }}>Tracking Number</label>
                <input value={tracking} onChange={(e) => setTracking(e.target.value)} placeholder="Enter tracking number"
                  style={{ width: "100%", padding: "0.5rem 0.75rem", border: "1px solid #d1d5db", borderRadius: 6, fontSize: "0.9rem", boxSizing: "border-box" }} />
              </div>
            </div>
            <div style={{ display: "flex", gap: "0.75rem", justifyContent: "flex-end" }}>
              <button onClick={() => { setAssignModal(null); setCourier(""); setTracking(""); }} style={{
                padding: "0.5rem 1rem", border: "1px solid #d1d5db", borderRadius: 6, background: "#fff", cursor: "pointer", fontSize: "0.85rem"
              }}>Cancel</button>
              <button onClick={() => assignCourier(assignModal)} style={{
                padding: "0.5rem 1rem", border: "none", borderRadius: 6, background: "#2563eb", color: "#fff", cursor: "pointer", fontSize: "0.85rem", fontWeight: 500
              }}>Assign</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminOrders;
