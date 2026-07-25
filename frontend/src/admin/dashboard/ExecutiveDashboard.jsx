import React, { useState, useEffect, useCallback } from "react";
import {
  ShoppingCart, Package, Users, Store, Hourglass, CreditCard,
  DollarSign, Calendar, TrendingDown, RefreshCw, AlertTriangle,
  XCircle, TrendingUp, Clock, CheckCircle, Heart, Star, Bell,
} from "lucide-react";
import api from "../../api/axios";

const f = (n) => n != null ? "\u20B9" + Number(n).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : "\u20B90.00";
const fd = (d) => d ? new Date(d).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" }) : "-";
const card = (label, value, color, icon) => (
  <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "18px 20px", display: "flex", alignItems: "center", gap: "16px" }}>
    <div style={{ width: "48px", height: "48px", borderRadius: "12px", background: color + "15", display: "flex", alignItems: "center", justifyContent: "center", fontSize: "1.5rem", flexShrink: 0 }}>{icon}</div>
    <div>
      <div style={{ fontSize: "1.6rem", fontWeight: 800, color, lineHeight: 1.2 }}>{value}</div>
      <div style={{ fontSize: "0.82rem", color: "#6b7280", marginTop: "4px" }}>{label}</div>
    </div>
  </div>
);

const tabStyle = (active) => ({
  padding: "10px 20px", border: "none", background: active ? "#1e3a5f" : "transparent",
  color: active ? "#fff" : "#64748b", borderRadius: "8px", cursor: "pointer",
  fontWeight: active ? 600 : 400, fontSize: "0.9rem", transition: "all 0.2s"
});

const thStyle = { padding: "10px 14px", textAlign: "left", fontSize: "0.8rem", fontWeight: 600, color: "#64748b", borderBottom: "2px solid #e2e8f0", background: "#f8fafc", whiteSpace: "nowrap" };
const tdStyle = { padding: "10px 14px", fontSize: "0.85rem", color: "#333", borderBottom: "1px solid #f1f5f9" };
const badge = (status) => {
  const m = { PLACED: { bg: "#dbeafe", c: "#1e40af" }, PROCESSING: { bg: "#fef3c7", c: "#92400e" }, SHIPPED: { bg: "#e0e7ff", c: "#3730a3" }, DELIVERED: { bg: "#d1fae5", c: "#065f46" }, CANCELLED: { bg: "#fee2e2", c: "#991b1b" }, PENDING: { bg: "#fef3c7", c: "#92400e" }, APPROVED: { bg: "#d1fae5", c: "#065f46" }, REJECTED: { bg: "#fee2e2", c: "#991b1b" } };
  const s = m[status] || { bg: "#f1f5f9", c: "#475569" };
  return <span style={{ padding: "3px 10px", borderRadius: "12px", fontSize: "0.75rem", fontWeight: 600, background: s.bg, color: s.c }}>{status}</span>;
};

const Spinner = () => <div style={{ textAlign: "center", padding: "4rem", color: "#6b7280", fontSize: "1rem" }}>Loading...</div>;
const ErrorMsg = ({ msg }) => <div style={{ textAlign: "center", padding: "3rem", color: "#dc2626", fontSize: "1rem" }}>{msg || "Failed to load data."}</div>;

function Table({ headers, rows, renderRow }) {
  if (!rows || rows.length === 0) return <div style={{ padding: "20px", textAlign: "center", color: "#94a3b8", fontSize: "0.9rem" }}>No data available</div>;
  return (
    <div style={{ overflowX: "auto" }}>
      <table style={{ width: "100%", borderCollapse: "collapse", minWidth: "600px" }}>
        <thead><tr>{headers.map((h, i) => <th key={i} style={thStyle}>{h}</th>)}</tr></thead>
        <tbody>{rows.map((row, i) => <tr key={i} style={{ background: i % 2 === 0 ? "#fff" : "#f8fafc" }}>{renderRow(row, i)}</tr>)}</tbody>
      </table>
    </div>
  );
}

