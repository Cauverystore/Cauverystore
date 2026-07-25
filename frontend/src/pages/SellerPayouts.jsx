import React, { useState, useEffect } from "react";
import api from "../api/axios";

const SellerPayouts = () => {
  const [payouts, setPayouts] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get("/api/seller/payouts")
      .then(r => setPayouts(Array.isArray(r.data) ? r.data : []))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="page-loader"><div>Loading payouts...</div></div>;

  return (
    <div style={{padding:"2rem"}}>
      <h1 style={{fontSize:"1.5rem",fontWeight:700,marginBottom:"1rem"}}>Payouts</h1>
      {payouts.length === 0 ? (
        <div style={{textAlign:"center",padding:"3rem",color:"var(--sn-text-secondary)"}}>
          <p>No payouts yet.</p>
        </div>
      ) : (
        <table className="sn-data-table" style={{width:"100%",borderCollapse:"collapse"}}>
          <thead><tr style={{background:"#f8f8f8"}}>
            <th style={{padding:"12px",textAlign:"left"}}>Date</th>
            <th style={{padding:"12px",textAlign:"left"}}>Amount</th>
            <th style={{padding:"12px",textAlign:"left"}}>Status</th>
            <th style={{padding:"12px",textAlign:"left"}}>Reference</th>
          </tr></thead>
          <tbody>
            {payouts.map(p => (
              <tr key={p.id} style={{borderBottom:"1px solid var(--sn-border)"}}>
                <td style={{padding:"12px"}}>{p.date || p.createdAt ? new Date(p.date||p.createdAt).toLocaleDateString() : "N/A"}</td>
                <td style={{padding:"12px"}}>₹{p.amount?.toLocaleString?.() || "0"}</td>
                <td style={{padding:"12px"}}><span className={`status-badge ${p.status?.toLowerCase()}`}>{p.status || "PENDING"}</span></td>
                <td style={{padding:"12px"}}>{p.reference || "N/A"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default SellerPayouts;
