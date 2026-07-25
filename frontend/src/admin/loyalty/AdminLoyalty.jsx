import React, { useState, useEffect } from "react";
import api from "../../api/axios";
const emptyRule = { name:"", description:"", pointsPerUnit:1, minimumOrderAmount:0, active:true };
const AdminLoyalty = () => {
  const [rules, setRules] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyRule);
  const fetchRules = async () => { try { setRules((await api.get("/api/admin/loyalty/rules")).data); } catch {} };
  useEffect(() => { fetchRules(); }, []);
  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const payload = { ...form, pointsPerUnit: parseFloat(form.pointsPerUnit), minimumOrderAmount: parseFloat(form.minimumOrderAmount) };
      if (editing) { await api.put(`/api/admin/loyalty/rules/${editing}`, payload); } else { await api.post("/api/admin/loyalty/rules", payload); }
      setShowForm(false); setEditing(null); setForm(emptyRule); fetchRules();
    } catch { alert("Failed"); }
  };
  const handleDelete = async (id) => { if (!window.confirm("Delete this rule?")) return; try { await api.delete(`/api/admin/loyalty/rules/${id}`); fetchRules(); } catch { alert("Failed"); } };
  const handleEdit = (r) => { setForm({ name:r.name||"", description:r.description||"", pointsPerUnit:r.pointsPerUnit||1, minimumOrderAmount:r.minimumOrderAmount||0, active:r.active }); setEditing(r.id); setShowForm(true); };
  const inp = { padding:"0.5rem 0.6rem", border:"1px solid #d1d5db", borderRadius:6, fontSize:"0.85rem", width:"100%", boxSizing:"border-box" };
  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>Loyalty Rules</h1>
        <button onClick={() => { setShowForm(!showForm); setEditing(null); setForm(emptyRule); }} style={{ padding:"0.5rem 1rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem", fontWeight:500 }}>{showForm ? "Cancel" : "Add Rule"}</button>
      </div>
      {showForm && (
        <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, padding:"1.5rem", marginBottom:"1.5rem" }}>
          <h3 style={{ fontSize:"1.1rem", fontWeight:600, marginBottom:"1rem" }}>{editing ? "Edit Rule" : "New Rule"}</h3>
          <form onSubmit={handleSubmit}>
            <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fill,minmax(250px,1fr))", gap:"1rem", marginBottom:"1rem" }}>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Name</label><input value={form.name} onChange={e => setForm({...form,name:e.target.value})} style={inp} required /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Description</label><textarea value={form.description} onChange={e => setForm({...form,description:e.target.value})} style={{...inp,minHeight:60}} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Points Per Unit (₹)</label><input type="number" step="0.1" value={form.pointsPerUnit} onChange={e => setForm({...form,pointsPerUnit:e.target.value})} style={inp} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Min Order Amount</label><input type="number" step="0.01" value={form.minimumOrderAmount} onChange={e => setForm({...form,minimumOrderAmount:e.target.value})} style={inp} /></div>
            </div>
            <label style={{ fontSize:"0.85rem", display:"flex", alignItems:"center", gap:"6px" }}><input type="checkbox" checked={form.active} onChange={e => setForm({...form,active:e.target.checked})} /> Active</label>
            <button type="submit" style={{ marginTop:"1rem", padding:"0.5rem 1.5rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem" }}>{editing ? "Update" : "Create"}</button>
          </form>
        </div>
      )}
      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflow:"hidden" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["Name","Points/Unit","Min Order","Status","Actions"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {rules.length === 0 && <tr><td colSpan={5} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No rules yet</td></tr>}
            {rules.map(r => (
              <tr key={r.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                <td style={{ padding:"10px 12px", fontWeight:500 }}>{r.name}</td>
                <td style={{ padding:"10px 12px" }}>{r.pointsPerUnit}</td>
                <td style={{ padding:"10px 12px" }}>₹{r.minimumOrderAmount?.toLocaleString() || "0"}</td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:r.active?"#f0fdf4":"#fef2f2", color:r.active?"#16a34a":"#dc2626" }}>{r.active ? "Active" : "Inactive"}</span></td>
                <td style={{ padding:"10px 12px" }}>
                  <button onClick={() => handleEdit(r)} style={{ padding:"0.2rem 0.6rem", background:"#2563eb", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", marginRight:"4px", fontSize:"0.75rem" }}>Edit</button>
                  <button onClick={() => handleDelete(r.id)} style={{ padding:"0.2rem 0.6rem", background:"#dc2626", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem" }}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
export default AdminLoyalty;