import React, { useState, useEffect } from "react";
import api from "../api/axios";

const cardBase = {
  background: "#fff",
  borderRadius: 12,
  padding: "1.25rem",
  boxShadow: "0 1px 3px rgba(0,0,0,0.08)",
  textAlign: "center",
};

const thStyle = {
  textAlign: "left",
  padding: "0.7rem 0.75rem",
  fontSize: "0.76rem",
  fontWeight: 600,
  textTransform: "uppercase",
  letterSpacing: "0.05em",
  color: "#6b7280",
  borderBottom: "2px solid #e5e7eb",
  background: "#f9fafb",
  whiteSpace: "nowrap",
};

const tdStyle = {
  padding: "0.7rem 0.75rem",
  fontSize: "0.87rem",
  borderBottom: "1px solid #f3f4f6",
  whiteSpace: "nowrap",
};

const conversionColor = (rate) => {
  if (rate == null) return "#6b7280";
  if (rate > 5) return "#22c55e";
  if (rate >= 1) return "#f59e0b";
  return "#ef4444";
};

const SellerAnalytics = () => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");

  useEffect(() => {
    api.get("/api/seller/analytics")
      .then((r) => setData(r.data))
      .catch((err) => setError(err.response?.data?.error || "Failed to load analytics"))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div style={{ padding: "2rem", maxWidth: 1200, margin: "0 auto" }}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))", gap: "1rem" }}>
          {Array.from({ length: 7 }).map((_, i) => (
            <div key={i} style={{ ...cardBase, height: 100, background: "#f3f4f6" }} />
          ))}
        </div>
        <div style={{ marginTop: "1.5rem" }}>
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} style={{ height: 48, background: "#f3f4f6", borderRadius: 8, marginBottom: "0.75rem" }} />
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
        <button
          style={{ padding: "0.55rem 1.25rem", border: "none", borderRadius: 8, background: "#3b82f6", color: "#fff", fontWeight: 600, cursor: "pointer" }}
          onClick={() => window.location.reload()}
        >
          Retry
        </button>
      </div>
    );
  }

  const summaryCards = [
    { label: "Total Views", value: data.totalViews, color: "#3b82f6" },
    { label: "Add to Cart", value: data.totalAddToCart, color: "#8b5cf6" },
    { label: "Wishlist Adds", value: data.totalWishlist, color: "#ec4899" },
    { label: "Total Orders", value: data.totalOrders, color: "#f59e0b" },
    { label: "Total Revenue", value: `₹${(data.totalRevenue || 0).toLocaleString()}`, color: "#22c55e" },
    { label: "Conversion Rate", value: `${(data.conversionRate || 0).toFixed(2)}%`, color: conversionColor(data.conversionRate) },
    { label: "Return Rate", value: `${(data.returnRate || 0).toFixed(2)}%`, color: "#ef4444" },
  ];

  const productPerformance = (data.productPerformance || [])
    .filter((p) => {
      if (!search) return true;
      const q = search.toLowerCase();
      return (p.name || "").toLowerCase().includes(q) || (p.sku || "").toLowerCase().includes(q);
    });

  return (
    <div style={{ padding: "2rem", maxWidth: 1200, margin: "0 auto" }}>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: "0 0 1.5rem" }}>Product Analytics</h1>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(150px, 1fr))", gap: "1rem", marginBottom: "2rem" }}>
        {summaryCards.map((c) => (
          <div key={c.label} style={cardBase}>
            <div style={{ fontSize: "1.5rem", fontWeight: 700, color: c.color, lineHeight: 1.2 }}>{c.value ?? 0}</div>
            <div style={{ fontSize: "0.78rem", color: "#6b7280", marginTop: "0.35rem" }}>{c.label}</div>
          </div>
        ))}
      </div>

      <div style={{ marginBottom: "1.25rem" }}>
        <input
          type="text"
          placeholder="Search by product name or SKU..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{
            width: "100%",
            maxWidth: 400,
            padding: "0.65rem 1rem",
            border: "1px solid #d1d5db",
            borderRadius: 8,
            fontSize: "0.9rem",
            outline: "none",
          }}
        />
      </div>

      {productPerformance.length === 0 ? (
        <div style={{ textAlign: "center", padding: "3rem 1rem", background: "#fff", borderRadius: 12, boxShadow: "0 1px 3px rgba(0,0,0,0.08)" }}>
          <p style={{ color: "#6b7280" }}>No product performance data available.</p>
        </div>
      ) : (
        <div style={{ background: "#fff", borderRadius: 12, boxShadow: "0 1px 3px rgba(0,0,0,0.08)", overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 900 }}>
            <thead>
              <tr>
                <th style={thStyle}>Product Name</th>
                <th style={thStyle}>SKU</th>
                <th style={thStyle}>Price</th>
                <th style={thStyle}>Stock</th>
                <th style={thStyle}>Views</th>
                <th style={thStyle}>Add-to-Cart</th>
                <th style={thStyle}>Wishlist</th>
                <th style={thStyle}>Orders</th>
                <th style={thStyle}>Revenue</th>
                <th style={thStyle}>Conversion</th>
              </tr>
            </thead>
            <tbody>
              {productPerformance.map((p, idx) => {
                const convRate = p.conversionRate ?? (p.orders && p.views ? (p.orders / p.views) * 100 : 0);
                return (
                  <tr key={p.id || p._id || idx} style={{ background: idx % 2 === 0 ? "#fff" : "#f9fafb" }}>
                    <td style={{ ...tdStyle, fontWeight: 500 }}>{p.name}</td>
                    <td style={tdStyle}>{p.sku || "-"}</td>
                    <td style={tdStyle}>₹{p.price ?? p.offerPrice ?? 0}</td>
                    <td style={tdStyle}>{p.stock ?? p.quantity ?? 0}</td>
                    <td style={tdStyle}>{p.views ?? p.totalViews ?? 0}</td>
                    <td style={tdStyle}>{p.addToCart ?? p.totalAddToCart ?? 0}</td>
                    <td style={tdStyle}>{p.wishlist ?? p.totalWishlist ?? 0}</td>
                    <td style={tdStyle}>{p.orders ?? p.totalOrders ?? 0}</td>
                    <td style={tdStyle}>₹{(p.revenue ?? p.totalRevenue ?? 0).toLocaleString()}</td>
                    <td style={{ ...tdStyle, fontWeight: 600, color: conversionColor(convRate) }}>
                      {convRate.toFixed(2)}%
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default SellerAnalytics;
