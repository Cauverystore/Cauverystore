import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const emptyCoupon = { code:"", type:"PERCENTAGE", value:"", minOrderAmount:"", maxUses:"", maxDiscountAmount:"", validFrom:"", validUntil:"", appliesTo:"ALL", appliesToId:"", freeShipping:false, active:true };

const AdminCoupons = () => {
  const [coupons, setCoupons] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyCoupon);

  const fetchCoupons = async () => {
    try { setCoupons((await api.get("/api/admin/coupons")).data); } catch {}
  };

  useEffect(() => { fetchCoupons(); }, []);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setForm({ ...form, [name]: type === "checkbox" ? checked : value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const payload = { ...form, value: parseFloat(form.value), minOrderAmount: form.minOrderAmount ? parseFloat(form.minOrderAmount) : null, maxUses: form.maxUses ? parseInt(form.maxUses) : null, maxDiscountAmount: form.maxDiscountAmount ? parseFloat(form.maxDiscountAmount) : null };
    try {
      if (editing) { await api.put(`/api/admin/coupons/${editing}`, payload); }
      else { await api.post("/api/admin/coupons", payload); }
      setShowForm(false); setEditing(null); setForm(emptyCoupon); fetchCoupons();
    } catch { alert("Failed to save coupon"); }
  };

  const handleToggle = async (id) => {
    try { await api.put(`/api/admin/coupons/${id}/toggle`); fetchCoupons(); } catch { alert("Failed"); }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Delete this coupon?")) return;
    try { await api.delete(`/api/admin/coupons/${id}`); fetchCoupons(); } catch { alert("Failed"); }
  };

  const handleEdit = (c) => {
    setForm({ code:c.code, type:c.type, value:c.value, minOrderAmount:c.minOrderAmount||"", maxUses:c.maxUses||"", maxDiscountAmount:c.maxDiscountAmount||"", validFrom:c.validFrom?.split("T")[0]||"", validUntil:c.validUntil?.split("T")[0]||"", appliesTo:c.appliesTo||"ALL", appliesToId:c.appliesToId||"", freeShipping:c.freeShipping, active:c.active });
    setEditing(c.id); setShowForm(true);
  };

  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>Coupons & Promotions</h1>
        <button onClick={() => { setShowForm(!showForm); setEditing(null); setForm(emptyCoupon); }} style={{ padding:"0.5rem 1rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem", fontWeight:500 }}>{showForm ? "Cancel" : "Add Coupon"}</button>
      </div>

      {showForm && (
        <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, padding:"1.5rem", marginBottom:"1.5rem" }}>
          <h3 style={{ fontSize:"1.1rem", fontWeight:600, marginBottom:"1rem" }}>{editing ? "Edit Coupon" : "New Coupon"}</h3>
          <form onSubmit={handleSubmit}>
            <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fill,minmax(200px,1fr))", gap:"1rem", marginBottom:"1rem" }}>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Code</label><input name="code" value={form.code} onChange={handleChange} style={inp} required /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Type</label>
                <select name="type" value={form.type} onChange={handleChange} style={inp}>
                  <option value="PERCENTAGE">Percentage</option><option value="FIXED">Fixed Amount</option>
                </select>
              </div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Value</label><input name="value" type="number" step="0.01" value={form.value} onChange={handleChange} style={inp} required /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Min Order</label><input name="minOrderAmount" type="number" step="0.01" value={form.minOrderAmount} onChange={handleChange} style={inp} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Max Uses</label><input name="maxUses" type="number" value={form.maxUses} onChange={handleChange} style={inp} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Max Discount</label><input name="maxDiscountAmount" type="number" step="0.01" value={form.maxDiscountAmount} onChange={handleChange} style={inp} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Valid From</label><input name="validFrom" type="date" value={form.validFrom} onChange={handleChange} style={inp} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Valid Until</label><input name="validUntil" type="date" value={form.validUntil} onChange={handleChange} style={inp} /></div>
            </div>
            <div style={{ display:"flex", gap:"1rem", alignItems:"center" }}>
              <label style={{ fontSize:"0.85rem", display:"flex", alignItems:"center", gap:"6px" }}><input name="freeShipping" type="checkbox" checked={form.freeShipping} onChange={handleChange} /> Free Shipping</label>
              <label style={{ fontSize:"0.85rem", display:"flex", alignItems:"center", gap:"6px" }}><input name="active" type="checkbox" checked={form.active} onChange={handleChange} /> Active</label>
            </div>
            <button type="submit" style={{ marginTop:"1rem", padding:"0.5rem 1.5rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem" }}>{editing ? "Update" : "Create"}</button>
          </form>
        </div>
      )}

      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflow:"hidden" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["Code","Type","Value","Min Order","Uses","Expires","Status","Actions"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {coupons.length === 0 && <tr><td colSpan={8} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No coupons yet</td></tr>}
            {coupons.map(c => (
              <tr key={c.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                <td style={{ padding:"10px 12px", fontWeight:600 }}>{c.code}</td>
                <td style={{ padding:"10px 12px" }}>{c.type}</td>
                <td style={{ padding:"10px 12px" }}>{c.type === "PERCENTAGE" ? `${c.value}%` : `₹${c.value}`}</td>
                <td style={{ padding:"10px 12px" }}>{c.minOrderAmount ? `₹${c.minOrderAmount}` : "-"}</td>
                <td style={{ padding:"10px 12px" }}>{c.usedCount || 0}{c.maxUses ? ` / ${c.maxUses}` : ""}</td>
                <td style={{ padding:"10px 12px" }}>{c.validUntil ? new Date(c.validUntil).toLocaleDateString() : "-"}</td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:c.active?"#f0fdf4":"#fef2f2", color:c.active?"#16a34a":"#dc2626" }}>{c.active ? "Active" : "Inactive"}</span></td>
                <td style={{ padding:"10px 12px" }}>
                  <button onClick={() => handleEdit(c)} style={{ padding:"0.2rem 0.6rem", background:"#2563eb", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", marginRight:"4px", fontSize:"0.75rem" }}>Edit</button>
                  <button onClick={() => handleToggle(c.id)} style={{ padding:"0.2rem 0.6rem", background:c.active?"#f97316":"#16a34a", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", marginRight:"4px", fontSize:"0.75rem" }}>{c.active ? "Deactivate" : "Activate"}</button>
                  <button onClick={() => handleDelete(c.id)} style={{ padding:"0.2rem 0.6rem", background:"#dc2626", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem" }}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

const inp = { padding:"0.5rem 0.6rem", border:"1px solid #d1d5db", borderRadius:6, fontSize:"0.85rem", width:"100%", boxSizing:"border-box" };

export default AdminCoupons;