const ExecutiveDashboard = () => {
  const [activeTab, setActiveTab] = useState("sales");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [dashboard, setDashboard] = useState(null);
  const [sales, setSales] = useState(null);
  const [topProducts, setTopProducts] = useState([]);
  const [sellerBreakdown, setSellerBreakdown] = useState([]);
  const [inProgress, setInProgress] = useState([]);
  const [completed, setCompleted] = useState([]);
  const [returnStats, setReturnStats] = useState(null);
  const [pendingApprovals, setPendingApprovals] = useState([]);
  const [inventory, setInventory] = useState(null);
  const [warehouses, setWarehouses] = useState([]);
  const [sellers, setSellers] = useState([]);
  const [customerActivity, setCustomerActivity] = useState(null);
  const [sentiment, setSentiment] = useState(null);
  const [notifications, setNotifications] = useState([]);
  const [reportData, setReportData] = useState(null);
  const [reportType, setReportType] = useState("seller");
  const [reportFilter, setReportFilter] = useState("");
  const [reportLoading, setReportLoading] = useState(false);
  const [lastUpdated, setLastUpdated] = useState(new Date());
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [notifOpen, setNotifOpen] = useState(false);

  const refreshNotifCount = notifications.filter(n => n.severity === "critical" || n.severity === "warning").length;

  const fetchAll = useCallback(async () => {
    try {
      const [d, s, tp, sb, ip, co, rs, pa, inv, wh, sp, ca, se, nt] = await Promise.all([
        api.get("/api/executive/dashboard"),
        api.get("/api/executive/sales/overview"),
        api.get("/api/executive/sales/top-products?limit=10"),
        api.get("/api/executive/sales/seller-breakdown"),
        api.get("/api/executive/orders/in-progress"),
        api.get("/api/executive/orders/completed"),
        api.get("/api/executive/orders/returns-refunds"),
        api.get("/api/executive/orders/pending-approvals"),
        api.get("/api/executive/inventory/insights"),
        api.get("/api/executive/inventory/warehouses"),
        api.get("/api/executive/sellers/performance"),
        api.get("/api/executive/customers/activity"),
        api.get("/api/executive/customers/reviews/sentiment"),
        api.get("/api/executive/notifications"),
      ]);
      setDashboard(d.data);
      setSales(s.data);
      setTopProducts(Array.isArray(tp.data) ? tp.data : []);
      setSellerBreakdown(Array.isArray(sb.data) ? sb.data : []);
      setInProgress(Array.isArray(ip.data) ? ip.data : []);
      setCompleted(Array.isArray(co.data) ? co.data : []);
      setReturnStats(rs.data);
      setPendingApprovals(Array.isArray(pa.data) ? pa.data : []);
      setInventory(inv.data);
      setWarehouses(Array.isArray(wh.data) ? wh.data : []);
      setSellers(Array.isArray(sp.data) ? sp.data : []);
      setCustomerActivity(ca.data);
      setSentiment(se.data);
      setNotifications(Array.isArray(nt.data) ? nt.data : []);
      setLastUpdated(new Date());
      setError(null);
    } catch (e) {
      setError("Failed to load executive dashboard. Please try again.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  useEffect(() => {
    if (!autoRefresh) return;
    const id = setInterval(fetchAll, 60000);
    return () => clearInterval(id);
  }, [autoRefresh, fetchAll]);

  const generateReport = async () => {
    setReportLoading(true);
    try {
      const res = await api.get("/api/executive/reports/generate", { params: { type: reportType, filter: reportFilter || undefined } });
      setReportData(res.data);
    } catch (e) {
      setReportData({ error: "Failed to generate report" });
    } finally {
      setReportLoading(false);
    }
  };

  if (loading) return <Spinner />;
  if (error) return <ErrorMsg msg={error} />;

  const tabs = [
    { key: "sales", label: "Sales Overview" },
    { key: "orders", label: "Orders" },
    { key: "inventory", label: "Inventory" },
    { key: "sellers", label: "Sellers" },
    { key: "customers", label: "Customers" },
    { key: "reports", label: "Reports" },
  ];

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "20px", flexWrap: "wrap", gap: "12px" }}>
        <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: 0 }}>Executive Dashboard</h1>
        <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          <span style={{ fontSize: "0.78rem", color: "#94a3b8" }}>Last updated: {lastUpdated.toLocaleTimeString()}</span>
          <button onClick={() => setAutoRefresh(!autoRefresh)} style={{ padding: "6px 14px", borderRadius: "6px", border: "1px solid #e2e8f0", background: autoRefresh ? "#d1fae5" : "#f1f5f9", color: autoRefresh ? "#065f46" : "#64748b", fontSize: "0.78rem", cursor: "pointer", fontWeight: 600 }}>{autoRefresh ? "Auto ON" : "Auto OFF"}</button>
          <div style={{ position: "relative" }}>
            <button onClick={() => setNotifOpen(!notifOpen)} style={{ padding: "8px 12px", borderRadius: "8px", border: "1px solid #e2e8f0", background: "#fff", cursor: "pointer", fontSize: "1.1rem", position: "relative" }}>
              <Bell size={20} />
              {refreshNotifCount > 0 && <span style={{ position: "absolute", top: "-4px", right: "-4px", background: "#dc2626", color: "#fff", borderRadius: "50%", width: "18px", height: "18px", fontSize: "0.65rem", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700 }}>{refreshNotifCount}</span>}
            </button>
            {notifOpen && (
              <div style={{ position: "absolute", right: 0, top: "calc(100% + 8px)", width: "360px", background: "#fff", border: "1px solid #e2e8f0", borderRadius: "12px", boxShadow: "0 10px 40px rgba(0,0,0,0.12)", zIndex: 100, maxHeight: "400px", overflowY: "auto" }}>
                <div style={{ padding: "14px 16px", borderBottom: "1px solid #e2e8f0", fontWeight: 600, fontSize: "0.9rem" }}>Alerts & Notifications</div>
                {notifications.length === 0 ? <div style={{ padding: "20px", textAlign: "center", color: "#94a3b8", fontSize: "0.85rem" }}>No alerts</div> :
                  notifications.map((n, i) => {
                    const sc = n.severity === "critical" ? "#dc2626" : n.severity === "warning" ? "#d97706" : "#2563eb";
                    const icons = { LOW_STOCK: <Package size={18} />, PENDING_APPROVAL: <Hourglass size={18} />, NEGATIVE_RATINGS: <Star size={18} />, HIGH_REFUND_RATE: <DollarSign size={18} />, OUT_OF_STOCK: <XCircle size={18} /> };
                    return (
                      <div key={i} style={{ padding: "12px 16px", borderBottom: "1px solid #f1f5f9", display: "flex", gap: "10px", alignItems: "flex-start" }}>
                        <span style={{ display: "flex" }}>{icons[n.type] || <Bell size={18} />}</span>
                        <div style={{ flex: 1 }}>
                          <div style={{ fontSize: "0.82rem", color: "#333", lineHeight: 1.4 }}>{n.message}</div>
                          <div style={{ display: "flex", gap: "6px", marginTop: "4px" }}>
                            <span style={{ padding: "2px 8px", borderRadius: "10px", fontSize: "0.7rem", fontWeight: 600, background: sc + "20", color: sc }}>{n.severity}</span>
                          </div>
                        </div>
                      </div>
                    );
                  })}
              </div>
            )}
          </div>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: "14px", marginBottom: "24px" }}>
        {dashboard && <>
          {card("Total Revenue", f(dashboard.totalRevenue), "#059669", <DollarSign size={24} />)}
          {card("Total Orders", dashboard.totalOrders || 0, "#2563eb", <ShoppingCart size={24} />)}
          {card("Active Products", dashboard.activeProducts || 0, "#7c3aed", <Package size={24} />)}
          {card("Total Customers", dashboard.totalCustomers || 0, "#ea580c", <Users size={24} />)}
          {card("Active Sellers", dashboard.totalSellers || 0, "#0d9488", <Store size={24} />)}
          {card("Pending Approvals", dashboard.pendingApprovals || 0, "#dc2626", <Hourglass size={24} />)}
        </>}
      </div>

      <div style={{ display: "flex", gap: "6px", marginBottom: "24px", flexWrap: "wrap", borderBottom: "2px solid #e2e8f0", paddingBottom: "4px" }}>
        {tabs.map(t => <button key={t.key} onClick={() => setActiveTab(t.key)} style={tabStyle(activeTab === t.key)}>{t.label}</button>)}
      </div>

      {activeTab === "sales" && <SalesTab sales={sales} topProducts={topProducts} sellerBreakdown={sellerBreakdown} />}
      {activeTab === "orders" && <OrdersTab inProgress={inProgress} completed={completed} returnStats={returnStats} pendingApprovals={pendingApprovals} />}
      {activeTab === "inventory" && <InventoryTab inventory={inventory} warehouses={warehouses} />}
      {activeTab === "sellers" && <SellersTab sellers={sellers} />}
      {activeTab === "customers" && <CustomersTab activity={customerActivity} sentiment={sentiment} />}
      {activeTab === "reports" && <ReportsTab reportType={reportType} setReportType={setReportType} reportFilter={reportFilter} setReportFilter={setReportFilter} generateReport={generateReport} reportData={reportData} reportLoading={reportLoading} />}
    </div>
  );
};

