import React, { useState, useEffect } from "react";
import api from "../../api/axios";
import Pagination from "../../components/Pagination";

const AdminCustomers = () => {
  const [customers, setCustomers] = useState([]);
  const [filtered, setFiltered] = useState([]);
  const [search, setSearch] = useState("");
  const [segment, setSegment] = useState("CUSTOMER");
  const [page, setPage] = useState(1);
  const pageSize = 15;

  useEffect(() => {
    api.get("/api/admin/users").then(r => {
      const all = Array.isArray(r.data) ? r.data : [];
      setCustomers(all);
    }).catch(() => {});
  }, []);

  useEffect(() => {
    let list = [...customers];
    if (search) list = list.filter(u => (u.name||u.fullName||u.username||"").toLowerCase().includes(search.toLowerCase()) || (u.email||"").toLowerCase().includes(search.toLowerCase()));
    if (segment === "ACTIVE") list = list.filter(u => !u.isBlocked && u.status !== "BLOCKED");
    else if (segment === "BLOCKED") list = list.filter(u => u.isBlocked || u.status === "BLOCKED");
    else if (segment === "ADMIN") list = list.filter(u => u.role === "ADMIN");
    else if (segment === "SELLER") list = list.filter(u => u.role === "SELLER");
    else if (segment === "CUSTOMER") list = list.filter(u => u.role === "CUSTOMER");
    setFiltered(list);
    setPage(1);
  }, [customers, search, segment]);

  const handleBlock = async (id, isBlocked) => {
    try {
      if (isBlocked) await api.put(`/api/admin/users/${id}/unblock`);
      else await api.put(`/api/admin/users/${id}/block`);
      const r = await api.get("/api/admin/users");
      setCustomers(Array.isArray(r.data) ? r.data : []);
    } catch { alert("Failed"); }
  };

  const handleRole = async (id, role) => {
    try { await api.put(`/api/admin/users/${id}/role?role=${role}`);
      const r = await api.get("/api/admin/users");
      setCustomers(Array.isArray(r.data) ? r.data : []);
    } catch { alert("Failed"); }
  };

  const totalPages = Math.ceil(filtered.length / pageSize);
  const pageItems = filtered.slice((page - 1) * pageSize, page * pageSize);

  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>Customers</h1>
        <div style={{ display:"flex", gap:"0.5rem" }}>
          <input placeholder="Search by name or email..." value={search} onChange={e => setSearch(e.target.value)}
            style={{ padding:"0.4rem 0.75rem", border:"1px solid #d1d5db", borderRadius:6, fontSize:"0.85rem", width:"220px" }} />
          <select value={segment} onChange={e => setSegment(e.target.value)} style={{ padding:"0.4rem 0.5rem", border:"1px solid #d1d5db", borderRadius:6, fontSize:"0.85rem" }}>
            <option value="ALL">All Users</option><option value="CUSTOMER">Customers</option><option value="ADMIN">Admins</option><option value="SELLER">Sellers</option><option value="ACTIVE">Active</option><option value="BLOCKED">Blocked</option>
          </select>
        </div>
      </div>

      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflowX:"auto" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["Name","Email","Role","Orders","Status","Joined","Actions"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {pageItems.length === 0 && <tr><td colSpan={7} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No customers found</td></tr>}
            {pageItems.map(u => (
              <tr key={u.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                <td style={{ padding:"10px 12px", fontWeight:500 }}>{u.name || u.fullName || u.username}</td>
                <td style={{ padding:"10px 12px", color:"#475569" }}>{u.email}</td>
                <td style={{ padding:"10px 12px" }}>
                  <select value={u.role} onChange={e => handleRole(u.id, e.target.value)} style={{ padding:"0.2rem 0.3rem", fontSize:"0.75rem", borderRadius:4, border:"1px solid #d1d5db" }}>
                    <option value="CUSTOMER">Customer</option><option value="ADMIN">Admin</option><option value="SELLER">Seller</option><option value="EXECUTIVE">Executive</option>
                  </select>
                </td>
                <td style={{ padding:"10px 12px" }}>{u.orders?.length || 0}</td>
                <td style={{ padding:"10px 12px" }}><span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:u.isBlocked||u.status==="BLOCKED"?"#fef2f2":"#f0fdf4", color:u.isBlocked||u.status==="BLOCKED"?"#dc2626":"#16a34a" }}>{u.isBlocked||u.status==="BLOCKED"?"Blocked":"Active"}</span></td>
                <td style={{ padding:"10px 12px", color:"#6b7280", fontSize:"0.8rem" }}>{u.createdAt ? new Date(u.createdAt).toLocaleDateString() : "-"}</td>
                <td style={{ padding:"10px 12px" }}>
                  <button onClick={() => handleBlock(u.id, u.isBlocked)} style={{ padding:"0.2rem 0.6rem", background:u.isBlocked?"#16a34a":"#f97316", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem", marginRight:"4px" }}>{u.isBlocked ? "Unblock" : "Block"}</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={page} totalPages={totalPages} onPage={setPage} />
    </div>
  );
};

export default AdminCustomers;
