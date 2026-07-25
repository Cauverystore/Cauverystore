import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const AdminReviews = () => {
  const [reviews, setReviews] = useState([]);
  const [filter, setFilter] = useState("ALL");

  const fetchReviews = async () => {
    try { setReviews((await api.get("/api/admin/reviews")).data); } catch {}
  };

  useEffect(() => { fetchReviews(); }, []);

  const handleApprove = async (id) => {
    try { await api.put(`/api/admin/reviews/${id}/approve`); fetchReviews(); } catch { alert("Failed"); }
  };

  const handleReject = async (id) => {
    try { await api.put(`/api/admin/reviews/${id}/reject`); fetchReviews(); } catch { alert("Failed"); }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Delete this review?")) return;
    try { await api.delete(`/api/admin/reviews/${id}`); fetchReviews(); } catch { alert("Failed"); }
  };

  const filtered = filter === "ALL" ? reviews : filter === "APPROVED" ? reviews.filter(r => r.approved) : reviews.filter(r => !r.approved);

  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>Reviews & Moderation</h1>
        <div style={{ display:"flex", gap:"0.5rem" }}>
          {[{k:"ALL",v:"All Reviews"},{k:"PENDING",v:"Pending"},{k:"APPROVED",v:"Approved"}].map(o => (
            <button key={o.k} onClick={() => setFilter(o.k)}
              style={{ padding:"0.4rem 0.8rem", border:"1px solid #e2e8f0", borderRadius:6, background:filter===o.k?"#16a34a":"#fff", color:filter===o.k?"#fff":"#475569", cursor:"pointer", fontSize:"0.8rem" }}>{o.v}</button>
          ))}
        </div>
      </div>

      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflow:"hidden" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["Product","Customer","Rating","Review","Status","Date","Actions"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {filtered.length === 0 && <tr><td colSpan={7} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No reviews found</td></tr>}
            {filtered.map(r => (
              <tr key={r.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                <td style={{ padding:"10px 12px", fontWeight:500 }}>{r.product?.name || `#${r.product?.id}`}</td>
                <td style={{ padding:"10px 12px" }}>{r.user?.name || r.user?.email || `#${r.user?.id}`}</td>
                <td style={{ padding:"10px 12px" }}>{"★".repeat(r.rating)}{"☆".repeat(5-r.rating)}</td>
                <td style={{ padding:"10px 12px", maxWidth:"250px", overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" }}>{r.comment}</td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:r.approved?"#f0fdf4":"#fefce8", color:r.approved?"#16a34a":"#ca8a04" }}>{r.approved ? "Approved" : "Pending"}</span></td>
                <td style={{ padding:"10px 12px", color:"#6b7280", fontSize:"0.8rem" }}>{r.createdAt ? new Date(r.createdAt).toLocaleDateString() : "-"}</td>
                <td style={{ padding:"10px 12px" }}>
                  {!r.approved && <button onClick={() => handleApprove(r.id)} style={{ padding:"0.2rem 0.6rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem", marginRight:"4px" }}>Approve</button>}
                  {r.approved && <button onClick={() => handleReject(r.id)} style={{ padding:"0.2rem 0.6rem", background:"#f97316", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem", marginRight:"4px" }}>Reject</button>}
                  <button onClick={() => handleDelete(r.id)} style={{ padding:"0.2rem 0.6rem", background:"#dc2626", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem" }}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default AdminReviews;