const SalesTab = ({ sales, topProducts, sellerBreakdown }) => (
  <div>
    {sales && <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: "14px", marginBottom: "24px" }}>
      {card("Daily Sales", f(sales.dailySales), "#059669", <Calendar size={24} />)}
      {card("Weekly Sales", f(sales.weeklySales), "#2563eb", <Calendar size={24} />)}
      {card("Monthly Sales", f(sales.monthlySales), "#7c3aed", <Calendar size={24} />)}
      {card("Total Revenue", f(sales.totalRevenue), "#ea580c", <DollarSign size={24} />)}
    </div>}
    {sales && sales.revenueTrends && sales.revenueTrends.length > 0 && <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px", marginBottom: "24px" }}>
      <h3 style={{ margin: "0 0 16px 0", fontSize: "1rem", fontWeight: 600 }}>Revenue Trends (Last 30 Days)</h3>
      <div style={{ display: "flex", alignItems: "flex-end", gap: "4px", height: "180px", padding: "10px 0" }}>
        {(() => {
          const vals = sales.revenueTrends.map(t => Number(t.sales));
          const mx = Math.max(...vals, 1);
          return sales.revenueTrends.map((t, i) => (
            <div key={i} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", height: "100%", justifyContent: "flex-end" }}>
              <div style={{ width: "100%", maxWidth: "40px", background: "linear-gradient(to top, #2563eb, #3b82f6)", borderRadius: "4px 4px 0 0", height: Math.max((Number(t.sales) / mx) * 160, 4) + "px", transition: "height 0.3s", minHeight: "4px" }} />
              <span style={{ fontSize: "0.6rem", color: "#94a3b8", marginTop: "4px", transform: "rotate(-45deg)", whiteSpace: "nowrap" }}>{t.date ? t.date.substring(5) : ""}</span>
            </div>
          ));
        })()}
      </div>
    </div>}
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
      <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px" }}>
        <h3 style={{ margin: "0 0 14px 0", fontSize: "1rem", fontWeight: 600 }}>Top Selling Products</h3>
        <Table headers={["#", "Product Name", "Code", "Sold"]} rows={topProducts} renderRow={(r, i) => <>
          <td style={tdStyle}>{i + 1}</td>
          <td style={tdStyle}>{r.name || "-"}</td>
          <td style={{ ...tdStyle, color: "#64748b" }}>{r.productCode || "-"}</td>
          <td style={{ ...tdStyle, fontWeight: 600 }}>{r.totalSold || 0}</td>
        </>} />
      </div>
      <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px" }}>
        <h3 style={{ margin: "0 0 14px 0", fontSize: "1rem", fontWeight: 600 }}>Seller Sales Breakdown</h3>
        <Table headers={["Seller", "Orders", "Total Sales"]} rows={sellerBreakdown} renderRow={(r) => <>
          <td style={tdStyle}>{r.sellerName || "Unknown"}</td>
          <td style={tdStyle}>{r.orderCount || 0}</td>
          <td style={{ ...tdStyle, fontWeight: 600 }}>{f(r.totalSales)}</td>
        </>} />
      </div>
    </div>
  </div>
);

