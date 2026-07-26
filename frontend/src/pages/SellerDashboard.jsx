import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api/axios";

const statusBadgeColors = {
  PLACED: "#3b82f6",
  PROCESSING: "#f59e0b",
  SHIPPED: "#6366f1",
  DELIVERED: "#22c55e",
  CANCELLED: "#ef4444",
};

const severityColors = {
  critical: "#ef4444",
  warning: "#f59e0b",
  info: "#3b82f6",
  success: "#22c55e",
};

const cardStyle = {
  background: "#fff",
  borderRadius: 12,
  padding: "1.25rem",
  boxShadow: "0 1px 3px rgba(0,0,0,0.08)",
  display: "flex",
  alignItems: "center",
  gap: "1rem",
};

const iconBoxStyle = (bg) => ({
  width: 48,
  height: 48,
  borderRadius: 12,
  background: bg,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  fontSize: "1.4rem",
});

const tableHeadStyle = {
  textAlign: "left",
  padding: "0.75rem 1rem",
  fontSize: "0.8rem",
  fontWeight: 600,
  textTransform: "uppercase",
  letterSpacing: "0.05em",
  color: "#6b7280",
  borderBottom: "2px solid #e5e7eb",
};

const tableCellStyle = {
  padding: "0.75rem 1rem",
  fontSize: "0.9rem",
  borderBottom: "1px solid #f3f4f6",
};

const btnBase = {
  padding: "0.6rem 1.25rem",
  border: "none",
  borderRadius: 8,
  fontWeight: 600,
  fontSize: "0.85rem",
  cursor: "pointer",
  transition: "opacity 0.15s",
};

