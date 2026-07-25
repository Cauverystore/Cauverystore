import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const AdminNotifications = () => {
  const [notifications, setNotifications] = useState([]);
  const [type, setType] = useState("ALL");

  useEffect(() => {
    const fetch = async () => {
      try {
        const res = await api.get("/api/admin/notifications");
        setNotifications(Array.isArray(res.data) ? res.data : []);
      } catch {}
    };
    fetch();
  }, []);

  const filtered = type === "ALL" ? notifications : notifications.filter(n => n.type === type);

  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>Notifications</h1>
        <div style={{ display:"flex", gap:"0.5rem" }}>
          {[{k:"ALL",v:"All"},{k:"ORDER",v:"Orders"},{k:"MARKETING",v:"Marketing"},{k:"SYSTEM",v:"System"}].map(o => (
            <button key={o.k} onClick={() => setType(o.k)}
              style={{ padding:"0.4rem 0.8rem", border:"1px solid #e2e8f0", borderRadius:6, background:type===o.k?"#16a34a":"#fff", color:type===o.k?"#fff":"#475569", cursor:"pointer", fontSize:"0.8rem" }}>{o.v}</button>
          ))}
        </div>
      </div>

      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflow:"hidden" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["Type","Title","Message","User","Date","Status"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {filtered.length === 0 && <tr><td colSpan={6} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No notifications</td></tr>}
            {filtered.map(n => (
              <tr key={n.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.7rem", fontWeight:600, background:n.type==="ORDER"?"#eff6ff":n.type==="MARKETING"?"#fefce8":"#f3e8ff", color:n.type==="ORDER"?"#2563eb":n.type==="MARKETING"?"#ca8a04":"#9333ea" }}>{n.type}</span></td>
                <td style={{ padding:"10px 12px", fontWeight:500 }}>{n.title}</td>
                <td style={{ padding:"10px 12px", color:"#475569", maxWidth:"300px", overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" }}>{n.message}</td>
                <td style={{ padding:"10px 12px", color:"#6b7280" }}>{n.userId || "-"}</td>
                <td style={{ padding:"10px 12px", color:"#6b7280", fontSize:"0.8rem" }}>{n.createdAt ? new Date(n.createdAt).toLocaleString() : "-"}</td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:"#f0fdf4", color:"#16a34a" }}>Sent</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default AdminNotifications;
