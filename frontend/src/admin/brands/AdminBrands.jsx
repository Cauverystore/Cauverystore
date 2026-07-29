import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const emptyBrand = { name:"", description:"", logo:"", active:true, sortOrder:0 };

const AdminBrands = () => {
  const [brands, setBrands] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyBrand);

  const fetchBrands = async () => {
    try { setBrands((await api.get("/api/admin/brands")).data); } catch {}
  };

  useEffect(() => { fetchBrands(); }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      if (editing) { await api.put(`/api/admin/brands/${editing}`, { ...form, sortOrder: parseInt(form.sortOrder) }); }
      else { await api.post("/api/admin/brands", { ...form, sortOrder: parseInt(form.sortOrder) }); }
      setShowForm(false); setEditing(null); setForm(emptyBrand); fetchBrands();
    } catch { alert("Failed"); }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Delete this brand?")) return;
    try { await api.delete(`/api/admin/brands/${id}`); fetchBrands(); } catch { alert("Failed"); }
  };

  const handleEdit = (b) => {
    setForm({ name:b.name, description:b.description||"", logo:b.logo||"", active:b.active, sortOrder:b.sortOrder||0 });
    setEditing(b.id); setShowForm(true);
  };

  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>Brands</h1>
        <button onClick={() => { setShowForm(!showForm); setEditing(null); setForm(emptyBrand); }} style={{ padding:"0.5rem 1rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem", fontWeight:500 }}>{showForm ? "Cancel" : "Add Brand"}</button>
      </div>

      {showForm && (
        <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, padding:"1.5rem", marginBottom:"1.5rem" }}>
          <h3 style={{ fontSize:"1.1rem", fontWeight:600, marginBottom:"1rem" }}>{editing ? "Edit Brand" : "New Brand"}</h3>
          <form onSubmit={handleSubmit}>
            <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fill,minmax(250px,1fr))", gap:"1rem", marginBottom:"1rem" }}>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Name</label><input name="name" value={form.name} onChange={e => setForm({...form,name:e.target.value})} style={inp} required /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Description</label><textarea name="description" value={form.description} onChange={e => setForm({...form,description:e.target.value})} style={{...inp,minHeight:60}} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Logo URL</label><input name="logo" value={form.logo} onChange={e => setForm({...form,logo:e.target.value})} style={inp} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Sort Order</label><input name="sortOrder" type="number" value={form.sortOrder} onChange={e => setForm({...form,sortOrder:e.target.value})} style={inp} /></div>
            </div>
            <label style={{ fontSize:"0.85rem", display:"flex", alignItems:"center", gap:"6px" }}><input name="active" type="checkbox" checked={form.active} onChange={e => setForm({...form,active:e.target.checked})} /> Active</label>
            <button type="submit" style={{ marginTop:"1rem", padding:"0.5rem 1.5rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem" }}>{editing ? "Update" : "Create"}</button>
          </form>
        </div>
      )}

      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflowX:"auto" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["Name","Description","Sort Order","Status","Actions"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {brands.length === 0 && <tr><td colSpan={5} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No brands yet</td></tr>}
            {brands.map(b => (
              <tr key={b.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                <td style={{ padding:"10px 12px", fontWeight:500 }}>{b.name}</td>
                <td style={{ padding:"10px 12px", color:"#6b7280", maxWidth:"300px", overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" }}>{b.description || "-"}</td>
                <td style={{ padding:"10px 12px" }}>{b.sortOrder}</td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:b.active?"#f0fdf4":"#fef2f2", color:b.active?"#16a34a":"#dc2626" }}>{b.active ? "Active" : "Inactive"}</span></td>
                <td style={{ padding:"10px 12px" }}>
                  <button onClick={() => handleEdit(b)} style={{ padding:"0.2rem 0.6rem", background:"#2563eb", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", marginRight:"4px", fontSize:"0.75rem" }}>Edit</button>
                  <button onClick={() => handleDelete(b.id)} style={{ padding:"0.2rem 0.6rem", background:"#dc2626", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem" }}>Delete</button>
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

export default AdminBrands;
