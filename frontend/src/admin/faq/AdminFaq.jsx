import React, { useState, useEffect } from "react";
import api from "../../api/axios";
const emptyFaq = { question:"", answer:"", category:"general", sortOrder:0, active:true };
const AdminFaq = () => {
  const [faqs, setFaqs] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyFaq);
  const fetchFaqs = async () => { try { setFaqs((await api.get("/api/admin/faqs")).data); } catch {} };
  useEffect(() => { fetchFaqs(); }, []);
  const handleSubmit = async (e) => {
    e.preventDefault();
    try { if (editing) { await api.put(`/api/admin/faqs/${editing}`, { ...form, sortOrder: parseInt(form.sortOrder) }); } else { await api.post("/api/admin/faqs", { ...form, sortOrder: parseInt(form.sortOrder) }); } setShowForm(false); setEditing(null); setForm(emptyFaq); fetchFaqs(); } catch { alert("Failed"); }
  };
  const handleDelete = async (id) => { if (!window.confirm("Delete this FAQ?")) return; try { await api.delete(`/api/admin/faqs/${id}`); fetchFaqs(); } catch { alert("Failed"); } };
  const handleEdit = (f) => { setForm({ question:f.question||"", answer:f.answer||"", category:f.category||"general", sortOrder:f.sortOrder||0, active:f.active }); setEditing(f.id); setShowForm(true); };
  const inp = { padding:"0.5rem 0.6rem", border:"1px solid #d1d5db", borderRadius:6, fontSize:"0.85rem", width:"100%", boxSizing:"border-box" };
  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>FAQs</h1>
        <button onClick={() => { setShowForm(!showForm); setEditing(null); setForm(emptyFaq); }} style={{ padding:"0.5rem 1rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem", fontWeight:500 }}>{showForm ? "Cancel" : "Add FAQ"}</button>
      </div>
      {showForm && (
        <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, padding:"1.5rem", marginBottom:"1.5rem" }}>
          <h3 style={{ fontSize:"1.1rem", fontWeight:600, marginBottom:"1rem" }}>{editing ? "Edit FAQ" : "New FAQ"}</h3>
          <form onSubmit={handleSubmit}>
            <div style={{ display:"grid", gap:"1rem", marginBottom:"1rem" }}>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Question</label><input value={form.question} onChange={e => setForm({...form,question:e.target.value})} style={inp} required /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Answer</label><textarea value={form.answer} onChange={e => setForm({...form,answer:e.target.value})} style={{...inp, minHeight:100}} required /></div>
              <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:"1rem" }}>
                <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Category</label>
                  <select value={form.category} onChange={e => setForm({...form,category:e.target.value})} style={inp}>
                    <option value="general">General</option>
                    <option value="orders">Orders</option>
                    <option value="shipping">Shipping</option>
                    <option value="returns">Returns</option>
                    <option value="payment">Payment</option>
                    <option value="account">Account</option>
                  </select>
                </div>
                <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Sort Order</label><input type="number" value={form.sortOrder} onChange={e => setForm({...form,sortOrder:e.target.value})} style={inp} /></div>
              </div>
            </div>
            <label style={{ fontSize:"0.85rem", display:"flex", alignItems:"center", gap:"6px" }}><input type="checkbox" checked={form.active} onChange={e => setForm({...form,active:e.target.checked})} /> Active</label>
            <button type="submit" style={{ marginTop:"1rem", padding:"0.5rem 1.5rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem" }}>{editing ? "Update" : "Create"}</button>
          </form>
        </div>
      )}
      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflow:"hidden" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["Question","Category","Status","Actions"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {faqs.length === 0 && <tr><td colSpan={4} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No FAQs yet</td></tr>}
            {faqs.map(f => (
              <tr key={f.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                <td style={{ padding:"10px 12px", fontWeight:500, maxWidth:"300px", overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" }}>{f.question}</td>
                <td style={{ padding:"10px 12px", color:"#6b7280" }}>{f.category}</td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:f.active?"#f0fdf4":"#fef2f2", color:f.active?"#16a34a":"#dc2626" }}>{f.active ? "Active" : "Inactive"}</span></td>
                <td style={{ padding:"10px 12px" }}>
                  <button onClick={() => handleEdit(f)} style={{ padding:"0.2rem 0.6rem", background:"#2563eb", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", marginRight:"4px", fontSize:"0.75rem" }}>Edit</button>
                  <button onClick={() => handleDelete(f.id)} style={{ padding:"0.2rem 0.6rem", background:"#dc2626", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem" }}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
export default AdminFaq;