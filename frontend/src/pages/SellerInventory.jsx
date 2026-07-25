import React, { useState, useEffect } from "react";
import api from "../api/axios";

const SellerInventory = () => {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get("/api/seller/products")
      .then(r => setItems(Array.isArray(r.data?.content) ? r.data.content : Array.isArray(r.data) ? r.data : []))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="page-loader"><div>Loading inventory...</div></div>;

  const lowStock = items.filter(i => (i.stock ?? i.quantity ?? 0) < 5);

  return (
    <div style={{padding:"2rem"}}>
      <h1 style={{fontSize:"1.5rem",fontWeight:700,marginBottom:"1rem"}}>Inventory</h1>
      {lowStock.length > 0 && (
        <div style={{background:"#fff3cd",padding:"1rem",borderRadius:"8px",marginBottom:"1rem",color:"#856404"}}>
          <strong>Low Stock Alert:</strong> {lowStock.length} product(s) have less than 5 units
        </div>
      )}
      <table className="sn-data-table" style={{width:"100%",borderCollapse:"collapse"}}>
        <thead><tr style={{background:"#f8f8f8"}}>
          <th style={{padding:"12px",textAlign:"left"}}>Product</th>
          <th style={{padding:"12px",textAlign:"left"}}>SKU</th>
          <th style={{padding:"12px",textAlign:"left"}}>Stock</th>
          <th style={{padding:"12px",textAlign:"left"}}>Status</th>
        </tr></thead>
        <tbody>
          {items.map(p => (
            <tr key={p.id} style={{borderBottom:"1px solid var(--sn-border)",background:(p.stock??0)<5?"#fff8f0":"transparent"}}>
              <td style={{padding:"12px"}}>{p.name}</td>
              <td style={{padding:"12px"}}>{p.sku || p.productCode || "N/A"}</td>
              <td style={{padding:"12px",fontWeight:(p.stock??0)<5?700:400,color:(p.stock??0)<5?"var(--sn-accent)":"inherit"}}>{p.stock ?? 0}</td>
              <td style={{padding:"12px"}}><span className={`status-badge ${p.active?"active":"inactive"}`}>{p.active ? "Active" : "Inactive"}</span></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default SellerInventory;
