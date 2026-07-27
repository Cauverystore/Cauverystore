import React, { useState, useEffect, useCallback, useRef } from "react";
import api from "../api/axios";
import Pagination from "../components/Pagination";
import { useNavigate } from "react-router-dom";

const btnBase = {
  padding: "0.55rem 1rem",
  border: "none",
  borderRadius: 8,
  fontWeight: 600,
  fontSize: "0.85rem",
  cursor: "pointer",
  transition: "opacity 0.15s",
  whiteSpace: "nowrap",
};

const thStyle = {
  textAlign: "left",
  padding: "0.75rem 0.75rem",
  fontSize: "0.78rem",
  fontWeight: 600,
  textTransform: "uppercase",
  letterSpacing: "0.05em",
  color: "#6b7280",
  borderBottom: "2px solid #e5e7eb",
  background: "#f9fafb",
};

const tdStyle = {
  padding: "0.75rem 0.75rem",
  fontSize: "0.88rem",
  borderBottom: "1px solid #f3f4f6",
};

const statusColors = {
  Active: "#22c55e",
  Draft: "#f59e0b",
  "Out of Stock": "#ef4444",
  Inactive: "#6b7280",
};

const labelStyle = {
  display: "block",
  fontSize: "0.8rem",
  fontWeight: 600,
  color: "#374151",
  marginBottom: "0.35rem",
};

const selectStyle = {
  width: "100%",
  padding: "0.5rem 0.75rem",
  border: "1px solid #d1d5db",
  borderRadius: 6,
  fontSize: "0.85rem",
  background: "#fff",
};