const SellerDashboard = () => {
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [gstInfo, setGstInfo] = useState(null);
  const [gstEditing, setGstEditing] = useState(false);
  const [gstForm, setGstForm] = useState({ gstin: "", panNumber: "", businessName: "", businessAddress: "", licenses: "" });
  const [gstSaving, setGstSaving] = useState(false);
  const [gstSaveMsg, setGstSaveMsg] = useState("");

  useEffect(() => {
    const fetchAll = async () => {
      setLoading(true);
      setError("");
      try {
        const [dashRes, notifRes, gstRes] = await Promise.all([
          api.get("/api/seller/dashboard"),
          api.get("/api/seller/notifications"),
          api.get("/api/seller/gst-info"),
        ]);
        setData(dashRes.data);
        setNotifications(notifRes.data.alerts || []);
        setGstInfo(gstRes.data);
        setGstForm(gstRes.data);
      } catch (err) {
        setError(err.response?.data?.error || "Failed to load dashboard");
      }
      setLoading(false);
    };
    fetchAll();
  }, []);

  const handleGstSave = async () => {
    setGstSaving(true);
    setGstSaveMsg("");
    try {
      const res = await api.put("/api/seller/gst-info", gstForm);
      setGstInfo(res.data);
      setGstEditing(false);
      setGstSaveMsg("GST information saved successfully");
      setTimeout(() => setGstSaveMsg(""), 3000);
    } catch (err) {
      setGstSaveMsg(err.response?.data?.error || "Failed to save GST information");
    }
    setGstSaving(false);
  };

  if (loading) {
    return (
      <div style={{ padding: "2rem", maxWidth: 1200, margin: "0 auto" }}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: "1rem" }}>
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} style={{ ...cardStyle, height: 88, background: "#f3f4f6", animation: "none" }} />
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: "2rem", maxWidth: 1200, margin: "0 auto", textAlign: "center" }}>
        <div style={{ fontSize: "2rem", marginBottom: "0.5rem" }}>!</div>
        <p style={{ color: "#ef4444", marginBottom: "1rem" }}>{error}</p>
        <button style={{ ...btnBase, background: "#3b82f6", color: "#fff" }} onClick={() => window.location.reload()}>Retry</button>
      </div>
    );
  }

  const statCards = [
    { label: "Total Products", value: data.totalProducts, icon: "📦", bg: "#dbeafe" },
    { label: "Active Products", value: data.activeProducts, icon: "✅", bg: "#dcfce7" },
    { label: "Draft Products", value: data.draftProducts, icon: "📝", bg: "#f3e8ff" },
    { label: "Out of Stock", value: data.outOfStockProducts, icon: "❌", bg: "#fef2f2", color: "#dc2626" },
    { label: "Low Stock Alerts", value: data.lowStockProducts, icon: "⚠️", bg: "#fffbeb", color: "#d97706" },
    { label: "Orders in Progress", value: data.ordersInProgress, icon: "📋", bg: "#eff6ff", color: "#2563eb" },
  ];

  const secondRowCards = [
    { label: "Returns", value: data.totalReturns, icon: "🔄", bg: "#fef2f2", color: "#dc2626" },
    { label: "Refunds", value: data.totalRefunds, icon: "💳", bg: "#fef2f2", color: "#dc2626" },
    { label: "Replacements", value: data.replacements, icon: "🔄", bg: "#fffbeb", color: "#d97706" },
  ];

  const renderBadge = (status) => {
    const color = statusBadgeColors[status] || "#6b7280";
    return (
      <span style={{
        display: "inline-block",
        padding: "0.2rem 0.6rem",
        borderRadius: 999,
        fontSize: "0.75rem",
        fontWeight: 600,
        color: "#fff",
        background: color,
      }}>
        {status}
      </span>
    );
  };

  return (
    <div style={{ padding: "2rem", maxWidth: 1200, margin: "0 auto" }}>
      <div style={{ marginBottom: "2rem" }}>
        <h1 style={{ fontSize: "1.75rem", fontWeight: 700, margin: "0 0 0.25rem" }}>
          {data.storeName || "Seller Dashboard"}
        </h1>
        <p style={{ color: "#6b7280", fontSize: "0.9rem", margin: 0 }}>
          Welcome, {data.sellerName || data.sellerName || "Seller"}
        </p>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))", gap: "1rem", marginBottom: "1.5rem" }}>
        {statCards.map((c) => (
          <div key={c.label} style={cardStyle}>
            <div style={iconBoxStyle(c.bg)}>{c.icon}</div>
            <div>
              <div style={{ fontSize: "1.5rem", fontWeight: 700, color: c.color || "#111827", lineHeight: 1.2 }}>{c.value ?? 0}</div>
              <div style={{ fontSize: "0.8rem", color: "#6b7280", marginTop: 2 }}>{c.label}</div>
            </div>
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))", gap: "1rem", marginBottom: "2rem" }}>
        {secondRowCards.map((c) => (
          <div key={c.label} style={cardStyle}>
            <div style={iconBoxStyle(c.bg)}>{c.icon}</div>
            <div>
              <div style={{ fontSize: "1.5rem", fontWeight: 700, color: c.color || "#111827", lineHeight: 1.2 }}>{c.value ?? 0}</div>
              <div style={{ fontSize: "0.8rem", color: "#6b7280", marginTop: 2 }}>{c.label}</div>
            </div>
          </div>
        ))}
      </div>

      <div style={{ background: "#fff", borderRadius: 12, boxShadow: "0 1px 3px rgba(0,0,0,0.08)", marginBottom: "1.5rem", overflow: "hidden" }}>
        <div style={{ padding: "1rem 1.5rem", borderBottom: "1px solid #e5e7eb", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h3 style={{ margin: 0, fontSize: "1rem", fontWeight: 600 }}>GST Information</h3>
          {!gstEditing && (
            <button style={{ ...btnBase, background: "#0E5C5C", color: "#fff", padding: "0.4rem 1rem", fontSize: "0.8rem" }} onClick={() => { setGstEditing(true); setGstForm({ ...gstInfo }); }}>Edit</button>
          )}
        </div>
        <div style={{ padding: "1rem 1.5rem" }}>
          {gstSaveMsg && (
            <div style={{ padding: "0.5rem 1rem", borderRadius: 8, marginBottom: "0.75rem", fontSize: "0.85rem", background: gstSaveMsg.includes("successfully") ? "#dcfce7" : "#fef2f2", color: gstSaveMsg.includes("successfully") ? "#16a34a" : "#dc2626" }}>
              {gstSaveMsg}
            </div>
          )}
          {!gstEditing && gstInfo && (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: "0.75rem" }}>
              {[
                { label: "GSTIN", value: gstInfo.gstin || "Not set" },
                { label: "PAN Number", value: gstInfo.panNumber || "Not set" },
                { label: "Business Name", value: gstInfo.businessName || "Not set" },
                { label: "Business Address", value: gstInfo.businessAddress || "Not set" },
                { label: "Licenses", value: gstInfo.licenses || "None" },
              ].map((f) => (
                <div key={f.label}>
                  <div style={{ fontSize: "0.75rem", color: "#6b7280", marginBottom: 2 }}>{f.label}</div>
                  <div style={{ fontSize: "0.9rem", fontWeight: 500, color: !f.value || f.value === "Not set" || f.value === "None" ? "#9ca3af" : "#111827" }}>{f.value}</div>
                </div>
              ))}
            </div>
          )}
          {gstEditing && (
            <div>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: "1rem", marginBottom: "1rem" }}>
                <div>
                  <label style={{ display: "block", fontSize: "0.8rem", fontWeight: 600, color: "#374151", marginBottom: 4 }}>GSTIN</label>
                  <input style={{ width: "100%", padding: "0.5rem 0.75rem", border: "1px solid #d1d5db", borderRadius: 8, fontSize: "0.9rem", boxSizing: "border-box" }} value={gstForm.gstin} onChange={(e) => setGstForm({ ...gstForm, gstin: e.target.value })} placeholder="22AAAAA0000A1Z5" />
                </div>
                <div>
                  <label style={{ display: "block", fontSize: "0.8rem", fontWeight: 600, color: "#374151", marginBottom: 4 }}>PAN Number</label>
                  <input style={{ width: "100%", padding: "0.5rem 0.75rem", border: "1px solid #d1d5db", borderRadius: 8, fontSize: "0.9rem", boxSizing: "border-box" }} value={gstForm.panNumber} onChange={(e) => setGstForm({ ...gstForm, panNumber: e.target.value })} placeholder="AAAAA0000A" />
                </div>
                <div>
                  <label style={{ display: "block", fontSize: "0.8rem", fontWeight: 600, color: "#374151", marginBottom: 4 }}>Business Name</label>
                  <input style={{ width: "100%", padding: "0.5rem 0.75rem", border: "1px solid #d1d5db", borderRadius: 8, fontSize: "0.9rem", boxSizing: "border-box" }} value={gstForm.businessName} onChange={(e) => setGstForm({ ...gstForm, businessName: e.target.value })} placeholder="Your registered business name" />
                </div>
                <div>
                  <label style={{ display: "block", fontSize: "0.8rem", fontWeight: 600, color: "#374151", marginBottom: 4 }}>Business Address</label>
                  <input style={{ width: "100%", padding: "0.5rem 0.75rem", border: "1px solid #d1d5db", borderRadius: 8, fontSize: "0.9rem", boxSizing: "border-box" }} value={gstForm.businessAddress} onChange={(e) => setGstForm({ ...gstForm, businessAddress: e.target.value })} placeholder="Registered business address" />
                </div>
                <div style={{ gridColumn: "1 / -1" }}>
                  <label style={{ display: "block", fontSize: "0.8rem", fontWeight: 600, color: "#374151", marginBottom: 4 }}>Licenses (JSON format: {'[{"type":"FSSAI","number":"...","expiry":"..."}]'})</label>
                  <textarea style={{ width: "100%", padding: "0.5rem 0.75rem", border: "1px solid #d1d5db", borderRadius: 8, fontSize: "0.9rem", boxSizing: "border-box", minHeight: 80, fontFamily: "monospace" }} value={gstForm.licenses} onChange={(e) => setGstForm({ ...gstForm, licenses: e.target.value })} placeholder={'[{"type":"FSSAI","number":"123456789","expiry":"2026-12-31"}]'} />
                </div>
              </div>
              <div style={{ display: "flex", gap: "0.5rem" }}>
                <button style={{ ...btnBase, background: "#0E5C5C", color: "#fff" }} onClick={handleGstSave} disabled={gstSaving}>{gstSaving ? "Saving..." : "Save"}</button>
                <button style={{ ...btnBase, background: "#e5e7eb", color: "#374151" }} onClick={() => { setGstEditing(false); setGstForm({ ...gstInfo }); }}>Cancel</button>
              </div>
            </div>
          )}
        </div>
      </div>

      {(data.lowStockList || []).length > 0 && (
        <div style={{ background: "#fff", borderRadius: 12, boxShadow: "0 1px 3px rgba(0,0,0,0.08)", marginBottom: "1.5rem", border: "1px solid #fecaca", overflow: "hidden" }}>
          <div style={{ padding: "1rem 1.5rem", borderBottom: "1px solid #fecaca", background: "#fef2f2" }}>
            <h3 style={{ margin: 0, fontSize: "1rem", fontWeight: 600, color: "#dc2626" }}>Low Stock Alert</h3>
          </div>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr>
                <th style={tableHeadStyle}>Product Name</th>
                <th style={tableHeadStyle}>Stock</th>
              </tr>
            </thead>
            <tbody>
              {(data.lowStockList || []).map((p) => (
                <tr key={p.id || p._id}>
                  <td style={tableCellStyle}>{p.name}</td>
                  <td style={{ ...tableCellStyle, fontWeight: 600, color: "#dc2626" }}>{p.stock ?? p.quantity ?? 0}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {(data.inProgressOrders || []).length > 0 && (
        <div style={{ background: "#fff", borderRadius: 12, boxShadow: "0 1px 3px rgba(0,0,0,0.08)", marginBottom: "1.5rem", overflow: "hidden" }}>
          <div style={{ padding: "1rem 1.5rem", borderBottom: "1px solid #e5e7eb" }}>
            <h3 style={{ margin: 0, fontSize: "1rem", fontWeight: 600 }}>Orders in Progress</h3>
          </div>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr>
                <th style={tableHeadStyle}>Order ID</th>
                <th style={tableHeadStyle}>Customer</th>
                <th style={tableHeadStyle}>Amount</th>
                <th style={tableHeadStyle}>Status</th>
              </tr>
            </thead>
            <tbody>
              {(data.inProgressOrders || []).map((o) => (
                <tr key={o.id || o.orderId}>
                  <td style={tableCellStyle}>#{o.id || o.orderId}</td>
                  <td style={tableCellStyle}>{o.customerName || o.customer || "-"}</td>
                  <td style={tableCellStyle}>₹{o.amount?.toLocaleString() || o.totalAmount?.toLocaleString() || "0"}</td>
                  <td style={tableCellStyle}>{renderBadge(o.status)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {notifications.length > 0 && (
        <div style={{ background: "#fff", borderRadius: 12, boxShadow: "0 1px 3px rgba(0,0,0,0.08)", marginBottom: "1.5rem", overflow: "hidden" }}>
          <div style={{ padding: "1rem 1.5rem", borderBottom: "1px solid #e5e7eb" }}>
            <h3 style={{ margin: 0, fontSize: "1rem", fontWeight: 600 }}>Notifications</h3>
          </div>
          <div style={{ padding: "0.5rem 1.5rem 1rem" }}>
            {notifications.map((n, i) => {
              const sevColor = severityColors[n.severity] || "#6b7280";
              return (
                <div key={i} style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "0.75rem",
                  padding: "0.75rem 0",
                  borderBottom: i < notifications.length - 1 ? "1px solid #f3f4f6" : "none",
                }}>
                  <div style={{ width: 10, height: 10, borderRadius: "50%", background: sevColor, flexShrink: 0 }} />
                  <div style={{ flex: 1, fontSize: "0.9rem" }}>{n.message || n.type}</div>
                  {n.count > 1 && (
                    <span style={{
                      background: "#f3f4f6",
                      padding: "0.15rem 0.5rem",
                      borderRadius: 999,
                      fontSize: "0.75rem",
                      fontWeight: 600,
                      color: "#6b7280",
                    }}>
                      x{n.count}
                    </span>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap" }}>
        <button style={{ ...btnBase, background: "#3b82f6", color: "#fff" }} onClick={() => navigate("/seller/products")}>Manage Products</button>
        <button style={{ ...btnBase, background: "#22c55e", color: "#fff" }} onClick={() => navigate("/seller/products/add")}>Add Product</button>
        <button style={{ ...btnBase, background: "#6366f1", color: "#fff" }} onClick={() => navigate("/seller/products/bulk-upload")}>Bulk Upload</button>
      </div>
    </div>
  );
};

export default SellerDashboard;
