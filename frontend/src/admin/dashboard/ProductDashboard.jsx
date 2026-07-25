import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../api/axios";
import { imgUrl } from "../../utils/images";
import "../../styles/admin.css";

const ProductDashboard = () => {
  const navigate = useNavigate();
  const [stats, setStats] = useState(null);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState({ q: "", brand: "", category: "", stockStatus: "" });
  const [brands, setBrands] = useState([]);
  const [categories, setCategories] = useState([]);

  useEffect(() => {
    Promise.all([
      api.get("/api/admin/product-dashboard"),
      api.get("/api/admin/products/all"),
      api.get("/api/categories"),
    ]).then(([s, p, c]) => {
      setStats(s.data || {});
      setProducts(Array.isArray(p.data) ? p.data : p.data?.content || []);
      const cats = Array.isArray(c.data) ? c.data : [];
      setCategories(cats);
      const brs = [...new Set((Array.isArray(p.data) ? p.data : []).map((x) => x.brand).filter(Boolean))];
      setBrands(brs);
    }).catch(() => {}).finally(() => setLoading(false));
  }, []);

  const filtered = products.filter((p) => {
    if (search.q && !((p.name || "").toLowerCase().includes(search.q.toLowerCase()) || (p.sku || "").toLowerCase().includes(search.q.toLowerCase()) || (p.brand || "").toLowerCase().includes(search.q.toLowerCase()))) return false;
    if (search.brand && p.brand !== search.brand) return false;
    if (search.category && (p.category?.name || "") !== search.category) return false;
    if (search.stockStatus === "in_stock" && (p.stock == null || p.stock <= 0)) return false;
    if (search.stockStatus === "out_of_stock" && p.stock != null && p.stock > 0) return false;
    if (search.stockStatus === "low_stock" && (p.stock == null || p.stock <= 0 || p.stock >= 10)) return false;
    return true;
  });

  const StatCard = ({ label, value, color }) => (
    <div style={{ background: "#fff", borderRadius: "8px", border: `1px solid #CFE8D6`, borderTop: `3px solid ${color}`, padding: "1rem", textAlign: "center" }}>
      <div style={{ fontSize: "1.5rem", fontWeight: 700, color: "#0B3D2E" }}>{value ?? 0}</div>
      <div style={{ fontSize: "0.75rem", color: "#64748B", marginTop: "4px" }}>{label}</div>
    </div>
  );

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  const SC = {
    wrap: { maxWidth: "1200px", padding: "1.5rem" },
    h1: { fontSize: "1.5rem", fontWeight: 700, color: "#0B3D2E", marginBottom: "0.25rem" },
    sub: { color: "#64748B", marginBottom: "1.25rem", fontSize: "0.85rem" },
    grid: { display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))", gap: "0.75rem", marginBottom: "1.5rem" },
    section: { background: "#fff", borderRadius: "8px", border: "1px solid #CFE8D6", padding: "1rem", marginBottom: "1rem" },
    sTitle: { fontSize: "0.9rem", fontWeight: 700, color: "#0B3D2E", marginBottom: "0.75rem", paddingBottom: "0.4rem", borderBottom: "2px solid #EAF7EE" },
    input: { width: "100%", padding: "8px 10px", border: "1px solid #CFE8D6", borderRadius: "6px", fontSize: "0.85rem", outline: "none", boxSizing: "border-box" },
    select: { width: "100%", padding: "8px 10px", border: "1px solid #CFE8D6", borderRadius: "6px", fontSize: "0.85rem", background: "#fff", outline: "none", boxSizing: "border-box" },
  };

  return (
    <div style={SC.wrap}>
      <h1 style={SC.h1}>Product Dashboard</h1>
      <p style={SC.sub}>Key metrics and product overview</p>

      <div style={SC.grid}>
        <StatCard label="Total Products" value={stats.totalProducts} color="#0E5C5C" />
        <StatCard label="Active" value={stats.active} color="#16a34a" />
        <StatCard label="Draft" value={stats.draft} color="#f59e0b" />
        <StatCard label="Out of Stock" value={stats.outOfStock} color="#dc2626" />
        <StatCard label="Low Stock" value={stats.lowStock} color="#f97316" />
        <StatCard label="Pending Approval" value={stats.pendingApproval} color="#3b82f6" />
        <StatCard label="Rejected" value={stats.rejected} color="#991b1b" />
        <StatCard label="Recently Added" value={stats.recentlyAdded} color="#8b5cf6" />
        <StatCard label="Featured" value={stats.featured} color="#0E5C5C" />
        <StatCard label="Trending" value={stats.trending} color="#06b6d4" />
        <StatCard label="Best Seller" value={stats.bestSeller} color="#C8A24B" />
        <StatCard label="New Arrival" value={stats.newArrival} color="#ec4899" />
      </div>

      <div style={SC.section}>
        <h3 style={SC.sTitle}>Advanced Search</h3>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))", gap: "0.5rem" }}>
          <input style={SC.input} placeholder="Search name, SKU, brand..." value={search.q} onChange={(e) => setSearch({ ...search, q: e.target.value })} />
          <select style={SC.select} value={search.brand} onChange={(e) => setSearch({ ...search, brand: e.target.value })}>
            <option value="">All Brands</option>
            {brands.map((b) => <option key={b} value={b}>{b}</option>)}
          </select>
          <select style={SC.select} value={search.category} onChange={(e) => setSearch({ ...search, category: e.target.value })}>
            <option value="">All Categories</option>
            {categories.map((c) => <option key={c.id} value={c.name}>{c.name}</option>)}
          </select>
          <select style={SC.select} value={search.stockStatus} onChange={(e) => setSearch({ ...search, stockStatus: e.target.value })}>
            <option value="">All Stock</option>
            <option value="in_stock">In Stock</option>
            <option value="out_of_stock">Out of Stock</option>
            <option value="low_stock">Low Stock (&lt;10)</option>
          </select>
        </div>
      </div>

      <div style={SC.section}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.75rem", paddingBottom: "0.4rem", borderBottom: "2px solid #EAF7EE" }}>
          <h3 style={{ fontSize: "0.9rem", fontWeight: 700, color: "#0B3D2E", margin: 0 }}>Products ({filtered.length})</h3>
          <button onClick={() => navigate("/admin/products/add")} style={{ padding: "6px 14px", background: "#2E9B57", color: "#fff", border: "none", borderRadius: "4px", cursor: "pointer", fontSize: "0.8rem", fontWeight: 600 }}>+ Add Product</button>
        </div>
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.8rem" }}>
            <thead>
              <tr style={{ background: "#f8f8f8" }}>
                <th style={{ padding: "8px", textAlign: "left", minWidth: "50px" }}>Image</th>
                <th style={{ padding: "8px", textAlign: "left", minWidth: "120px" }}>Name</th>
                <th style={{ padding: "8px", textAlign: "left", minWidth: "100px" }}>SKU</th>
                <th style={{ padding: "8px", textAlign: "left", minWidth: "80px" }}>Brand</th>
                <th style={{ padding: "8px", textAlign: "left", minWidth: "80px" }}>Category</th>
                <th style={{ padding: "8px", textAlign: "right", minWidth: "70px" }}>Price</th>
                <th style={{ padding: "8px", textAlign: "right", minWidth: "60px" }}>Stock</th>
                <th style={{ padding: "8px", textAlign: "center", minWidth: "60px" }}>Status</th>
                <th style={{ padding: "8px", textAlign: "right", minWidth: "80px" }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.slice(0, 50).map((p) => (
                <tr key={p.id} style={{ borderBottom: "1px solid var(--sn-border)" }}>
                  <td style={{ padding: "8px" }}>
                    <div style={{ width: "36px", height: "36px", borderRadius: "4px", background: "#f1f5f9", overflow: "hidden" }}>
                      {(() => { const url = p.images?.find((i) => i.main)?.url || p.images?.[0]?.url; const full = imgUrl(url); return full ? <img src={full} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} onError={(e) => { e.target.style.display = "none" }} /> : null; })()}
                    </div>
                  </td>
                  <td style={{ padding: "8px", fontWeight: 500 }}>{p.name}</td>
                  <td style={{ padding: "8px", fontFamily: "monospace", fontSize: "0.75rem" }}>{p.sku || p.productCode || "-"}</td>
                  <td style={{ padding: "8px" }}>{p.brand || "-"}</td>
                  <td style={{ padding: "8px" }}>{p.category?.name || "-"}</td>
                  <td style={{ padding: "8px", textAlign: "right" }}>₹{(p.offerPrice || p.price || 0).toLocaleString()}</td>
                  <td style={{ padding: "8px", textAlign: "right", color: p.stock != null && p.stock < 10 ? "#dc2626" : "inherit", fontWeight: p.stock != null && p.stock < 10 ? 700 : 400 }}>{p.stock ?? 0}</td>
                  <td style={{ padding: "8px", textAlign: "center" }}>
                    <span style={{ padding: "2px 8px", borderRadius: "10px", fontSize: "0.7rem", fontWeight: 600, background: p.active ? "#EAF7EE" : "#fef2f2", color: p.active ? "#146C43" : "#dc2626" }}>{p.active ? "Active" : "Inactive"}</span>
                  </td>
                  <td style={{ padding: "8px", textAlign: "right", whiteSpace: "nowrap" }}>
                    <button onClick={() => navigate(`/admin/products/edit/${p.id}`)} style={{ padding: "4px 10px", border: "1px solid #CFE8D6", borderRadius: "4px", background: "#fff", cursor: "pointer", fontSize: "0.75rem", marginRight: "4px" }}>Edit</button>
                    <button onClick={() => navigate(`/admin/products/${p.id}/images`)} style={{ padding: "4px 10px", border: "1px solid #CFE8D6", borderRadius: "4px", background: "#EAF7EE", color: "#146C43", cursor: "pointer", fontSize: "0.75rem" }}>Images</button>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && <tr><td colSpan={9} style={{ padding: "2rem", textAlign: "center", color: "#64748B" }}>No products found.</td></tr>}
            </tbody>
          </table>
        </div>
        {filtered.length > 50 && <p style={{ fontSize: "0.75rem", color: "#94a3b8", textAlign: "center", marginTop: "0.5rem" }}>Showing first 50 of {filtered.length} results</p>}
      </div>
    </div>
  );
};

export default ProductDashboard;
