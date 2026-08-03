import React, { useState, useEffect } from "react";
import api from "../api/axios";

const statusColors = {
  PLACED: "#3b82f6",
  PROCESSING: "#f59e0b",
  SHIPPED: "#6366f1",
  DELIVERED: "#22c55e",
  CANCELLED: "#ef4444",
  RETURNED: "#a855f7",
  REFUNDED: "#f97316",
  APPROVED: "#22c55e",
  REJECTED: "#ef4444",
  PENDING: "#eab308",
};

const SellerOrders = () => {
  const [tab, setTab] = useState("orders");
  const [orders, setOrders] = useState([]);
  const [returns, setReturns] = useState([]);
  const [statusFilter, setStatusFilter] = useState("All");
  const [expandedOrder, setExpandedOrder] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    if (tab === "orders") {
      api.get("/api/seller/orders").then(r => setOrders(Array.isArray(r.data) ? r.data : [])).catch(() => {}).finally(() => setLoading(false));
    } else {
      api.get("/api/seller/returns").then(r => setReturns(Array.isArray(r.data) ? r.data : [])).catch(() => {}).finally(() => setLoading(false));
    }
  }, [tab]);

  const handleStatusUpdate = async (orderId, newStatus) => {
    try {
      await api.put(`/api/seller/orders/${orderId}/status`, { status: newStatus });
      setOrders(prev => prev.map(o => o.id === orderId ? { ...o, status: newStatus } : o));
    } catch { alert("Failed to update status"); }
  };

  const UNLABELABLE_STATUSES = ["CANCELLED", "REFUNDED", "RETURNED"];

  const fetchLabelBlobUrl = async (orderId) => {
    const res = await api.get(`/api/seller/orders/${orderId}/shipping-label/pdf`, { responseType: "blob" });
    return window.URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }));
  };

  const downloadLabel = async (orderId) => {
    try {
      const url = await fetchLabelBlobUrl(orderId);
      const a = document.createElement("a");
      a.href = url;
      a.download = `shipping-label-${orderId}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch { alert("Failed to download shipping label"); }
  };

  const printLabel = async (orderId) => {
    try {
      const url = await fetchLabelBlobUrl(orderId);
      const printWindow = window.open(url, "_blank");
      if (printWindow) {
        printWindow.addEventListener("load", () => printWindow.print());
      }
    } catch { alert("Failed to print shipping label"); }
  };

  const assignCourier = async (order) => {
    const courier = prompt("Courier name:", order.courier || "");
    if (courier === null) return;
    if (!courier.trim()) { alert("Courier name is required"); return; }
    const trackingNumber = prompt("Tracking / AWB number (optional):", order.trackingNumber || "");
    if (trackingNumber === null) return;
    try {
      await api.put(`/api/seller/orders/${order.id}/courier`, { courier, trackingNumber });
      setOrders(prev => prev.map(o => o.id === order.id ? { ...o, courier, trackingNumber } : o));
    } catch (e) { alert(e.response?.data?.error || "Failed to assign courier"); }
  };

  const handleReturnAction = async (returnId, status) => {
    const note = prompt("Admin note (optional):") || "";
    try {
      await api.put(`/api/seller/returns/${returnId}/status`, { status, adminNote: note });
      setReturns(prev => prev.map(r => r.id === returnId ? { ...r, status } : r));
    } catch { alert("Failed to update return"); }
  };

  const getNextStatus = (status) => {
    const map = { PLACED: "PROCESSING", PROCESSING: "SHIPPED", SHIPPED: "DELIVERED" };
    return map[status] || null;
  };

  const filteredOrders = statusFilter === "All" ? orders : orders.filter(o => o.status === statusFilter);

  const formatDate = (d) => {
    if (!d) return "N/A";
    return new Date(d).toLocaleDateString("en-IN", { year: "numeric", month: "short", day: "numeric" });
  };

  const statusTabs = ["All", "PLACED", "SHIPPED", "DELIVERED", "RETURNED", "REFUNDED"];

  return (
    <div style={{ padding: "2rem" }}>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 700, marginBottom: "1rem" }}>Orders & Returns</h1>

      <div style={{ display: "flex", gap: "1rem", marginBottom: "1.5rem", borderBottom: "2px solid #e5e7eb", paddingBottom: "0.5rem", flexWrap: "wrap" }}>
        {["orders", "returns"].map(t => (
          <button key={t} onClick={() => { setTab(t); setStatusFilter("All"); setExpandedOrder(null); }}
            style={{ background: "none", border: "none", padding: "0.5rem 1rem", cursor: "pointer", fontWeight: tab === t ? 700 : 400, color: tab === t ? "#3b82f6" : "#6b7280", borderBottom: tab === t ? "2px solid #3b82f6" : "2px solid transparent", marginBottom: "-0.5rem", fontSize: "1rem" }}>
            {t === "orders" ? "Orders" : "Returns"}
          </button>
        ))}
      </div>

      {loading && <div style={{ textAlign: "center", padding: "2rem", color: "#6b7280" }}>Loading...</div>}

      {!loading && tab === "orders" && (
        <>
          <div style={{ display: "flex", gap: "0.5rem", marginBottom: "1rem", flexWrap: "wrap" }}>
            {statusTabs.map(s => (
              <button key={s} onClick={() => setStatusFilter(s)}
                style={{ padding: "0.25rem 0.75rem", borderRadius: "9999px", border: "1px solid #d1d5db", background: statusFilter === s ? "#3b82f6" : "#fff", color: statusFilter === s ? "#fff" : "#374151", cursor: "pointer", fontSize: "0.875rem" }}>
                {s === "All" ? "All" : s.charAt(0) + s.slice(1).toLowerCase()}
              </button>
            ))}
          </div>

          {filteredOrders.length === 0 ? (
            <div style={{ textAlign: "center", padding: "3rem", color: "#6b7280" }}>No orders found.</div>
          ) : (
            <div style={{ overflowX: "auto", borderRadius: "8px", boxShadow: "0 1px 3px rgba(0,0,0,0.1)" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", background: "#fff" }}>
              <thead>
                <tr style={{ background: "#f9fafb", borderBottom: "2px solid #e5e7eb" }}>
                  {["#", "Customer", "Total", "Status", "Date", "Payment", "Actions"].map(h => (
                    <th key={h} style={{ padding: "0.75rem 1rem", textAlign: "left", fontSize: "0.875rem", fontWeight: 600, color: "#6b7280" }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filteredOrders.map(o => (
                  <React.Fragment key={o.id}>
                    <tr onClick={() => setExpandedOrder(expandedOrder === o.id ? null : o.id)} style={{ borderBottom: "1px solid #e5e7eb", cursor: "pointer", background: expandedOrder === o.id ? "#f0f9ff" : "#fff" }}>
                      <td style={{ padding: "0.75rem 1rem", fontWeight: 500 }}>#{o.id}</td>
                      <td style={{ padding: "0.75rem 1rem" }}>{o.user?.fullName || "N/A"}</td>
                      <td style={{ padding: "0.75rem 1rem" }}>₹{o.totalAmount?.toLocaleString("en-IN") || "0"}</td>
                      <td style={{ padding: "0.75rem 1rem" }}>
                        <span style={{ background: statusColors[o.status] || "#6b7280", color: "#fff", padding: "0.125rem 0.5rem", borderRadius: "9999px", fontSize: "0.75rem", fontWeight: 600 }}>{o.status}</span>
                      </td>
                      <td style={{ padding: "0.75rem 1rem", fontSize: "0.875rem", color: "#6b7280" }}>{formatDate(o.createdAt)}</td>
                      <td style={{ padding: "0.75rem 1rem", fontSize: "0.875rem" }}>{o.paymentMethod || "N/A"}</td>
                      <td style={{ padding: "0.75rem 1rem" }}>
                        <div style={{ display: "flex", gap: "0.4rem", alignItems: "center", flexWrap: "wrap" }}>
                          {getNextStatus(o.status) ? (
                            <select value="" onChange={e => { if (e.target.value) handleStatusUpdate(o.id, e.target.value); }}
                              style={{ padding: "0.25rem 0.5rem", fontSize: "0.75rem", borderRadius: "4px", border: "1px solid #d1d5db", cursor: "pointer" }}>
                              <option value="">Update Status</option>
                              <option value={getNextStatus(o.status)}>Mark as {getNextStatus(o.status)}</option>
                            </select>
                          ) : (
                            <span style={{ fontSize: "0.75rem", color: "#9ca3af" }}>No actions</span>
                          )}
                          {!UNLABELABLE_STATUSES.includes(o.status) && (
                            <>
                              <button onClick={(e) => { e.stopPropagation(); assignCourier(o); }} title="Assign courier / tracking number" style={{
                                padding: "0.25rem 0.5rem", border: "1px solid #d1d5db", borderRadius: 4, cursor: "pointer",
                                background: "#fff", color: "#374151", fontSize: "0.7rem", fontWeight: 500, whiteSpace: "nowrap"
                              }}>
                                {o.trackingNumber ? "Edit Courier" : "Assign Courier"}
                              </button>
                              <button onClick={() => downloadLabel(o.id)} title="Download shipping label" style={{
                                padding: "0.25rem 0.5rem", border: "1px solid #0E5C5C", borderRadius: 4, cursor: "pointer",
                                background: "#fff", color: "#0E5C5C", fontSize: "0.7rem", fontWeight: 500, whiteSpace: "nowrap"
                              }}>
                                Download Label
                              </button>
                              <button onClick={() => printLabel(o.id)} title="Print shipping label" style={{
                                padding: "0.25rem 0.5rem", border: "1px solid #0E5C5C", borderRadius: 4, cursor: "pointer",
                                background: "#0E5C5C", color: "#fff", fontSize: "0.7rem", fontWeight: 500, whiteSpace: "nowrap"
                              }}>
                                Print Label
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                    {expandedOrder === o.id && (
                      <tr style={{ background: "#f9fafb" }}>
                        <td colSpan={7} style={{ padding: "1rem" }}>
                          <div style={{ fontSize: "0.875rem", fontWeight: 600, marginBottom: "0.5rem", color: "#374151" }}>Order Items</div>
                          <table style={{ width: "100%", borderCollapse: "collapse" }}>
                            <thead>
                              <tr style={{ borderBottom: "1px solid #e5e7eb" }}>
                                <th style={{ padding: "0.5rem", textAlign: "left", fontSize: "0.75rem", color: "#6b7280" }}>Product</th>
                                <th style={{ padding: "0.5rem", textAlign: "right", fontSize: "0.75rem", color: "#6b7280" }}>Qty</th>
                                <th style={{ padding: "0.5rem", textAlign: "right", fontSize: "0.75rem", color: "#6b7280" }}>Price</th>
                              </tr>
                            </thead>
                            <tbody>
                              {o.items?.map((item, idx) => (
                                <tr key={idx} style={{ borderBottom: "1px solid #f3f4f6" }}>
                                  <td style={{ padding: "0.5rem", fontSize: "0.875rem" }}>{item.product?.name || "N/A"}</td>
                                  <td style={{ padding: "0.5rem", textAlign: "right", fontSize: "0.875rem" }}>{item.quantity}</td>
                                  <td style={{ padding: "0.5rem", textAlign: "right", fontSize: "0.875rem" }}>₹{item.price?.toLocaleString("en-IN") || "0"}</td>
                                </tr>
                              ))}
                              {(!o.items || o.items.length === 0) && (
                                <tr><td colSpan={3} style={{ padding: "0.5rem", textAlign: "center", color: "#9ca3af", fontSize: "0.875rem" }}>No items</td></tr>
                              )}
                            </tbody>
                          </table>
                          {o.address && (
                            <div style={{ marginTop: "0.75rem", fontSize: "0.875rem", color: "#6b7280" }}>
                              <strong>Address:</strong> {o.address}
                            </div>
                          )}
                          <div style={{ marginTop: "0.5rem", fontSize: "0.875rem", color: "#6b7280" }}>
                            <strong>Courier:</strong> {o.courier || "Not assigned"}
                            {o.trackingNumber && <span> &middot; <strong>Tracking:</strong> {o.trackingNumber}</span>}
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                ))}
              </tbody>
            </table>
            </div>
          )}
        </>
      )}

      {!loading && tab === "returns" && (
        <>
          {returns.length === 0 ? (
            <div style={{ textAlign: "center", padding: "3rem", color: "#6b7280" }}>No return requests.</div>
          ) : (
            <div style={{ overflowX: "auto", borderRadius: "8px", boxShadow: "0 1px 3px rgba(0,0,0,0.1)" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", background: "#fff" }}>
              <thead>
                <tr style={{ background: "#f9fafb", borderBottom: "2px solid #e5e7eb" }}>
                  {["Return ID", "Order ID", "Product", "Customer", "Reason", "Status", "Refund Amount", "Date", "Actions"].map(h => (
                    <th key={h} style={{ padding: "0.75rem 1rem", textAlign: "left", fontSize: "0.875rem", fontWeight: 600, color: "#6b7280" }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {returns.map(r => (
                  <tr key={r.id} style={{ borderBottom: "1px solid #e5e7eb" }}>
                    <td style={{ padding: "0.75rem 1rem", fontWeight: 500 }}>#{r.id}</td>
                    <td style={{ padding: "0.75rem 1rem" }}>#{r.order?.id}</td>
                    <td style={{ padding: "0.75rem 1rem" }}>{r.product?.name || "N/A"}</td>
                    <td style={{ padding: "0.75rem 1rem" }}>{r.user?.fullName || "N/A"}</td>
                    <td style={{ padding: "0.75rem 1rem", fontSize: "0.875rem" }}>{r.reason || "N/A"}</td>
                    <td style={{ padding: "0.75rem 1rem" }}>
                      <span style={{ background: statusColors[r.status] || "#6b7280", color: "#fff", padding: "0.125rem 0.5rem", borderRadius: "9999px", fontSize: "0.75rem", fontWeight: 600 }}>{r.status}</span>
                    </td>
                    <td style={{ padding: "0.75rem 1rem" }}>₹{r.refundAmount?.toLocaleString("en-IN") || "0"}</td>
                    <td style={{ padding: "0.75rem 1rem", fontSize: "0.875rem", color: "#6b7280" }}>{formatDate(r.createdAt)}</td>
                    <td style={{ padding: "0.75rem 1rem" }}>
                      {r.status === "PENDING" ? (
                        <div style={{ display: "flex", gap: "0.5rem" }}>
                          <button onClick={() => handleReturnAction(r.id, "APPROVED")}
                            style={{ padding: "0.25rem 0.75rem", borderRadius: "4px", border: "none", background: "#22c55e", color: "#fff", cursor: "pointer", fontSize: "0.75rem", fontWeight: 600 }}>Approve</button>
                          <button onClick={() => handleReturnAction(r.id, "REJECTED")}
                            style={{ padding: "0.25rem 0.75rem", borderRadius: "4px", border: "none", background: "#ef4444", color: "#fff", cursor: "pointer", fontSize: "0.75rem", fontWeight: 600 }}>Reject</button>
                        </div>
                      ) : (
                        <span style={{ fontSize: "0.75rem", color: "#9ca3af" }}>--</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default SellerOrders;