const SellerProducts = () => {
  const navigate = useNavigate();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [selected, setSelected] = useState([]);
  const [bulkDeleting, setBulkDeleting] = useState(false);
  const mountedRef = useRef(true);
  const initialFetchDone = useRef(false);

  const [categories, setCategories] = useState([]);
  const [categoryFilter, setCategoryFilter] = useState("");
  const [stockStatusFilter, setStockStatusFilter] = useState("");
  const [dateAddedFilter, setDateAddedFilter] = useState("desc");

  const [analyticsProduct, setAnalyticsProduct] = useState(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);

  useEffect(() => {
    api.get("/api/admin/categories")
      .then((r) => setCategories(r.data || []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    return () => { mountedRef.current = false; };
  }, []);

  const fetchProducts = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const params = { page: page - 1, size: 20 };
      if (categoryFilter) params.category = categoryFilter;
      if (stockStatusFilter) params.stockStatus = stockStatusFilter;
      if (dateAddedFilter) params.dateAdded = dateAddedFilter;
      const res = await api.get("/api/seller/products", { params });
      if (!mountedRef.current) return;
      const data = res.data;
      setProducts(Array.isArray(data.content) ? data.content : Array.isArray(data) ? data : []);
      setTotalPages(data.totalPages || 1);
    } catch (err) {
      if (!mountedRef.current) return;
      setError(err.response?.data?.error || "Failed to load products");
    }
    if (mountedRef.current) setLoading(false);
  }, [page, categoryFilter, stockStatusFilter, dateAddedFilter]);

  useEffect(() => { fetchProducts(); }, [fetchProducts]);

  // Auto-refresh when page gains focus (bulk upload may have happened in another tab)
  useEffect(() => {
    const onFocus = () => { if (initialFetchDone.current) fetchProducts(); };
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, [fetchProducts]);

  useEffect(() => { initialFetchDone.current = true; }, [products]);

  const toggleSelect = (id) => setSelected((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);

  const handleBulkDelete = async () => {
    if (selected.length === 0) return;
    setBulkDeleting(true);
    setError("");
    try {
      await api.post("/api/seller/products/bulk-delete", { ids: selected });
      setSelected([]);
      await fetchProducts();
    } catch (err) {
      setError(err.response?.data?.error || "Failed to delete selected products");
    }
    setBulkDeleting(false);
  };

  const handleDelete = async (id) => {
    setError("");
    try {
      await api.delete(`/api/seller/products/${id}`);
      setProducts((prev) => prev.filter((p) => (p.id || p._id) !== id));
    } catch (err) {
      setError(err.response?.data?.error || "Failed to delete product");
    }
  };

  const openAnalytics = async (id) => {
    setAnalyticsLoading(true);
    setAnalyticsProduct(null);
    try {
      const res = await api.get(`/api/seller/products/${id}`);
      setAnalyticsProduct(res.data);
    } catch (err) {
      setError("Failed to load product analytics");
    }
    setAnalyticsLoading(false);
  };

  const getProductStatus = (p) => {
    if (p.stock === 0 || p.quantity === 0) return "Out of Stock";
    if (p.productStatus === "DRAFT" || p.approvalStatus === "DRAFT") return "Draft";
    if (p.active === false) return "Inactive";
    return "Active";
  };

  const getImageSrc = (p) => p.images?.[0]?.url || p.images?.[0] || p.image || "/images/placeholder.svg";

  const renderStatusBadge = (status) => {
    const color = statusColors[status] || "#6b7280";
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

  if (loading && products.length === 0) {
    return (
      <div style={{ padding: "2rem", maxWidth: 1200, margin: "0 auto" }}>
        <div style={{ background: "#fff", borderRadius: 12, padding: "1.5rem", boxShadow: "0 1px 3px rgba(0,0,0,0.08)" }}>
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} style={{ height: 48, background: "#f3f4f6", borderRadius: 6, marginBottom: "0.75rem" }} />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div style={{ padding: "2rem", maxWidth: 1200, margin: "0 auto" }}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "0.75rem", marginBottom: "1.5rem" }}>
        <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: 0 }}>My Products</h1>
        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
          <button style={{ ...btnBase, background: "#f3f4f6", color: "#374151" }} onClick={fetchProducts}>&#8635; Refresh</button>
          <a
            href={`${api.defaults.baseURL || "http://localhost:9091"}/api/seller/template.xlsx`}
            style={{ ...btnBase, background: "#f3f4f6", color: "#374151", textDecoration: "none", fontSize: "0.8rem" }}
          >
            Upload Template
          </a>
          <button style={{ ...btnBase, background: "#3b82f6", color: "#fff" }} onClick={() => navigate("/seller/products/add")}>Add Product</button>
        </div>
      </div>

      <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap", marginBottom: "1.25rem", alignItems: "flex-end" }}>
        <div style={{ minWidth: 160, flex: 1 }}>
          <label style={labelStyle}>Category</label>
          <select style={selectStyle} value={categoryFilter} onChange={(e) => { setPage(1); setCategoryFilter(e.target.value); }}>
            <option value="">All Categories</option>
            {categories.map((c) => (
              <option key={c.id || c._id} value={(c.id || c._id).toString()}>{c.name}</option>
            ))}
          </select>
        </div>
        <div style={{ minWidth: 140, flex: 1 }}>
          <label style={labelStyle}>Stock Status</label>
          <select style={selectStyle} value={stockStatusFilter} onChange={(e) => { setPage(1); setStockStatusFilter(e.target.value); }}>
            <option value="">All</option>
            <option value="in_stock">In Stock</option>
            <option value="out_of_stock">Out of Stock</option>
            <option value="low_stock">Low Stock</option>
            <option value="active">Active</option>
            <option value="draft">Draft</option>
          </select>
        </div>
        <div style={{ minWidth: 140, flex: 1 }}>
          <label style={labelStyle}>Date Added</label>
          <select style={selectStyle} value={dateAddedFilter} onChange={(e) => { setPage(1); setDateAddedFilter(e.target.value); }}>
            <option value="desc">Newest First</option>
            <option value="asc">Oldest First</option>
          </select>
        </div>
        <button style={{ ...btnBase, background: "#6366f1", color: "#fff", marginBottom: 1 }} onClick={() => { setPage(1); fetchProducts(); }}>Apply</button>
      </div>

      {error && (
        <div style={{ marginBottom: "1rem", padding: "0.75rem 1rem", background: "#fef2f2", borderRadius: 8, color: "#dc2626", fontSize: "0.9rem" }}>
          {error}
        </div>
      )}

      {(!loading && products.length > 0 && selected.length > 0) && (
        <div style={{ marginBottom: "1rem" }}>
          <button
            style={{ ...btnBase, background: "#ef4444", color: "#fff" }}
            onClick={handleBulkDelete}
            disabled={bulkDeleting}
          >
            {bulkDeleting ? "Deleting..." : `Delete Selected (${selected.length})`}
          </button>
        </div>
      )}

      {!loading && products.length === 0 ? (
        <div style={{ textAlign: "center", padding: "3rem 1rem", background: "#fff", borderRadius: 12, boxShadow: "0 1px 3px rgba(0,0,0,0.08)" }}>
          <div style={{ fontSize: "3rem", marginBottom: "0.5rem" }}>📦</div>
          <p style={{ color: "#6b7280", marginBottom: "1rem" }}>No products yet. Start by adding your first product.</p>
          <button style={{ ...btnBase, background: "#3b82f6", color: "#fff" }} onClick={() => navigate("/seller/products/add")}>Add Product</button>
        </div>
      ) : (
        <div style={{ background: "#fff", borderRadius: 12, boxShadow: "0 1px 3px rgba(0,0,0,0.08)", overflow: "hidden" }}>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr>
                  <th style={{ ...thStyle, width: 36 }}>
                    <input
                      type="checkbox"
                      onChange={(e) => e.target.checked ? setSelected(products.map((p) => p.id || p._id)) : setSelected([])}
                      checked={selected.length === products.length && products.length > 0}
                      style={{ cursor: "pointer", width: 16, height: 16 }}
                    />
                  </th>
                  <th style={thStyle}>Image</th>
                  <th style={thStyle}>Name</th>
                  <th style={thStyle}>Category</th>
                  <th style={thStyle}>Price</th>
                  <th style={thStyle}>Stock</th>
                  <th style={thStyle}>Status</th>
                  <th style={thStyle}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {products.map((p, idx) => {
                  const pid = p.id || p._id;
                  const status = getProductStatus(p);
                  return (
                    <tr key={pid} style={{ background: idx % 2 === 0 ? "#fff" : "#f9fafb" }}>
                      <td style={tdStyle}>
                        <input
                          type="checkbox"
                          checked={selected.includes(pid)}
                          onChange={() => toggleSelect(pid)}
                          style={{ cursor: "pointer", width: 16, height: 16 }}
                        />
                      </td>
                      <td style={tdStyle}>
                        <img src={getImageSrc(p)} alt="" style={{ width: 40, height: 40, objectFit: "cover", borderRadius: 6 }} />
                      </td>
                      <td style={{ ...tdStyle, fontWeight: 500 }}>{p.name}</td>
                      <td style={tdStyle}>{p.category?.name || "-"}</td>
                      <td style={tdStyle}>₹{p.offerPrice || p.price || 0}</td>
                      <td style={tdStyle}>{p.stock ?? p.quantity ?? 0}</td>
                      <td style={tdStyle}>{renderStatusBadge(status)}</td>
                      <td style={tdStyle}>
                        <div style={{ display: "flex", gap: "0.4rem", flexWrap: "nowrap" }}>
                          <button
                            style={{ ...btnBase, padding: "0.35rem 0.65rem", fontSize: "0.78rem", background: "#3b82f6", color: "#fff" }}
                            onClick={() => navigate(`/seller/products/edit/${pid}`)}
                          >
                            Edit
                          </button>
                          <button
                            style={{ ...btnBase, padding: "0.35rem 0.65rem", fontSize: "0.78rem", background: "#ef4444", color: "#fff" }}
                            onClick={() => handleDelete(pid)}
                          >
                            Delete
                          </button>
                          <button
                            style={{ ...btnBase, padding: "0.35rem 0.65rem", fontSize: "0.78rem", background: "#6366f1", color: "#fff" }}
                            onClick={() => openAnalytics(pid)}
                          >
                            Analytics
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div style={{ padding: "1rem 1.5rem", display: "flex", justifyContent: "space-between", alignItems: "center", borderTop: "1px solid #e5e7eb" }}>
            <span style={{ fontSize: "0.85rem", color: "#6b7280" }}>Showing {products.length} products</span>
            <Pagination page={page} totalPages={totalPages} onPage={setPage} />
          </div>
        </div>
      )}

      {analyticsLoading && (
        <div style={{
          position: "fixed", inset: 0, background: "rgba(0,0,0,0.4)", display: "flex",
          alignItems: "center", justifyContent: "center", zIndex: 1000,
        }}>
          <div style={{ background: "#fff", padding: "2rem", borderRadius: 12, fontSize: "0.9rem", color: "#6b7280" }}>Loading analytics...</div>
        </div>
      )}

      {analyticsProduct && (
        <div
          style={{
            position: "fixed", inset: 0, background: "rgba(0,0,0,0.4)", display: "flex",
            alignItems: "center", justifyContent: "center", zIndex: 1000, padding: "1rem",
          }}
          onClick={() => setAnalyticsProduct(null)}
        >
          <div
            style={{ background: "#fff", borderRadius: 12, maxWidth: 600, width: "100%", padding: "2rem", boxShadow: "0 20px 60px rgba(0,0,0,0.2)", position: "relative" }}
            onClick={(e) => e.stopPropagation()}
          >
            <button
              style={{ position: "absolute", top: "0.75rem", right: "0.75rem", border: "none", background: "none", fontSize: "1.25rem", cursor: "pointer", color: "#6b7280" }}
              onClick={() => setAnalyticsProduct(null)}
            >
              x
            </button>
            <h2 style={{ fontSize: "1.25rem", fontWeight: 700, margin: "0 0 1.25rem" }}>{analyticsProduct.name}</h2>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
              <div>
                <div style={{ fontSize: "0.75rem", color: "#6b7280", marginBottom: 2 }}>Total Views</div>
                <div style={{ fontSize: "1.25rem", fontWeight: 700 }}>{analyticsProduct.totalViews ?? analyticsProduct.views ?? 0}</div>
              </div>
              <div>
                <div style={{ fontSize: "0.75rem", color: "#6b7280", marginBottom: 2 }}>Add to Cart</div>
                <div style={{ fontSize: "1.25rem", fontWeight: 700 }}>{analyticsProduct.totalAddToCart ?? analyticsProduct.addToCart ?? 0}</div>
              </div>
              <div>
                <div style={{ fontSize: "0.75rem", color: "#6b7280", marginBottom: 2 }}>Wishlist</div>
                <div style={{ fontSize: "1.25rem", fontWeight: 700 }}>{analyticsProduct.totalWishlist ?? analyticsProduct.wishlist ?? 0}</div>
              </div>
              <div>
                <div style={{ fontSize: "0.75rem", color: "#6b7280", marginBottom: 2 }}>Orders</div>
                <div style={{ fontSize: "1.25rem", fontWeight: 700 }}>{analyticsProduct.totalOrders ?? analyticsProduct.orders ?? 0}</div>
              </div>
              <div>
                <div style={{ fontSize: "0.75rem", color: "#6b7280", marginBottom: 2 }}>Revenue</div>
                <div style={{ fontSize: "1.25rem", fontWeight: 700 }}>₹{analyticsProduct.totalRevenue?.toLocaleString() ?? analyticsProduct.revenue?.toLocaleString() ?? "0"}</div>
              </div>
              <div>
                <div style={{ fontSize: "0.75rem", color: "#6b7280", marginBottom: 2 }}>SKU</div>
                <div style={{ fontSize: "1.25rem", fontWeight: 700 }}>{analyticsProduct.sku || "-"}</div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SellerProducts;
