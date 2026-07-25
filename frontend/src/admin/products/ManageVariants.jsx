import React, { useState, useEffect } from "react";
import { useParams } from "react-router-dom";
import api from "../../api/axios";

const ManageVariants = () => {
  const { id } = useParams();
  const [variants, setVariants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ name: "", price: "", stock: "" });

  useEffect(() => {
    const fetch = async () => {
      try {
        const res = await api.get(`/api/products/${id}`);
        setVariants(res.data.variants || []);
      } catch (err) { console.error(err); }
      setLoading(false);
    };
    fetch();
  }, [id]);

  const handleAdd = async () => {
    try {
      await api.post(`/api/admin/products/${id}/variants`, { options: [{ name: form.name, price: parseFloat(form.price), stock: parseInt(form.stock) }] });
      setForm({ name: "", price: "", stock: "" });
      const res = await api.get(`/api/products/${id}`);
      setVariants(res.data.variants || []);
    } catch (err) { alert("Failed to add variant"); }
  };

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  return (
    <div style={{ maxWidth: "700px" }}>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 600, marginBottom: "1.5rem" }}>Manage Variants</h1>
      <div style={{ display: "flex", gap: "0.75rem", marginBottom: "1.5rem", flexWrap: "wrap" }}>
        <input placeholder="Variant Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} style={inputStyle} />
        <input placeholder="Price" type="number" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} style={inputStyle} />
        <input placeholder="Stock" type="number" value={form.stock} onChange={(e) => setForm({ ...form, stock: e.target.value })} style={inputStyle} />
        <button onClick={handleAdd} style={{ padding: "0.5rem 1.25rem", background: "#16a34a", color: "#fff", border: "none", borderRadius: 6, cursor: "pointer" }}>Add</button>
      </div>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead><tr style={{ background: "#f8fafc" }}><th style={thStyle}>Name</th><th style={thStyle}>Price</th><th style={thStyle}>Stock</th></tr></thead>
        <tbody>
          {variants.map((v, i) => (
            <tr key={v.id || v._id || i}><td style={tdStyle}>{v.name || v.value}</td><td style={tdStyle}>&#8377;{v.price}</td><td style={tdStyle}>{v.stock}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

const inputStyle = { padding: "0.5rem 0.75rem", border: "1px solid #e2e8f0", borderRadius: 6, fontSize: "0.9rem" };
const thStyle = { padding: "0.75rem", borderBottom: "1px solid #e2e8f0", textAlign: "left", fontWeight: 600 };
const tdStyle = { padding: "0.5rem 0.75rem", borderBottom: "1px solid #e2e8f0" };
export default ManageVariants;