const OrdersTab = ({ inProgress, completed, returnStats, pendingApprovals }) => (
  <div>
    {returnStats && <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: "14px", marginBottom: "24px" }}>
      {card("Total Refunds", returnStats.totalRefunds || 0, "#dc2626", <CreditCard size={24} />)}
      {card("Refund Amount", f(returnStats.totalRefundAmount), "#ea580c", <TrendingDown size={24} />)}
      {card("Replacements", returnStats.replacements || 0, "#2563eb", <RefreshCw size={24} />)}
      {card("Pending Approvals", pendingApprovals.length, "#d97706", <Clock size={24} />)}
    </div>}
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px", marginBottom: "24px" }}>
      <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px" }}>
        <h3 style={{ margin: "0 0 14px 0", fontSize: "1rem", fontWeight: 600 }}>Orders In Progress ({inProgress.length})</h3>
        <Table headers={["Order ID", "Customer", "Status", "Amount", "Date"]} rows={inProgress.slice(0, 20)} renderRow={(r) => <>
          <td style={tdStyle}>#{r.id}</td>
          <td style={tdStyle}>{r.user?.fullName || "N/A"}</td>
          <td style={tdStyle}>{badge(r.status)}</td>
          <td style={tdStyle}>{f(r.totalAmount)}</td>
          <td style={{ ...tdStyle, color: "#64748b", fontSize: "0.78rem" }}>{fd(r.createdAt)}</td>
        </>} />
      </div>
      <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px" }}>
        <h3 style={{ margin: "0 0 14px 0", fontSize: "1rem", fontWeight: 600 }}>Completed Orders ({completed.length})</h3>
        <Table headers={["Order ID", "Customer", "Status", "Amount", "Date"]} rows={completed.slice(0, 20)} renderRow={(r) => <>
          <td style={tdStyle}>#{r.id}</td>
          <td style={tdStyle}>{r.user?.fullName || "N/A"}</td>
          <td style={tdStyle}>{badge(r.status)}</td>
          <td style={tdStyle}>{f(r.totalAmount)}</td>
          <td style={{ ...tdStyle, color: "#64748b", fontSize: "0.78rem" }}>{fd(r.createdAt)}</td>
        </>} />
      </div>
    </div>
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
      {returnStats && returnStats.returnBreakdown && <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px" }}>
        <h3 style={{ margin: "0 0 14px 0", fontSize: "1rem", fontWeight: 600 }}>Returns Breakdown</h3>
        <Table headers={["Status", "Count"]} rows={returnStats.returnBreakdown} renderRow={(r) => <>
          <td style={tdStyle}>{badge(r.status)}</td>
          <td style={{ ...tdStyle, fontWeight: 600 }}>{r.count || 0}</td>
        </>} />
      </div>}
      <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px" }}>
        <h3 style={{ margin: "0 0 14px 0", fontSize: "1rem", fontWeight: 600 }}>Pending Approvals ({pendingApprovals.length})</h3>
        <Table headers={["Product", "SKU", "Seller ID"]} rows={pendingApprovals.slice(0, 20)} renderRow={(r) => <>
          <td style={tdStyle}>{r.name || "N/A"}</td>
          <td style={{ ...tdStyle, color: "#64748b" }}>{r.sku || "-"}</td>
          <td style={tdStyle}>{r.sellerId || "-"}</td>
        </>} />
      </div>
    </div>
  </div>
);

