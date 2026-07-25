import React, { useState } from "react";
import api from "../../api/axios";
import * as XLSX from "xlsx";

const AdminReports = () => {
  const [from, setFrom] = useState(() => { const d = new Date(); d.setDate(d.getDate()-30); return d.toISOString().split("T")[0]; });
  const [to, setTo] = useState(() => new Date().toISOString().split("T")[0]);
  const [salesReport, setSalesReport] = useState(null);
  const [productReport, setProductReport] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchSalesReport = async () => {
    setLoading(true);
    try {
      const res = await api.get(`/api/admin/reports/sales?from=${from}&to=${to}`);
      setSalesReport(res.data);
    } catch { alert("Failed to fetch sales report"); }
    setLoading(false);
  };

  const fetchProductReport = async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/admin/reports/products");
      setProductReport(res.data);
    } catch { alert("Failed to fetch product report"); }
    setLoading(false);
  };

  const downloadCSV = (data, filename) => {
    if (!data || data.length === 0) return;
    const headers = Object.keys(data[0]);
    const csv = [headers.join(","), ...data.map(r => headers.map(h => `"${r[h]||""}"`).join(","))].join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a"); a.href = url; a.download = filename; a.click();
    URL.revokeObjectURL(url);
  };

  const downloadExcel = (data, filename) => {
    if (!data || data.length === 0) return;
    const ws = XLSX.utils.json_to_sheet(data);
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "Report");
    XLSX.writeFile(wb, filename);
  };

  const inp = { padding:"0.4rem 0.5rem", border:"1px solid #d1d5db", borderRadius:5, fontSize:"0.85rem" };

  return (
    <div>
      <h1 style={{ fontSize:"1.5rem", fontWeight:700, marginBottom:"1.5rem" }}>Reports</h1>

      <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:"24px" }}>
        <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, padding:"1.25rem" }}>
          <h3 style={{ fontSize:"1.1rem", fontWeight:600, marginBottom:"1rem" }}>Sales Report</h3>
          <div style={{ display:"flex", gap:"0.75rem", marginBottom:"1rem", alignItems:"flex-end" }}>
            <div><label style={{ fontSize:"0.8rem", display:"block", marginBottom:"4px" }}>From</label><input type="date" value={from} onChange={e => setFrom(e.target.value)} style={inp} /></div>
            <div><label style={{ fontSize:"0.8rem", display:"block", marginBottom:"4px" }}>To</label><input type="date" value={to} onChange={e => setTo(e.target.value)} style={inp} /></div>
            <button onClick={fetchSalesReport} style={{ padding:"0.4rem 1rem", background:"#2563eb", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem" }}>{loading ? "Loading..." : "Generate"}</button>
          </div>
          {salesReport && (
            <div>
              <div style={{ display:"grid", gridTemplateColumns:"repeat(3,1fr)", gap:"12px", marginBottom:"1rem" }}>
                <div style={{ background:"#f0fdf4", padding:"12px", borderRadius:8, textAlign:"center" }}>
                  <div style={{ fontSize:"1.25rem", fontWeight:700, color:"#16a34a" }}>{salesReport.totalOrders}</div>
                  <div style={{ fontSize:"0.8rem", color:"#166534" }}>Total Orders</div>
                </div>
                <div style={{ background:"#eff6ff", padding:"12px", borderRadius:8, textAlign:"center" }}>
                  <div style={{ fontSize:"1.25rem", fontWeight:700, color:"#2563eb" }}>₹{(salesReport.totalRevenue||0).toLocaleString()}</div>
                  <div style={{ fontSize:"0.8rem", color:"#1e40af" }}>Total Revenue</div>
                </div>
                <div style={{ background:"#fef2f2", padding:"12px", borderRadius:8, textAlign:"center" }}>
                  <div style={{ fontSize:"1.25rem", fontWeight:700, color:"#dc2626" }}>{salesReport.cancelledOrders}</div>
                  <div style={{ fontSize:"0.8rem", color:"#991b1b" }}>Cancelled</div>
                </div>
              </div>
              <div style={{ display:"flex", gap:"0.5rem" }}>
                <button onClick={() => downloadCSV([salesReport], `sales-report-${from}-to-${to}.csv`)} style={{ padding:"0.4rem 1rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.8rem" }}>CSV</button>
                <button onClick={() => downloadExcel([salesReport], `sales-report-${from}-to-${to}.xlsx`)} style={{ padding:"0.4rem 1rem", background:"#2563eb", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.8rem" }}>Excel</button>
              </div>
            </div>
          )}
        </div>

        <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, padding:"1.25rem" }}>
          <h3 style={{ fontSize:"1.1rem", fontWeight:600, marginBottom:"1rem" }}>Product Report</h3>
          <button onClick={fetchProductReport} style={{ padding:"0.4rem 1rem", background:"#2563eb", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem", marginBottom:"1rem" }}>Generate Product Report</button>
          {productReport && productReport.length > 0 && (
            <div>
              <div style={{ maxHeight:"300px", overflowY:"auto", marginBottom:"0.75rem" }}>
                <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.8rem" }}>
                  <thead><tr style={{ background:"#f9fafb", position:"sticky", top:0 }}>
                    {["Name","Price","Stock","Status"].map(h => <th key={h} style={{ textAlign:"left", padding:"6px 8px", fontWeight:600, fontSize:"0.7rem", color:"#6b7280", textTransform:"uppercase" }}>{h}</th>)}
                  </tr></thead>
                  <tbody>
                    {productReport.map(p => (
                      <tr key={p.id} style={{ borderBottom:"1px solid #f3f4f6" }}>
                        <td style={{ padding:"6px 8px" }}>{p.name}</td>
                        <td style={{ padding:"6px 8px" }}>₹{p.price}</td>
                        <td style={{ padding:"6px 8px" }}>{p.stock}</td>
                        <td style={{ padding:"6px 8px" }}><span style={{ color:p.active?"#16a34a":"#dc2626" }}>{p.active ? "Active" : "Inactive"}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div style={{ display:"flex", gap:"0.5rem" }}>
                <button onClick={() => downloadCSV(productReport, `product-report-${new Date().toISOString().split("T")[0]}.csv`)} style={{ padding:"0.4rem 1rem", background:"#16a34a", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.8rem" }}>CSV</button>
                <button onClick={() => downloadExcel(productReport, `product-report-${new Date().toISOString().split("T")[0]}.xlsx`)} style={{ padding:"0.4rem 1rem", background:"#2563eb", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.8rem" }}>Excel</button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default AdminReports;
