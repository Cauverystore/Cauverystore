import React, { useState, useEffect } from "react";
import api from "../../api/axios";
import Pagination from "../../components/Pagination";

const AdminAddresses = () => {
  const [addresses, setAddresses] = useState([]);
  const [filtered, setFiltered] = useState([]);
  const [search, setSearch] = useState("");
  const [segment, setSegment] = useState("ALL");
  const [page, setPage] = useState(1);
  const [msg, setMsg] = useState("");
  const pageSize = 15;

  const fetchAddresses = () => {
    api.get("/api/admin/addresses").then(r => {
      const all = Array.isArray(r.data) ? r.data : [];
      setAddresses(all);
    }).catch(() => {});
  };

  useEffect(() => {
    fetchAddresses();
  }, []);

  useEffect(() => {
    let list = [...addresses];
    if (search) {
      const q = search.toLowerCase();
      list = list.filter(a =>
        (a.fullName || "").toLowerCase().includes(q) ||
        (a.userName || "").toLowerCase().includes(q) ||
        (a.userEmail || "").toLowerCase().includes(q) ||
        (a.city || "").toLowerCase().includes(q) ||
        (a.pincode || "").includes(q)
      );
    }
    if (segment === "ACTIVE") list = list.filter(a => a.active);
    else if (segment === "INACTIVE") list = list.filter(a => !a.active);
    else if (segment === "USED") list = list.filter(a => (a.usageCount || 0) > 0);
    setFiltered(list);
    setPage(1);
  }, [addresses, search, segment]);

  const handleRestore = async (id) => {
    try {
      await api.post(`/api/admin/addresses/${id}/restore`);
      setMsg("Address restored");
      setTimeout(() => setMsg(""), 2500);
      fetchAddresses();
    } catch { setMsg("Failed to restore address"); }
  };

  const totalPages = Math.ceil(filtered.length / pageSize);
  const pageItems = filtered.slice((page - 1) * pageSize, page * pageSize);

  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>Addresses</h1>
        <div style={{ display:"flex", gap:"0.5rem" }}>
          <input placeholder="Search name, email, city, pincode..." value={search} onChange={e => setSearch(e.target.value)}
            style={{ padding:"0.4rem 0.75rem", border:"1px solid #d1d5db", borderRadius:6, fontSize:"0.85rem", width:"220px" }} />
          <select value={segment} onChange={e => setSegment(e.target.value)} style={{ padding:"0.4rem 0.5rem", border:"1px solid #d1d5db", borderRadius:6, fontSize:"0.85rem" }}>
            <option value="ALL">All</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Soft-deleted</option>
            <option value="USED">In use</option>
          </select>
        </div>
      </div>

      {msg && <div style={{ marginBottom:"0.75rem", padding:"0.6rem 0.9rem", borderRadius:6, background:"#f0fdf4", color:"#16a34a", fontSize:"0.85rem" }}>{msg}</div>}

      <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, overflowX:"auto" }}>
        <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
          <thead><tr style={{ background:"#f9fafb" }}>
            {["Customer","Address","City","State","Pincode","Orders","Status","Created","Actions"].map(h => <th key={h} style={{ textAlign:"left", padding:"10px 12px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {pageItems.length === 0 && <tr><td colSpan={9} style={{ padding:"2rem", textAlign:"center", color:"#94a3b8" }}>No addresses found</td></tr>}
            {pageItems.map(a => (
              <tr key={a.id} style={{ borderBottom:"1px solid #f3f4f6", opacity: a.active ? 1 : 0.6 }}>
                <td style={{ padding:"10px 12px" }}>
                  <div style={{ fontWeight:500 }}>{a.fullName}</div>
                  <div style={{ fontSize:"0.75rem", color:"#6b7280" }}>{a.userName || "-"}</div>
                  <div style={{ fontSize:"0.75rem", color:"#94a3b8" }}>{a.userEmail || "-"}</div>
                </td>
                <td style={{ padding:"10px 12px" }}>
                  {a.line1 || a.street}{a.line2 ? `, ${a.line2}` : ""}
                </td>
                <td style={{ padding:"10px 12px" }}>{a.city}</td>
                <td style={{ padding:"10px 12px" }}>{a.state}</td>
                <td style={{ padding:"10px 12px" }}>{a.pincode}</td>
                <td style={{ padding:"10px 12px", fontWeight:600 }}>{a.usageCount || 0}</td>
                <td style={{ padding:"10px 12px" }}>
                  <span style={{ padding:"2px 8px", borderRadius:"4px", fontSize:"0.75rem", fontWeight:600, background:a.active?"#f0fdf4":"#fef2f2", color:a.active?"#16a34a":"#dc2626" }}>
                    {a.active ? "Active" : "Deleted"}
                  </span>
                </td>
                <td style={{ padding:"10px 12px", color:"#6b7280", fontSize:"0.8rem" }}>{a.createdAt ? new Date(a.createdAt).toLocaleDateString() : "-"}</td>
                <td style={{ padding:"10px 12px" }}>
                  {!a.active && (
                    <button onClick={() => handleRestore(a.id)} style={{ padding:"0.2rem 0.6rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem" }}>
                      Restore
                    </button>
                  )}
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

export default AdminAddresses;