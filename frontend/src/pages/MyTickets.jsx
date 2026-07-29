import React, { useState, useEffect } from "react";
import api from "../api/axios";

const MyTickets = () => {
  const [tickets, setTickets] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ subject:"", description:"", category:"general", priority:"NORMAL" });
  const fetchTickets = async () => { try { setTickets((await api.get("/api/support-tickets")).data); } catch {} };
  useEffect(() => { fetchTickets(); }, []);
  const handleSubmit = async (e) => {
    e.preventDefault();
    try { await api.post("/api/support-tickets", form); setShowForm(false); setForm({ subject:"", description:"", category:"general", priority:"NORMAL" }); fetchTickets(); } catch { alert("Failed"); }
  };
  const statusColors = { OPEN:"#f59e0b", IN_PROGRESS:"#3b82f6", RESOLVED:"#16a34a", CLOSED:"#6b7280" };
  const inp = { padding:"0.5rem 0.6rem", border:"1px solid #d1d5db", borderRadius:6, fontSize:"0.85rem", width:"100%", boxSizing:"border-box" };
  return (
    <div style={{ padding:"1.5rem", maxWidth:"900px", margin:"0 auto" }}>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>My Support Tickets</h1>
        <button onClick={() => setShowForm(!showForm)} style={{ padding:"0.5rem 1rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem" }}>{showForm ? "Cancel" : "New Ticket"}</button>
      </div>
      {showForm && (
        <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, padding:"1.5rem", marginBottom:"1.5rem" }}>
          <h3 style={{ fontSize:"1.1rem", fontWeight:600, marginBottom:"1rem" }}>Create Support Ticket</h3>
          <form onSubmit={handleSubmit}>
            <div style={{ display:"grid", gap:"1rem", marginBottom:"1rem" }}>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Subject</label><input value={form.subject} onChange={e => setForm({...form,subject:e.target.value})} style={inp} required /></div>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Description</label><textarea value={form.description} onChange={e => setForm({...form,description:e.target.value})} style={{...inp,minHeight:100}} required /></div>
              <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:"1rem" }}>
                <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Category</label>
                  <select value={form.category} onChange={e => setForm({...form,category:e.target.value})} style={inp}>
                    <option value="general">General</option>
                    <option value="order">Order Issue</option>
                    <option value="payment">Payment</option>
                    <option value="shipping">Shipping</option>
                    <option value="return">Return</option>
                    <option value="account">Account</option>
                  </select>
                </div>
                <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>Priority</label>
                  <select value={form.priority} onChange={e => setForm({...form,priority:e.target.value})} style={inp}>
                    <option value="LOW">Low</option>
                    <option value="NORMAL">Normal</option>
                    <option value="HIGH">High</option>
                  </select>
                </div>
              </div>
            </div>
            <button type="submit" style={{ padding:"0.5rem 1.5rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem" }}>Submit</button>
          </form>
        </div>
      )}
      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflowX:"auto" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["ID","Subject","Category","Priority","Status","Created"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {tickets.length === 0 && <tr><td colSpan={6} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No tickets yet</td></tr>}
            {tickets.map(t => (
              <tr key={t.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                <td style={{ padding:"10px 12px", fontWeight:500 }}>#{t.id}</td>
                <td style={{ padding:"10px 12px", fontWeight:500 }}>{t.subject}</td>
                <td style={{ padding:"10px 12px", color:"#6b7280" }}>{t.category}</td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:t.priority==="HIGH"?"#fef2f2":"#f0fdf4", color:t.priority==="HIGH"?"#dc2626":"#16a34a" }}>{t.priority}</span></td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:"#fefce8", color:statusColors[t.status]||"#6b7280" }}>{t.status}</span></td>
                <td style={{ padding:"10px 12px", color:"#6b7280", fontSize:"0.8rem" }}>{t.createdAt ? new Date(t.createdAt).toLocaleDateString() : "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
export default MyTickets;