const InventoryTab = ({ inventory, warehouses }) => (
  <div>
    {inventory && <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: "14px", marginBottom: "24px" }}>
      {card("Low Stock", inventory.lowStockCount || 0, "#d97706", <AlertTriangle size={24} />)}
      {card("Out of Stock", inventory.outOfStockCount || 0, "#dc2626", <XCircle size={24} />)}
      {card("Fast Moving", (inventory.fastMoving || []).length, "#059669", <TrendingUp size={24} />)}
      {card("Slow Moving", (inventory.slowMoving || []).length, "#6b7280", <Clock size={24} />)}
    </div>}
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px", marginBottom: "24px" }}>
      <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px" }}>
        <h3 style={{ margin: "0 0 14px 0", fontSize: "1rem", fontWeight: 600 }}>Low Stock Products</h3>
        <Table headers={["Product Name", "Stock", "SKU"]} rows={inventory?.lowStockProducts || []} renderRow={(r) => <>
          <td style={tdStyle}>{r.name || "-"}</td>
          <td style={{ ...tdStyle, fontWeight: 600, color: r.stock < 5 ? "#dc2626" : "#d97706" }}>{r.stock ?? "-"}</td>
          <td style={{ ...tdStyle, color: "#64748b" }}>{r.sku || "-"}</td>
        </>} />
      </div>
      <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px" }}>
        <h3 style={{ margin: "0 0 14px 0", fontSize: "1rem", fontWeight: 600 }}>Out of Stock Products</h3>
        <Table headers={["Product Name", "SKU"]} rows={inventory?.outOfStockProducts || []} renderRow={(r) => <>
          <td style={tdStyle}>{r.name || "-"}</td>
          <td style={{ ...tdStyle, color: "#64748b" }}>{r.sku || "-"}</td>
        </>} />
      </div>
    </div>
    <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px", marginBottom: "24px" }}>
      <h3 style={{ margin: "0 0 14px 0", fontSize: "1rem", fontWeight: 600 }}>Fast Moving Products</h3>
      <Table headers={["Product", "Orders (30d)", "Views (30d)"]} rows={inventory?.fastMoving || []} renderRow={(r) => <>
        <td style={tdStyle}>{r.productName || "-"}</td>
        <td style={{ ...tdStyle, fontWeight: 600, color: "#059669" }}>{r.totalOrders || 0}</td>
        <td style={tdStyle}>{r.totalViews || 0}</td>
      </>} />
    </div>
    <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px" }}>
      <h3 style={{ margin: "0 0 14px 0", fontSize: "1rem", fontWeight: 600 }}>Warehouse Summary</h3>
      <Table headers={["Warehouse", "City", "State", "Capacity"]} rows={warehouses} renderRow={(r) => <>
        <td style={tdStyle}>{r.name || "-"}</td>
        <td style={tdStyle}>{r.city || "-"}</td>
        <td style={tdStyle}>{r.state || "-"}</td>
        <td style={tdStyle}>{r.capacity != null ? r.capacity : "-"}</td>
      </>} />
    </div>
  </div>
);

