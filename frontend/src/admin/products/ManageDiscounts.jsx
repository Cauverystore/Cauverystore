import React, { useState, useEffect } from "react";
import { useParams } from "react-router-dom";
import api from "../../api/axios";

const ManageDiscounts = () => {
  const { id } = useParams();
  const [discounts, setDiscounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ percentage: "", startDate: "", endDate: "" });

  useEffect(() => {
    const fetch = async () => {
      try {
        const res = await api.get(`/api/admin/products/${id}/discounts`);
        setDiscounts(res.data || []);
      } catch (err) { console.error(err); }
      setLoading(false);
    };
    fetch();
  }, [id]);

  const handleAdd = async () => {
    try {
      await api.post(`/api/admin/products/${id}/discounts`, { percentage: parseFloat(form.percentage), startDate: form.startDate, endDate: form.endDate });
      setForm({ percentage: "", startDate: "", endDate: "" });
      const res = await api.get(`/api/admin/products/${id}/discounts`);
      setDiscounts(res.data || []);
    } catch (err) { alert("Failed to add discount"); }
  };

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  return (
    <div style={{ maxWidth: "700px" }}>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 600, marginBottom: "1.5rem" }}>Manage Discounts</h1>
      <div style={{ display: "flex", gap: "0.75rem", marginBottom: "1.5rem", flexWrap: "wrap" }}>
        <input placeholder="Discount %" type="number" value={form.percentage} onChange={(e) => setForm({ ...form, percentage: e.target.value })} style={inputStyle} />
        <input type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} style={inputStyle} />
        <input type="date" value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} style={inputStyle} />
        <button onClick={handleAdd} style={{ padding: "0.5rem 1.25rem", background: "#16a34a", color: "#fff", border: "none", borderRadius: 6, cursor: "pointer" }}>Add</button>
      </div>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead><tr style={{ background: "#f8fafc" }}><th style={thStyle}>%</th><th style={thStyle}>Start</th><th style={thStyle}>End</th><th style={thStyle}>Status</th></tr></thead>
        <tbody>
          {discounts.map((d, i) => {
            const now = new Date();
            const active = new Date(d.startDate) <= now && new Date(d.endDate) >= now;
            return (
              <tr key={d.id || d._id || i}><td style={tdStyle}>{d.percentage}%</td><td style={tdStyle}>{new Date(d.startDate).toLocaleDateString()}</td><td style={tdStyle}>{new Date(d.endDate).toLocaleDateString()}</td><td style={tdStyle}><span style={{ color: active ? "#16a34a" : "#dc2626", fontWeight: 500 }}>{active ? "Active" : "Inactive"}</span></td></tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};

const inputStyle = { padding: "0.5rem 0.75rem", border: "1px solid #e2e8f0", borderRadius: 6, fontSize: "0.9rem" };
const thStyle = { padding: "0.75rem", borderBottom: "1px solid #e2e8f0", textAlign: "left", fontWeight: 600 };
const tdStyle = { padding: "0.5rem 0.75rem", borderBottom: "1px solid #e2e8f0" };
export default ManageDiscounts;
