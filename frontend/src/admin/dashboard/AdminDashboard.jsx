import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Package, Users, Key, ClipboardList, CreditCard, Tags, BarChart3, HelpCircle, PlusCircle, ShieldCheck, PackageX, Store } from "lucide-react";
import api from "../../api/axios";
import "../../styles/adminDashboard.css";

const quickActions = [
  { to: "/admin/products", label: "Product Management", icon: Package, desc: "Add, edit, manage products", color: "#2563eb" },
  { to: "/admin/users", label: "User Management", icon: Users, desc: "View and manage all users", color: "#7c3aed" },
  { to: "/admin/users", label: "Access Control", icon: Key, desc: "Manage roles & permissions", color: "#dc2626" },
  { to: "/admin/orders", label: "Order Management", icon: ClipboardList, desc: "View and process orders", color: "#d97706" },
  { to: "/admin/refunds", label: "Refunds", icon: CreditCard, desc: "Handle refund requests", color: "#0891b2" },
  // Both of these were routed but linked from nowhere, so the only way in was to know the URL.
  { to: "/admin/returns", label: "Returns", icon: PackageX, desc: "Approve, inspect and refund returns", color: "#be123c" },
  { to: "/admin/seller-approvals", label: "Sellers", icon: Store, desc: "Approve, suspend and review sellers", color: "#0f766e" },
  { to: "/admin/categories", label: "Categories", icon: Tags, desc: "Organize product categories", color: "#059669" },
  { to: "/admin/inventory", label: "Inventory", icon: BarChart3, desc: "Stock and inventory tracking", color: "#4f46e5" },
  { to: "/admin/qna", label: "Q&A Management", icon: HelpCircle, desc: "Customer questions", color: "#ea580c" },
  { to: "/admin/products/add", label: "Add Product", icon: PlusCircle, desc: "Create a new product", color: "#16a34a" },
  { to: "/admin/executive-dashboard", label: "Executive Panel", icon: ShieldCheck, desc: "Executive operations", color: "#334155" },
];

const AdminDashboard = () => {
  const navigate = useNavigate();
  const [stats, setStats] = useState(null);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get("/api/admin/orders/dashboard").then(r => setStats(r.data)).catch(() => {
      Promise.all([
        api.get("/api/admin/orders").then(r => Array.isArray(r.data) ? r.data : []).catch(() => []),
        api.get("/api/admin/products/all").then(r => Array.isArray(r.data) ? r.data : []).catch(() => []),
        api.get("/api/admin/users").then(r => Array.isArray(r.data) ? r.data : []).catch(() => []),
      ]).then(([orders, products, users]) => {
        setStats({
          totalOrders: orders.length,
          totalRevenue: orders.filter(x => x.status !== "CANCELLED" && x.status !== "REFUNDED").reduce((s, x) => s + (x.totalAmount || 0), 0),
          totalProducts: products.length,
          totalUsers: users.length,
        });
      });
    });
    api.get("/api/admin/users").then(r => setUsers(Array.isArray(r.data) ? r.data.slice(0, 8) : [])).catch(() => {});
    setLoading(false);
  }, []);

  const handleRole = async (id, role) => {
    try {
      await api.put(`/api/admin/users/${id}/role?role=${role}`);
      const r = await api.get("/api/admin/users");
      setUsers(Array.isArray(r.data) ? r.data.slice(0, 8) : []);
    } catch { alert("Failed to update role"); }
  };

  const statCards = stats ? [
    { label: "Total Orders", value: stats.totalOrders, color: "#2563eb" },
    { label: "Total Revenue", value: "₹" + (stats.totalRevenue || 0).toLocaleString(), color: "#16a34a" },
    { label: "Total Products", value: stats.totalProducts, color: "#d97706" },
    { label: "Total Users", value: stats.totalUsers, color: "#7c3aed" },
  ] : [];

  return (
    <div className="admin-dashboard">
      <h1 style={{ fontSize: "1.5rem", fontWeight: 700, marginBottom: "1.5rem" }}>Admin Dashboard</h1>

      <div className="stats-grid">
        {statCards.map(c => (
          <div key={c.label} style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", padding: "1.25rem" }}>
            <div style={{ fontSize: "1.75rem", fontWeight: 800, color: c.color }}>{c.value}</div>
            <div style={{ fontSize: "0.85rem", color: "#6b7280", marginTop: "4px" }}>{c.label}</div>
          </div>
        ))}
      </div>

      <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "1rem", color: "#1e293b" }}>Quick Actions</h2>
      <div className="action-grid">
        {quickActions.map(a => (
          <div key={a.to + a.label} className="action-card" onClick={() => navigate(a.to)}>
            <div className="action-icon" style={{ background: a.color + "15", color: a.color }}><a.icon size={20} /></div>
            <div><div style={{ fontWeight: 600, fontSize: "0.9rem" }}>{a.label}</div><div style={{ fontSize: "0.75rem", color: "#6b7280", marginTop: "2px" }}>{a.desc}</div></div>
          </div>
        ))}
      </div>

      {users.length > 0 && (
        <div style={{ background: "#fff", border: "1px solid #e5e7eb", borderRadius: "10px", overflow: "hidden" }}>
          <div style={{ padding: "12px 16px", borderBottom: "1px solid #e5e7eb", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontWeight: 600, fontSize: "0.95rem" }}>Access Control — Recent Users</span>
            <button onClick={() => navigate("/admin/users")} style={{ background: "none", border: "1px solid #e2e8f0", padding: "4px 12px", borderRadius: "6px", cursor: "pointer", fontSize: "0.8rem", color: "#475569" }}>View All</button>
          </div>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem" }}>
            <thead><tr style={{ background: "#f9fafb" }}>
              <th style={{ textAlign: "left", padding: "10px 12px", fontWeight: 600, fontSize: "0.8rem", color: "#6b7280", textTransform: "uppercase" }}>Name</th>
              <th style={{ textAlign: "left", padding: "10px 12px", fontWeight: 600, fontSize: "0.8rem", color: "#6b7280", textTransform: "uppercase" }}>Email</th>
              <th style={{ textAlign: "left", padding: "10px 12px", fontWeight: 600, fontSize: "0.8rem", color: "#6b7280", textTransform: "uppercase" }}>Role</th>
              <th style={{ textAlign: "left", padding: "10px 12px", fontWeight: 600, fontSize: "0.8rem", color: "#6b7280", textTransform: "uppercase" }}>Status</th>
            </tr></thead>
            <tbody>
              {users.map(u => (
                <tr key={u.id || u._id} style={{ borderBottom: "1px solid #f3f4f6" }}>
                  <td style={{ padding: "10px 12px", color: "#1f2937" }}>{u.name || u.username || u.fullName}</td>
                  <td style={{ padding: "10px 12px", color: "#1f2937" }}>{u.email}</td>
                  <td style={{ padding: "10px 12px" }}>
                    <select value={u.role} onChange={e => handleRole(u.id || u._id, e.target.value)} style={{ padding: "0.2rem 0.4rem", fontSize: "0.8rem", borderRadius: "4px", border: "1px solid #d1d5db" }}>
                      <option value="USER">User</option><option value="ADMIN">Admin</option><option value="SELLER">Seller</option><option value="EXECUTIVE">Executive</option>
                    </select>
                  </td>
                  <td style={{ padding: "10px 12px" }}><span style={{ color: u.isBlocked || u.status === "BLOCKED" ? "#dc2626" : "#16a34a" }}>{u.isBlocked || u.status === "BLOCKED" ? "Blocked" : "Active"}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default AdminDashboard;