const SellersTab = ({ sellers }) => {
  const totalProducts = sellers.reduce((s, r) => s + (r.productCount || 0), 0);
  const totalSales = sellers.reduce((s, r) => s + Number(r.totalSales || 0), 0);
  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: "14px", marginBottom: "24px" }}>
        {card("Total Sellers", sellers.length, "#0d9488", <Store size={24} />)}
        {card("Total Products", totalProducts, "#7c3aed", <Package size={24} />)}
        {card("Total Sales", f(totalSales), "#059669", <DollarSign size={24} />)}
      </div>
      <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px" }}>
        <h3 style={{ margin: "0 0 14px 0", fontSize: "1rem", fontWeight: 600 }}>Seller Performance</h3>
        <Table headers={["Seller", "Email", "Status", "Products", "Orders", "Sales", "Returns", "Return Rate"]} rows={sellers} renderRow={(r) => {
          const rt = r.returnRate || 0;
          const rc = rt > 10 ? "#dc2626" : rt > 5 ? "#d97706" : "#059669";
          return <>
            <td style={tdStyle}>{r.sellerName || "-"}</td>
            <td style={{ ...tdStyle, color: "#64748b", fontSize: "0.78rem" }}>{r.email || "-"}</td>
            <td style={tdStyle}>{badge(r.status)}</td>
            <td style={tdStyle}>{r.productCount || 0}</td>
            <td style={tdStyle}>{r.orderCount || 0}</td>
            <td style={{ ...tdStyle, fontWeight: 600 }}>{f(r.totalSales)}</td>
            <td style={tdStyle}>{r.returnCount || 0}</td>
            <td style={{ ...tdStyle, fontWeight: 600, color: rc }}>{rt}%</td>
          </>;
        }} />
      </div>
    </div>
  );
};

