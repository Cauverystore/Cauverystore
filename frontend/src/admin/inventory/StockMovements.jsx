import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const StockMovements = () => {
  const [movements, setMovements] = useState([]);
  const [filter, setFilter] = useState("ALL");
  const fetch = async () => {
    try { const url = filter === "ALL" ? "/api/stock-movements" : `/api/stock-movements/type/${filter}`; setMovements((await api.get(url)).data); } catch {}
  };
  useEffect(() => { fetch(); }, [filter]);
  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>Stock Movements</h1>
        <div style={{ display:"flex", gap:"0.5rem" }}>
          {["ALL","IN","OUT","ADJUSTMENT","RETURN"].map(t => (
            <button key={t} onClick={() => setFilter(t)} style={{ padding:"0.3rem 0.8rem", borderRadius:4, border:"1px solid #d1d5db", cursor:"pointer", fontSize:"0.8rem", fontWeight: filter===t ? 600 : 400, background: filter===t ? "#16a34a" : "#fff", color: filter===t ? "#fff" : "#374151" }}>{t}</button>
          ))}
        </div>
      </div>
      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflow:"hidden" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["ID","Product","Type","Qty","Balance","Reference","Date"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {movements.length === 0 && <tr><td colSpan={7} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No movements recorded</td></tr>}
            {movements.map(m => (
              <tr key={m.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                <td style={{ padding:"10px 12px", fontWeight:500 }}>#{m.id}</td>
                <td style={{ padding:"10px 12px" }}>{m.product?.name || `#${m.product?.id || "N/A"}`}</td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:m.type==="IN"?"#f0fdf4":m.type==="OUT"?"#fef2f2":"#fefce8", color:m.type==="IN"?"#16a34a":m.type==="OUT"?"#dc2626":"#f59e0b" }}>{m.type}</span></td>
                <td style={{ padding:"10px 12px", fontWeight:600 }}>{m.type === "OUT" ? "-" : "+"}{m.quantity}</td>
                <td style={{ padding:"10px 12px" }}>{m.balanceAfter}</td>
                <td style={{ padding:"10px 12px", color:"#6b7280", fontSize:"0.8rem" }}>{m.referenceType ? `${m.referenceType}#${m.referenceId}` : "-"}</td>
                <td style={{ padding:"10px 12px", color:"#6b7280", fontSize:"0.8rem" }}>{m.createdAt ? new Date(m.createdAt).toLocaleString() : "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
export default StockMovements;