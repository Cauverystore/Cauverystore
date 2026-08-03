import React, { useState, useEffect } from "react";
import api from "../../api/axios";
import Pagination from "../../components/Pagination";

const AdminRefunds = () => {
  const [refunds, setRefunds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);

  const PAGE_SIZE = 20;

  useEffect(() => {
    const fetch = async () => {
      setLoading(true);
      try {
        const res = await api.get("/api/admin/refunds");
        const all = Array.isArray(res.data) ? res.data : res.data.content || [];
        setTotalPages(Math.max(1, Math.ceil(all.length / PAGE_SIZE)));
        setRefunds(all.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE));
      } catch (err) { console.error(err); }
      setLoading(false);
    };
    fetch();
  }, [page]);

  const handleAction = async (id, action) => {
    try {
      const status = action === "APPROVE" ? "COMPLETED" : "REJECTED";
      await api.put(`/api/admin/refunds/${id}/status`, { status });
      const res = await api.get("/api/admin/refunds");
      const all = Array.isArray(res.data) ? res.data : res.data.content || [];
      setTotalPages(Math.max(1, Math.ceil(all.length / PAGE_SIZE)));
      setRefunds(all.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE));
    } catch (err) { alert("Failed to process"); }
  };

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  return (
    <div>
      <h1 style={{ fontSize: "1.5rem", fontWeight: 600, marginBottom: "1.5rem" }}>Refunds</h1>
      <div style={{ overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem" }}>
          <thead><tr style={{ background: "#f8fafc" }}><th style={thStyle}>Refund #</th><th style={thStyle}>Order #</th><th style={thStyle}>Amount</th><th style={thStyle}>Reason</th><th style={thStyle}>Status</th><th style={thStyle}>Actions</th></tr></thead>
          <tbody>
            {refunds.map((r) => (
              <tr key={r.id || r._id}>
                <td style={tdStyle}>{(r.id || r._id)?.toString().slice(-8)}</td><td style={tdStyle}>{r.orderId}</td><td style={tdStyle}>&#8377;{(r.amount || 0).toFixed(2)}</td>
                <td style={tdStyle}>{r.reason}</td><td style={tdStyle}>{r.status}</td>
                <td style={tdStyle}>
                  {r.status === "PENDING" && (
                    <>
                      <button onClick={() => handleAction(r.id || r._id, "APPROVE")} style={{ padding: "0.2rem 0.5rem", background: "#16a34a", color: "#fff", border: "none", borderRadius: 4, cursor: "pointer", marginRight: "0.25rem", fontSize: "0.8rem" }}>Approve</button>
                      <button onClick={() => handleAction(r.id || r._id, "REJECT")} style={{ padding: "0.2rem 0.5rem", background: "#dc2626", color: "#fff", border: "none", borderRadius: 4, cursor: "pointer", fontSize: "0.8rem" }}>Reject</button>
                    </>
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
const thStyle = { padding: "0.75rem", borderBottom: "1px solid #e2e8f0", textAlign: "left", fontWeight: 600 };
const tdStyle = { padding: "0.5rem 0.75rem", borderBottom: "1px solid #e2e8f0" };
export default AdminRefunds;
