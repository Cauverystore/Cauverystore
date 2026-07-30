import React, { useState, useEffect } from "react";
import api from "../../api/axios";
import Pagination from "../../components/Pagination";

const AdminInventory = () => {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);

  useEffect(() => {
    const fetch = async () => {
      setLoading(true);
      try {
        const res = await api.get("/api/admin/products", { params: { page: page - 1 } });
        setProducts(res.data.content || res.data || []);
        setTotalPages(res.data.totalPages || 1);
      } catch (err) { console.error(err); }
      setLoading(false);
    };
    fetch();
  }, [page]);

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  return (
    <div>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 600, marginBottom: "1.5rem" }}>Inventory</h1>
      <div style={{ overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem" }}>
          <thead><tr style={{ background: "#f8fafc" }}><th style={thStyle}>Product</th><th style={thStyle}>SKU</th><th style={thStyle}>Stock</th><th style={thStyle}>Status</th></tr></thead>
          <tbody>
            {products.map((p) => (
              <tr key={p.id || p._id}>
                <td style={tdStyle}>{p.name}</td><td style={tdStyle}>{p.sku || "N/A"}</td><td style={tdStyle}>{p.stock || 0}</td>
                <td style={tdStyle}><span style={{ color: (p.stock || 0) > 0 ? "#16a34a" : "#dc2626", fontWeight: 500 }}>{(p.stock || 0) > 0 ? "In Stock" : "Out of Stock"}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={page} totalPages={totalPages} onPage={setPage} />
    </div>
  );
};
const thStyle = { padding: "0.75rem", borderBottom: "1px solid #e2e8f0", textAlign: "left", fontWeight: 600 };
const tdStyle = { padding: "0.5rem 0.75rem", borderBottom: "1px solid #e2e8f0" };
export default AdminInventory;