const CustomersTab = ({ activity, sentiment }) => (
  <div>
    {activity && <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: "14px", marginBottom: "24px" }}>
      {card("Total Customers", activity.totalCustomers || 0, "#ea580c", <Users size={24} />)}
      {card("Orders Placed", activity.totalOrdersPlaced || 0, "#2563eb", <ShoppingCart size={24} />)}
      {card("Delivered", activity.deliveredOrders || 0, "#059669", <CheckCircle size={24} />)}
      {card("Wishlist Items", activity.wishlistItems || 0, "#7c3aed", <Heart size={24} />)}
    </div>}
    {sentiment && <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
      <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px" }}>
        <h3 style={{ margin: "0 0 14px 0", fontSize: "1rem", fontWeight: 600 }}>Review Sentiment</h3>
        <div style={{ textAlign: "center", marginBottom: "16px" }}>
          <div style={{ fontSize: "2.5rem", fontWeight: 800, color: sentiment.averageRating >= 4 ? "#059669" : sentiment.averageRating >= 3 ? "#d97706" : "#dc2626" }}>{sentiment.averageRating}</div>
          <div style={{ fontSize: "1.2rem", color: "#f59e0b", marginTop: "4px" }}>
            {"\u2605".repeat(Math.round(sentiment.averageRating))}{"\u2606".repeat(5 - Math.round(sentiment.averageRating))}
          </div>
          <div style={{ fontSize: "0.8rem", color: "#64748b", marginTop: "4px" }}>Average Rating</div>
        </div>
        <div style={{ display: "flex", justifyContent: "center", gap: "24px" }}>
          <div style={{ textAlign: "center" }}><div style={{ fontSize: "1.3rem", fontWeight: 700 }}>{sentiment.totalReviews || 0}</div><div style={{ fontSize: "0.75rem", color: "#64748b" }}>Total Reviews</div></div>
          <div style={{ textAlign: "center" }}><div style={{ fontSize: "1.3rem", fontWeight: 700 }}>{sentiment.approvedReviews || 0}</div><div style={{ fontSize: "0.75rem", color: "#64748b" }}>Approved</div></div>
        </div>
      </div>
      <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px" }}>
        <h3 style={{ margin: "0 0 14px 0", fontSize: "1rem", fontWeight: 600 }}>Rating Distribution</h3>
        <Table headers={["Rating", "Count"]} rows={sentiment?.ratingDistribution || []} renderRow={(r) => <>
          <td style={tdStyle}><span style={{ color: "#f59e0b" }}>{"\u2605".repeat(r.rating)}{"\u2606".repeat(5 - r.rating)}</span></td>
          <td style={{ ...tdStyle, fontWeight: 600 }}>{r.count || 0}</td>
        </>} />
      </div>
    </div>}
  </div>
);

