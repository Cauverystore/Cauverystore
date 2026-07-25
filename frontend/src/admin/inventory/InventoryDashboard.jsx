import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import api from "../../api/axios";

const styles = {
  container: { padding: "24px" },
  header: { fontSize: "28px", fontWeight: "700", color: "#0B3D2E", marginBottom: "24px" },
  cardsRow: { display: "flex", gap: "16px", marginBottom: "24px", flexWrap: "wrap" },
  card: {
    background: "#fff",
    border: "1px solid #CFE8D6",
    borderRadius: "8px",
    padding: "20px",
    flex: "1 1 200px",
    minWidth: "180px",
    boxShadow: "0 1px 3px rgba(0,0,0,0.08)",
  },
  cardLabel: { fontSize: "13px", color: "#666", marginBottom: "8px", textTransform: "uppercase", letterSpacing: "0.5px" },
  cardValue: { fontSize: "28px", fontWeight: "700", color: "#0B3D2E" },
  sectionTitle: { fontSize: "20px", fontWeight: "600", color: "#0B3D2E", marginBottom: "12px", marginTop: "24px" },
  table: { width: "100%", borderCollapse: "collapse", background: "#fff", border: "1px solid #CFE8D6", borderRadius: "8px", overflow: "hidden" },
  th: { textAlign: "left", padding: "12px 16px", background: "#0B3D2E", color: "#fff", fontSize: "13px", fontWeight: "600", textTransform: "uppercase", letterSpacing: "0.5px" },
  td: { padding: "12px 16px", borderBottom: "1px solid #CFE8D6", fontSize: "14px", color: "#333" },
  badgeGreen: { background: "#E6F7EC", color: "#2E9B57", padding: "4px 10px", borderRadius: "12px", fontSize: "12px", fontWeight: "600" },
  badgeRed: { background: "#FEE2E2", color: "#dc2626", padding: "4px 10px", borderRadius: "12px", fontSize: "12px", fontWeight: "600" },
  editLink: { color: "#2E9B57", textDecoration: "none", fontWeight: "600", fontSize: "13px" },
  loading: { textAlign: "center", padding: "40px", color: "#666", fontSize: "16px" },
  error: { textAlign: "center", padding: "40px", color: "#dc2626", fontSize: "16px" },
};

export default function InventoryDashboard() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchProducts();
  }, []);

  const fetchProducts = async () => {
    try {
      setLoading(true);
      const { data } = await api.get("/api/admin/products/all");
      setProducts(data.products || data || []);
      setError(null);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to fetch products");
    } finally {
      setLoading(false);
    }
  };

  const productList = Array.isArray(products) ? products : [];

  const totalValue = productList.reduce((sum, p) => sum + (p.price || 0) * (p.stock || 0), 0);
  const totalUnits = productList.reduce((sum, p) => sum + (p.stock || 0), 0);
  const outOfStock = productList.filter((p) => p.stock === 0 || p.stock === null || p.stock === undefined).length;
  const lowStock = productList.filter((p) => p.stock > 0 && p.stock < 10).length;

  const lowStockProducts = productList.filter((p) => p.stock > 0 && p.stock < 10);
  const outOfStockProducts = productList.filter((p) => p.stock === 0 || p.stock === null || p.stock === undefined);

  if (loading) return <div style={styles.loading}>Loading inventory data...</div>;
  if (error) return <div style={styles.error}>{error}</div>;

  return (
    <div style={styles.container}>
      <h1 style={styles.header}>Inventory Dashboard</h1>

      <div style={styles.cardsRow}>
        <div style={styles.card}>
          <div style={styles.cardLabel}>Total Inventory Value</div>
          <div style={styles.cardValue}>${totalValue.toLocaleString()}</div>
        </div>
        <div style={styles.card}>
          <div style={styles.cardLabel}>Total Units</div>
          <div style={styles.cardValue}>{totalUnits.toLocaleString()}</div>
        </div>
        <div style={styles.card}>
          <div style={styles.cardLabel}>Out of Stock</div>
          <div style={{ ...styles.cardValue, color: outOfStock > 0 ? "#dc2626" : "#2E9B57" }}>{outOfStock}</div>
        </div>
        <div style={styles.card}>
          <div style={styles.cardLabel}>Low Stock</div>
          <div style={{ ...styles.cardValue, color: lowStock > 0 ? "#dc2626" : "#2E9B57" }}>{lowStock}</div>
        </div>
      </div>

      <h2 style={styles.sectionTitle}>Low Stock Products ({lowStockProducts.length})</h2>
      {lowStockProducts.length === 0 ? (
        <p style={{ color: "#666" }}>No low stock products.</p>
      ) : (
        <table style={styles.table}>
          <thead>
            <tr>
              <th style={styles.th}>Name</th>
              <th style={styles.th}>SKU</th>
              <th style={styles.th}>Stock</th>
              <th style={styles.th}>Price</th>
              <th style={styles.th}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {lowStockProducts.map((p) => (
              <tr key={p._id || p.id}>
                <td style={styles.td}>{p.name}</td>
                <td style={styles.td}>{p.sku || "-"}</td>
                <td style={styles.td}><span style={styles.badgeGreen}>{p.stock}</span></td>
                <td style={styles.td}>${(p.price || 0).toFixed(2)}</td>
                <td style={styles.td}>
                  <Link to={`/admin/products/edit/${p._id || p.id}`} style={styles.editLink}>Edit</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2 style={styles.sectionTitle}>Out of Stock Products ({outOfStockProducts.length})</h2>
      {outOfStockProducts.length === 0 ? (
        <p style={{ color: "#666" }}>No out of stock products.</p>
      ) : (
        <table style={styles.table}>
          <thead>
            <tr>
              <th style={styles.th}>Name</th>
              <th style={styles.th}>SKU</th>
              <th style={styles.th}>Stock</th>
              <th style={styles.th}>Price</th>
              <th style={styles.th}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {outOfStockProducts.map((p) => (
              <tr key={p._id || p.id}>
                <td style={styles.td}>{p.name}</td>
                <td style={styles.td}>{p.sku || "-"}</td>
                <td style={styles.td}><span style={styles.badgeRed}>0</span></td>
                <td style={styles.td}>${(p.price || 0).toFixed(2)}</td>
                <td style={styles.td}>
                  <Link to={`/admin/products/edit/${p._id || p.id}`} style={styles.editLink}>Edit</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
