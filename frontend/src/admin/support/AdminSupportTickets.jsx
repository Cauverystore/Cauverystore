import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const AdminSupportTickets = () => {
  const [tickets, setTickets] = useState([]);
  const [filter, setFilter] = useState("ALL");
  const fetchTickets = async () => {
    try { const url = filter === "ALL" ? "/api/admin/support-tickets" : `/api/admin/support-tickets/status/${filter}`; setTickets((await api.get(url)).data); } catch {}
  };
  useEffect(() => { fetchTickets(); }, [filter]);
  const handleStatus = async (id, status) => { try { await api.put(`/api/admin/support-tickets/${id}/status`, { status }); fetchTickets(); } catch { alert("Failed"); } };
  const handleAssign = async (id) => { try { await api.put(`/api/admin/support-tickets/${id}/assign`); fetchTickets(); } catch { alert("Failed"); } };
  const statusColors = { OPEN:"#f59e0b", IN_PROGRESS:"#3b82f6", RESOLVED:"#16a34a", CLOSED:"#6b7280" };
  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>Support Tickets</h1>
        <div style={{ display:"flex", gap:"0.5rem" }}>
          {["ALL","OPEN","IN_PROGRESS","RESOLVED","CLOSED"].map(s => (
            <button key={s} onClick={() => setFilter(s)} style={{ padding:"0.3rem 0.8rem", borderRadius:4, border:"1px solid #d1d5db", cursor:"pointer", fontSize:"0.8rem", fontWeight: filter===s ? 600 : 400, background: filter===s ? "#16a34a" : "#fff", color: filter===s ? "#fff" : "#374151" }}>{s.replace("_"," ")}</button>
          ))}
        </div>
      </div>
      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflow:"hidden" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["ID","Subject","Category","Priority","Status","Assigned To","Actions"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {tickets.length === 0 && <tr><td colSpan={7} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No tickets found</td></tr>}
            {tickets.map(t => (
              <tr key={t.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                <td style={{ padding:"10px 12px", fontWeight:500 }}>#{t.id}</td>
                <td style={{ padding:"10px 12px", maxWidth:"250px", overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" }}>{t.subject}</td>
                <td style={{ padding:"10px 12px", color:"#6b7280" }}>{t.category || "-"}</td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:t.priority==="HIGH"?"#fef2f2":"#f0fdf4", color:t.priority==="HIGH"?"#dc2626":"#16a34a" }}>{t.priority}</span></td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:"#fefce8", color:statusColors[t.status]||"#6b7280" }}>{t.status}</span></td>
                <td style={{ padding:"10px 12px", color:"#6b7280" }}>{t.assignedTo ? `Admin #${t.assignedTo.id}` : "Unassigned"}</td>
                <td style={{ padding:"10px 12px" }}>
                  {t.status === "OPEN" && <button onClick={() => handleStatus(t.id, "IN_PROGRESS")} style={{ padding:"0.2rem 0.6rem", background:"#3b82f6", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", marginRight:"4px", fontSize:"0.75rem" }}>Accept</button>}
                  {t.status === "IN_PROGRESS" && <button onClick={() => handleStatus(t.id, "RESOLVED")} style={{ padding:"0.2rem 0.6rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", marginRight:"4px", fontSize:"0.75rem" }}>Resolve</button>}
                  {!t.assignedTo && <button onClick={() => handleAssign(t.id)} style={{ padding:"0.2rem 0.6rem", background:"#8b5cf6", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", marginRight:"4px", fontSize:"0.75rem" }}>Assign</button>}
                  {(t.status === "RESOLVED" || t.status === "IN_PROGRESS") && <button onClick={() => handleStatus(t.id, "CLOSED")} style={{ padding:"0.2rem 0.6rem", background:"#6b7280", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", marginRight:"4px", fontSize:"0.75rem" }}>Close</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
export default AdminSupportTickets;