const ReportsTab = ({ reportType, setReportType, reportFilter, setReportFilter, generateReport, reportData, reportLoading }) => (
  <div>
    <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px", marginBottom: "20px" }}>
      <h3 style={{ margin: "0 0 16px 0", fontSize: "1rem", fontWeight: 600 }}>Generate Report</h3>
      <div style={{ display: "flex", gap: "12px", flexWrap: "wrap", alignItems: "flex-end" }}>
        <div>
          <label style={{ display: "block", fontSize: "0.8rem", color: "#64748b", marginBottom: "4px", fontWeight: 500 }}>Report Type</label>
          <select value={reportType} onChange={e => setReportType(e.target.value)} style={{ padding: "9px 14px", borderRadius: "8px", border: "1px solid #e2e8f0", fontSize: "0.85rem", minWidth: "160px", background: "#fff" }}>
            <option value="seller">By Seller</option>
            <option value="category">By Category</option>
            <option value="warehouse">By Warehouse</option>
            <option value="month">By Month</option>
            <option value="state">By State</option>
          </select>
        </div>
        <div>
          <label style={{ display: "block", fontSize: "0.8rem", color: "#64748b", marginBottom: "4px", fontWeight: 500 }}>Filter (optional)</label>
          <input value={reportFilter} onChange={e => setReportFilter(e.target.value)} placeholder="e.g., seller ID or state" style={{ padding: "9px 14px", borderRadius: "8px", border: "1px solid #e2e8f0", fontSize: "0.85rem", minWidth: "200px" }} />
        </div>
        <button onClick={generateReport} disabled={reportLoading} style={{ padding: "9px 24px", borderRadius: "8px", border: "none", background: "#1e3a5f", color: "#fff", fontSize: "0.85rem", fontWeight: 600, cursor: "pointer", opacity: reportLoading ? 0.6 : 1 }}>
          {reportLoading ? "Generating..." : "Generate"}
        </button>
      </div>
    </div>
    {reportData && (
      <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "20px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
          <h3 style={{ margin: 0, fontSize: "1rem", fontWeight: 600 }}>{reportData.label || "Report"}</h3>
          <span style={{ fontSize: "0.75rem", color: "#94a3b8" }}>Generated: {reportData.generatedAt ? new Date(reportData.generatedAt).toLocaleString() : "-"}</span>
        </div>
        {reportData.error ? <div style={{ color: "#dc2626", padding: "20px", textAlign: "center" }}>{reportData.error}</div> :
          !reportData.data || (Array.isArray(reportData.data) && reportData.data.length === 0) ?
            <div style={{ padding: "20px", textAlign: "center", color: "#94a3b8" }}>No data for this report</div> :
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr>{Object.keys(Array.isArray(reportData.data) ? reportData.data[0] || {} : {}).map(k => <th key={k} style={thStyle}>{k.replace(/([A-Z])/g, " $1").replace(/^./, s => s.toUpperCase())}</th>)}</tr></thead>
                <tbody>{(Array.isArray(reportData.data) ? reportData.data : []).map((row, i) => <tr key={i} style={{ background: i % 2 === 0 ? "#fff" : "#f8fafc" }}>{Object.values(row).map((v, j) => <td key={j} style={tdStyle}>{typeof v === "number" && reportType !== "seller" ? f(v) : typeof v === "number" ? v : v != null ? String(v) : "-"}</td>)}</tr>)}</tbody>
              </table>
            </div>
        }
      </div>
    )}
  </div>
);

export default ExecutiveDashboard;
