import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const emptyZone = { name:"", regions:"", charge:"", freeShippingThreshold:"", estimatedMinDays:"", estimatedMaxDays:"", courierPartner:"", codAvailable:true, active:true };

const AdminShipping = () => {
  const [zones, setZones] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyZone);

  const fetchZones = async () => {
    try { setZones((await api.get("/api/admin/shipping")).data); } catch {}
  };

  useEffect(() => { fetchZones(); }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    const payload = { ...form, charge: parseFloat(form.charge) || 0, freeShippingThreshold: form.freeShippingThreshold ? parseFloat(form.freeShippingThreshold) : null, estimatedMinDays: parseInt(form.estimatedMinDays) || null, estimatedMaxDays: parseInt(form.estimatedMaxDays) || null };
    try {
      if (editing) { await api.put(`/api/admin/shipping/${editing}`, payload); }
      else { await api.post("/api/admin/shipping", payload); }
      setShowForm(false); setEditing(null); setForm(emptyZone); fetchZones();
    } catch { alert("Failed"); }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Delete this shipping zone?")) return;
    try { await api.delete(`/api/admin/shipping/${id}`); fetchZones(); } catch { alert("Failed"); }
  };

  const handleEdit = (z) => {
    setForm({ name:z.name, regions:z.regions||"", charge:z.charge, freeShippingThreshold:z.freeShippingThreshold||"", estimatedMinDays:z.estimatedMinDays||"", estimatedMaxDays:z.estimatedMaxDays||"", courierPartner:z.courierPartner||"", codAvailable:z.codAvailable, active:z.active });
    setEditing(z.id); setShowForm(true);
  };

  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>Shipping Configuration</h1>
        <button onClick={() => { setShowForm(!showForm); setEditing(null); setForm(emptyZone); }} style={{ padding:"0.5rem 1rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem", fontWeight:500 }}>{showForm ? "Cancel" : "Add Zone"}</button>
      </div>

      {showForm && (
        <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, padding:"1.5rem", marginBottom:"1.5rem" }}>
          <h3 style={{ fontSize:"1.1rem", fontWeight:600, marginBottom:"1rem" }}>{editing ? "Edit Zone" : "New Shipping Zone"}</h3>
          <form onSubmit={handleSubmit}>
            <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fill,minmax(200px,1fr))", gap:"1rem", marginBottom:"1rem" }}>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Zone Name</label><input value={form.name} onChange={e => setForm({...form,name:e.target.value})} style={inp} required /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Regions (comma-separated)</label><input value={form.regions} onChange={e => setForm({...form,regions:e.target.value})} style={inp} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Shipping Charge</label><input type="number" step="0.01" value={form.charge} onChange={e => setForm({...form,charge:e.target.value})} style={inp} required /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Free Shipping Threshold</label><input type="number" step="0.01" value={form.freeShippingThreshold} onChange={e => setForm({...form,freeShippingThreshold:e.target.value})} style={inp} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Est. Min Days</label><input type="number" value={form.estimatedMinDays} onChange={e => setForm({...form,estimatedMinDays:e.target.value})} style={inp} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Est. Max Days</label><input type="number" value={form.estimatedMaxDays} onChange={e => setForm({...form,estimatedMaxDays:e.target.value})} style={inp} /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Courier Partner</label><input value={form.courierPartner} onChange={e => setForm({...form,courierPartner:e.target.value})} style={inp} /></div>
            </div>
            <div style={{ display:"flex", gap:"1rem", alignItems:"center" }}>
              <label style={{ fontSize:"0.85rem", display:"flex", alignItems:"center", gap:"6px" }}><input type="checkbox" checked={form.codAvailable} onChange={e => setForm({...form,codAvailable:e.target.checked})} /> COD Available</label>
              <label style={{ fontSize:"0.85rem", display:"flex", alignItems:"center", gap:"6px" }}><input type="checkbox" checked={form.active} onChange={e => setForm({...form,active:e.target.checked})} /> Active</label>
            </div>
            <button type="submit" style={{ marginTop:"1rem", padding:"0.5rem 1.5rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem" }}>{editing ? "Update" : "Create"}</button>
          </form>
        </div>
      )}

      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflow:"hidden" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["Zone","Regions","Charge","Free Threshold","Est. Delivery","Courier","COD","Status","Actions"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {zones.length === 0 && <tr><td colSpan={9} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No shipping zones configured</td></tr>}
            {zones.map(z => (
              <tr key={z.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                <td style={{ padding:"10px 12px", fontWeight:500 }}>{z.name}</td>
                <td style={{ padding:"10px 12px", color:"#6b7280", fontSize:"0.8rem" }}>{z.regions?.substring(0,50)}{z.regions?.length > 50 ? "..." : ""}</td>
                <td style={{ padding:"10px 12px" }}>₹{z.charge}</td>
                <td style={{ padding:"10px 12px" }}>{z.freeShippingThreshold ? `₹${z.freeShippingThreshold}` : "-"}</td>
                <td style={{ padding:"10px 12px" }}>{z.estimatedMinDays && z.estimatedMaxDays ? `${z.estimatedMinDays}-${z.estimatedMaxDays} days` : "-"}</td>
                <td style={{ padding:"10px 12px", color:"#6b7280" }}>{z.courierPartner || "-"}</td>
                <td style={{ padding:"10px 12px" }}><span style={{ color:z.codAvailable?"#16a34a":"#dc2626" }}>{z.codAvailable ? "Yes" : "No"}</span></td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:z.active?"#f0fdf4":"#fef2f2", color:z.active?"#16a34a":"#dc2626" }}>{z.active ? "Active" : "Inactive"}</span></td>
                <td style={{ padding:"10px 12px" }}>
                  <button onClick={() => handleEdit(z)} style={{ padding:"0.2rem 0.6rem", background:"#2563eb", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", marginRight:"4px", fontSize:"0.75rem" }}>Edit</button>
                  <button onClick={() => handleDelete(z.id)} style={{ padding:"0.2rem 0.6rem", background:"#dc2626", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem" }}>Delete</button>
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

export default AdminShipping;
