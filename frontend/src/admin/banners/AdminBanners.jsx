import React, { useState, useEffect } from "react";
import api from "../../api/axios";
const emptyBanner = { title:"", subtitle:"", imageUrl:"", link:"", position:"home_top", sortOrder:0, active:true };
const AdminBanners = () => {
  const [banners, setBanners] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyBanner);
  const fetchBanners = async () => { try { setBanners((await api.get("/api/admin/banners")).data); } catch {} };
  useEffect(() => { fetchBanners(); }, []);
  const handleSubmit = async (e) => {
    e.preventDefault();
    try { if (editing) { await api.put(`/api/admin/banners/${editing}`, { ...form, sortOrder: parseInt(form.sortOrder) }); } else { await api.post("/api/admin/banners", { ...form, sortOrder: parseInt(form.sortOrder) }); } setShowForm(false); setEditing(null); setForm(emptyBanner); fetchBanners(); } catch { alert("Failed"); }
  };
  const handleDelete = async (id) => { if (!window.confirm("Delete this banner?")) return; try { await api.delete(`/api/admin/banners/${id}`); fetchBanners(); } catch { alert("Failed"); } };
  const handleEdit = (b) => { setForm({ title:b.title||"", subtitle:b.subtitle||"", imageUrl:b.imageUrl||"", link:b.link||"", position:b.position||"home_top", sortOrder:b.sortOrder||0, active:b.active }); setEditing(b.id); setShowForm(true); };
  const inp = { padding:"0.5rem 0.6rem", border:"1px solid #d1d5db", borderRadius:6, fontSize:"0.85rem", width:"100%", boxSizing:"border-box" };
  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>Banners</h1>
        <button onClick={() => { setShowForm(!showForm); setEditing(null); setForm(emptyBanner); }} style={{ padding:"0.5rem 1rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem", fontWeight:500 }}>{showForm ? "Cancel" : "Add Banner"}</button>
      </div>
      {showForm && (
        <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, padding:"1.5rem", marginBottom:"1.5rem" }}>
          <h3 style={{ fontSize:"1.1rem", fontWeight:600, marginBottom:"1rem" }}>{editing ? "Edit Banner" : "New Banner"}</h3>
          <form onSubmit={handleSubmit}>
            <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fill,minmax(250px,1fr))", gap:"1rem", marginBottom:"1rem" }}>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Title</label><input value={form.title} onChange={e => setForm({...form,title:e.target.value})} style={inp} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Subtitle</label><input value={form.subtitle} onChange={e => setForm({...form,subtitle:e.target.value})} style={inp} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Image URL</label><input value={form.imageUrl} onChange={e => setForm({...form,imageUrl:e.target.value})} style={inp} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Link</label><input value={form.link} onChange={e => setForm({...form,link:e.target.value})} style={inp} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Position</label>
                <select value={form.position} onChange={e => setForm({...form,position:e.target.value})} style={inp}>
                  <option value="home_top">Home Top</option>
                  <option value="home_middle">Home Middle</option>
                  <option value="home_bottom">Home Bottom</option>
                  <option value="sidebar">Sidebar</option>
                </select>
              </div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Sort Order</label><input type="number" value={form.sortOrder} onChange={e => setForm({...form,sortOrder:e.target.value})} style={inp} /></div>
            </div>
            <label style={{ fontSize:"0.85rem", display:"flex", alignItems:"center", gap:"6px" }}><input type="checkbox" checked={form.active} onChange={e => setForm({...form,active:e.target.checked})} /> Active</label>
            <button type="submit" style={{ marginTop:"1rem", padding:"0.5rem 1.5rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem" }}>{editing ? "Update" : "Create"}</button>
          </form>
        </div>
      )}
      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflow:"hidden" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["Title","Position","Sort","Status","Actions"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {banners.length === 0 && <tr><td colSpan={5} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No banners yet</td></tr>}
            {banners.map(b => (
              <tr key={b.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                <td style={{ padding:"10px 12px", fontWeight:500 }}>{b.title || b.subtitle || "Untitled"}</td>
                <td style={{ padding:"10px 12px", color:"#6b7280" }}>{b.position}</td>
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
export default AdminBanners;