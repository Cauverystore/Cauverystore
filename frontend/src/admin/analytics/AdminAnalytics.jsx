import React, { useState, useEffect } from "react";
import api from "../../api/axios";

const months = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
const LABELS = { "7d":"Last 7 Days","30d":"Last 30 Days","90d":"Last 90 Days","1y":"Last Year","all":"All Time" };

const AdminAnalytics = () => {
  const [range, setRange] = useState("30d");
  const [stats, setStats] = useState(null);
  const [chartData, setChartData] = useState([]);
  const [topProducts, setTopProducts] = useState([]);

  const getDates = (r) => {
    const now = new Date();
    let from = new Date();
    if (r === "7d") from.setDate(now.getDate()-7);
    else if (r === "30d") from.setDate(now.getDate()-30);
    else if (r === "90d") from.setDate(now.getDate()-90);
    else if (r === "1y") from.setFullYear(now.getFullYear()-1);
    else from = new Date("2020-01-01");
    return { from: from.toISOString().split("T")[0], to: now.toISOString().split("T")[0] };
  };

  const fetchData = async (r) => {
    const { from, to } = getDates(r);
    try {
      const [s, c, t] = await Promise.all([
        api.get("/api/admin/analytics/dashboard"),
        api.get(`/api/admin/analytics/sales-chart?from=${from}&to=${to}`),
        api.get(`/api/admin/analytics/top-products?from=${from}&to=${to}&limit=10`),
      ]);
      setStats(s.data);
      setChartData(c.data?.points || []);
      setTopProducts(t.data || []);
    } catch { /* ignore */ }
  };

  useEffect(() => { fetchData(range); }, [range]);

  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>Analytics</h1>
        <div style={{ display:"flex", gap:"0.5rem" }}>
          {Object.entries(LABELS).map(([k,v]) => (
            <button key={k} onClick={() => setRange(k)}
              style={{ padding:"0.4rem 0.8rem", border:"1px solid #e2e8f0", borderRadius:6, background:range===k?"#16a34a":"#fff", color:range===k?"#fff":"#475569", cursor:"pointer", fontSize:"0.8rem" }}>{v}</button>
          ))}
        </div>
      </div>

      {stats && (
        <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fit,minmax(180px,1fr))", gap:"16px", marginBottom:"24px" }}>
          {[
            { label:"Total Orders", value:stats.totalOrders, color:"#2563eb" },
            { label:"Total Revenue", value:"₹"+(stats.totalRevenue||0).toLocaleString(), color:"#16a34a" },
            { label:"Total Products", value:stats.totalProducts, color:"#d97706" },
            { label:"Total Users", value:stats.totalUsers, color:"#7c3aed" },
            { label:"Total Refunds", value:stats.totalRefunds, color:"#dc2626" },
            { label:"Low Stock Items", value:stats.lowStockProducts, color:"#ea580c" },
          ].map(c => (
            <div key={c.label} style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:"10px", padding:"1.25rem" }}>
              <div style={{ fontSize:"1.5rem", fontWeight:800, color:c.color }}>{c.value}</div>
              <div style={{ fontSize:"0.85rem", color:"#6b7280", marginTop:"4px" }}>{c.label}</div>
            </div>
          ))}
        </div>
      )}

      <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:"24px" }}>
        <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:"10px", padding:"1.25rem" }}>
          <h3 style={{ fontSize:"1rem", fontWeight:600, marginBottom:"1rem" }}>Sales Trend</h3>
          {chartData.length > 0 ? (
            <div style={{ display:"flex", alignItems:"flex-end", gap:"2px", height:"160px" }}>
              {chartData.map((p,i) => {
                const max = Math.max(...chartData.map(x => x.sales), 1);
                const h = (p.sales / max) * 140;
                return <div key={i} style={{ flex:1, display:"flex", flexDirection:"column", alignItems:"center" }}>
                  <div style={{ width:"100%", background:"#16a34a", borderRadius:"3px 3px 0 0", height:`${h}px`, minHeight:"2px", opacity:0.8 }} title={`${p.date}: ₹${p.sales}`} />
                </div>;
              })}
            </div>
          ) : <p style={{ color:"#94a3b8", fontSize:"0.9rem" }}>No sales data for this period</p>}
        </div>

        <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:"10px", padding:"1.25rem" }}>
          <h3 style={{ fontSize:"1rem", fontWeight:600, marginBottom:"0.75rem" }}>Top Selling Products</h3>
          {topProducts.length > 0 ? (
            <table style={{ width:"100%", borderCollapse:"collapse", fontSize:"0.85rem" }}>
              <thead><tr style={{ background:"#f9fafb" }}>
                <th style={{ textAlign:"left", padding:"8px 10px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>Product</th>
                <th style={{ textAlign:"right", padding:"8px 10px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>Sold</th>
                <th style={{ textAlign:"right", padding:"8px 10px", fontWeight:600, fontSize:"0.75rem", color:"#6b7280", textTransform:"uppercase" }}>Revenue</th>
              </tr></thead>
              <tbody>
                {topProducts.map((p,i) => (
                  <tr key={i} style={{ borderBottom:"1px solid #f3f4f6" }}>
                    <td style={{ padding:"8px 10px" }}>{p.name}</td>
                    <td style={{ padding:"8px 10px", textAlign:"right" }}>{p.totalSold}</td>
                    <td style={{ padding:"8px 10px", textAlign:"right" }}>₹{(p.totalRevenue||0).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : <p style={{ color:"#94a3b8", fontSize:"0.9rem" }}>No product sales data</p>}
        </div>
      </div>
    </div>
  );
};

export default AdminAnalytics;
