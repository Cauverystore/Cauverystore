import React, { useState, useEffect } from "react";
import api from "../../api/axios";
import Pagination from "../../components/Pagination";

const AdminAuditDashboard = () => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);

  useEffect(() => {
    const fetch = async () => {
      setLoading(true);
      try { const res = await api.get("/api/admin/audit-logs", { params: { page } }); setLogs(res.data.content || res.data || []); setTotalPages(res.data.totalPages || 1); } catch (err) { console.error(err); }
      setLoading(false);
    };
    fetch();
  }, [page]);

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  return (
    <div>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 600, marginBottom: "1.5rem" }}>Audit Logs</h1>
      <div style={{ overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem" }}>
          <thead><tr style={{ background: "#f8fafc" }}><th style={thStyle}>Timestamp</th><th style={thStyle}>User</th><th style={thStyle}>Action</th><th style={thStyle}>Details</th></tr></thead>
          <tbody>
            {logs.map((l, i) => (
              <tr key={l.id || l._id || i}><td style={tdStyle}>{new Date(l.timestamp || l.createdAt).toLocaleString()}</td><td style={tdStyle}>{l.user || l.userName || l.adminId}</td><td style={tdStyle}>{l.action}</td><td style={tdStyle}><pre style={{ margin: 0, fontSize: "0.8rem" }}>{JSON.stringify(l.details, null, 2)}</pre></td></tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={page} totalPages={totalPages} onPage={setPage} />
    </div>
  );
};
const thStyle = { padding: "0.75rem", borderBottom: "1px solid #e2e8f0", textAlign: "left", fontWeight: 600 };
const tdStyle = { padding: "0.5rem 0.75rem", borderBottom: "1px solid #e2e8f0" };
export default AdminAuditDashboard;
