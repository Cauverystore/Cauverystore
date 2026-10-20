import React, { useState, useEffect } from "react";
import api from "../../api/axios";
import { confirmAction, promptText, showMessage } from "../../components/ConfirmDialog";

// Sellers' applications to sell a brand. Approving one lets them list a protected brand.
const BrandApplications = ({ onChanged }) => {
  const [apps, setApps] = useState([]);
  const load = async () => { try { setApps((await api.get("/api/admin/seller-brands", { params: { openOnly: true } })).data || []); } catch {} };
  useEffect(() => { load(); }, []);

  const openFile = async (url) => {
    const tab = window.open("", "_blank");
    try {
      const res = await api.get(url, { responseType: "blob" });
      const blobUrl = URL.createObjectURL(res.data);
      if (tab) tab.location.href = blobUrl; else window.location.href = blobUrl;
    } catch { if (tab) tab.close(); showMessage("Could not open the file"); }
  };

  const act = async (a, action) => {
    let reason = null;
    if (action === "REJECTED") {
      reason = await promptText(`Why is ${a.storeName}'s application for ${a.brandName} rejected? The seller sees this.`, "", { confirmLabel: "Reject", danger: true });
      if (reason === null) return;
      if (!reason.trim()) { showMessage("A reason is needed."); return; }
    } else if (action === "APPROVED") {
      if (!(await confirmAction(`Approve ${a.storeName} to sell ${a.brandName}?

If the brand is not in your list yet it will be added.`, { confirmLabel: "Approve" }))) return;
    }
    try { await api.post(`/api/admin/seller-brands/${a.id}/review`, { action, reason }); load(); onChanged && onChanged(); }
    catch (err) { showMessage(err.response?.data?.error || "Could not save"); }
  };

  if (apps.length === 0) return null;
  const btn = (bg) => ({ padding:"0.25rem 0.6rem", background:bg, color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem", marginRight:4 });
  return (
    <div style={{ background:"#fff", border:"1px solid #fde68a", borderRadius:10, padding:"1rem 1.25rem", marginBottom:"1.5rem" }}>
      <h3 style={{ fontSize:"1rem", fontWeight:700, margin:"0 0 0.75rem" }}>Seller brand applications ({apps.length})</h3>
      <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
        <thead><tr>{["Store","Brand","Claim","Proof","Status",""].map(h => <th key={h} style={{ textAlign:"left", padding:"6px 8px", fontSize:"0.75rem", color:"#6b7280" }}>{h}</th>)}</tr></thead>
        <tbody>
          {apps.map(a => (
            <tr key={a.id} style={{ borderTop:"1px solid #f3f4f6" }}>
              <td style={{ padding:"8px" }}>{a.storeName}</td>
              <td style={{ padding:"8px", fontWeight:600 }}>{a.brandName}</td>
              <td style={{ padding:"8px" }}>{a.relationshipLabel}{a.notes && <div style={{ color:"#6b7280", fontSize:"0.78rem" }}>{a.notes}</div>}</td>
              <td style={{ padding:"8px" }}>
                {a.trademarkNumber && <div>TM {a.trademarkNumber}</div>}
                {a.documentUrl && <button type="button" onClick={() => openFile(a.documentUrl)} style={{ background:"none", border:"none", color:"#2563eb", cursor:"pointer", padding:0, fontSize:"0.8rem" }}>View document</button>}
              </td>
              <td style={{ padding:"8px" }}>{a.status === "UNDER_REVIEW" ? "Under review" : "Submitted"}</td>
              <td style={{ padding:"8px", whiteSpace:"nowrap" }}>
                {a.status === "SUBMITTED" && <button type="button" style={btn("#6b7280")} onClick={() => act(a, "UNDER_REVIEW")}>Start review</button>}
                <button type="button" style={btn("#16a34a")} onClick={() => act(a, "APPROVED")}>Approve</button>
                <button type="button" style={btn("#dc2626")} onClick={() => act(a, "REJECTED")}>Reject</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

const emptyBrand = { name:"", description:"", logo:"", active:true, sortOrder:0, gated:false };

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
    } catch { showMessage("Failed"); }
  };

  const handleDelete = async (id) => {
    if (!await confirmAction("Delete this brand?")) return;
    try { await api.delete(`/api/admin/brands/${id}`); fetchBrands(); } catch { showMessage("Failed"); }
  };

  const handleEdit = (b) => {
    setForm({ name:b.name, description:b.description||"", logo:b.logo||"", active:b.active, sortOrder:b.sortOrder||0, gated:!!b.gated });
    setEditing(b.id); setShowForm(true);
  };

  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>Brands</h1>
        <button onClick={() => { setShowForm(!showForm); setEditing(null); setForm(emptyBrand); }} style={{ padding:"0.5rem 1rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem", fontWeight:500 }}>{showForm ? "Cancel" : "Add Brand"}</button>
      </div>

      <BrandApplications onChanged={fetchBrands} />

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
            <label style={{ fontSize:"0.85rem", display:"flex", alignItems:"center", gap:"6px", marginTop:"0.5rem" }}><input name="gated" type="checkbox" checked={!!form.gated} onChange={e => setForm({...form,gated:e.target.checked})} /> Protected: sellers need an approved brand application to list it</label>
            <button type="submit" style={{ marginTop:"1rem", padding:"0.5rem 1.5rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem" }}>{editing ? "Update" : "Create"}</button>
          </form>
        </div>
      )}

      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflowX:"auto" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["Name","Description","Sort Order","Status","Protected","Actions"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {brands.length === 0 && <tr><td colSpan={6} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No brands yet</td></tr>}
            {brands.map(b => (
              <tr key={b.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                <td style={{ padding:"10px 12px", fontWeight:500 }}>{b.name}</td>
                <td style={{ padding:"10px 12px", color:"#6b7280", maxWidth:"300px", overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" }}>{b.description || "-"}</td>
                <td style={{ padding:"10px 12px" }}>{b.sortOrder}</td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:b.active?"#f0fdf4":"#fef2f2", color:b.active?"#16a34a":"#dc2626" }}>{b.active ? "Active" : "Inactive"}</span></td>
                <td style={{ padding:"10px 12px", color:b.gated?"#b45309":"#94a3b8" }}>{b.gated ? "Yes" : "No"}</td>
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
