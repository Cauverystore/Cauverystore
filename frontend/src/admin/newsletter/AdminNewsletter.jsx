import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const AdminNewsletter = () => {
  const [subs, setSubs] = useState([]);
  const fetch = async () => { try { setSubs((await api.get("/api/admin/newsletter")).data); } catch {} };
  useEffect(() => { fetch(); }, []);
  const handleDelete = async (id, email) => { if (!window.confirm(`Unsubscribe ${email}?`)) return; try { await api.delete(`/api/admin/newsletter/${id}`); fetch(); } catch { alert("Failed"); } };
  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>Newsletter Subscribers</h1>
        <span style={{ padding:"0.3rem 0.8rem", background:"#f0fdf4", color:"#16a34a", borderRadius:6, fontWeight:600, fontSize:"0.85rem" }}>{subs.length} subscribers</span>
      </div>
      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflow:"hidden" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["Email","Subscribed At","Active","Actions"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {subs.length === 0 && <tr><td colSpan={4} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No subscribers yet</td></tr>}
            {subs.map(s => (
              <tr key={s.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                <td style={{ padding:"10px 12px", fontWeight:500 }}>{s.email}</td>
                <td style={{ padding:"10px 12px", color:"#6b7280", fontSize:"0.8rem" }}>{s.subscribedAt ? new Date(s.subscribedAt).toLocaleDateString() : "-"}</td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:s.active?"#f0fdf4":"#fef2f2", color:s.active?"#16a34a":"#dc2626" }}>{s.active ? "Active" : "Inactive"}</span></td>
                <td style={{ padding:"10px 12px" }}><button onClick={() => handleDelete(s.id, s.email)} style={{ padding:"0.2rem 0.6rem", background:"#dc2626", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem" }}>Remove</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
export default AdminNewsletter;