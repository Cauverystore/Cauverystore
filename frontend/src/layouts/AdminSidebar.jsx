import React from "react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { canAccessModule } from "../utils/rolePermissions";

const allLinks = [
  { to: "/admin", label: "Dashboard", icon: "??", end: true, module: null },
  { to: "/admin/analytics", label: "Analytics", icon: "??", module: "analytics" },
  { to: "/admin/products", label: "Products", icon: "??", module: "products" },
  { to: "/admin/categories", label: "Categories", icon: "???", module: "categories" },
  { to: "/admin/brands", label: "Brands", icon: "??", module: "brands" },
  { to: "/admin/product-dashboard", label: "Product Dashboard", icon: "??", module: "productDashboard" },
  { to: "/admin/inventory-dashboard", label: "Inventory Dashboard", icon: "??", module: "inventoryDashboard" },
  { to: "/admin/warehouses", label: "Warehouses", icon: "??", module: "warehouses" },
  { to: "/admin/suppliers", label: "Suppliers", icon: "??", module: "suppliers" },
  { to: "/admin/purchase-orders", label: "Purchase Orders", icon: "??", module: "purchaseOrders" },
  { to: "/admin/returns", label: "Returns", icon: "??", module: "returns" },
  { to: "/admin/bulk-operations", label: "Bulk Operations", icon: "?", module: "bulkOperations" },
  { to: "/admin/orders", label: "Orders", icon: "??", module: "orders" },
  { to: "/admin/refunds", label: "Refunds", icon: "??", module: "refunds" },
  { to: "/admin/customers", label: "Customers", icon: "??", module: "customers" },
  { to: "/admin/inventory", label: "Inventory", icon: "??", module: "inventory" },
  { to: "/admin/coupons", label: "Coupons", icon: "??", module: "coupons" },
  { to: "/admin/shipping", label: "Shipping", icon: "??", module: "shipping" },
  { to: "/admin/reviews", label: "Reviews", icon: "?", module: "reviews" },
  { to: "/admin/qna", label: "Q&A", icon: "?", module: "qna" },
  { to: "/admin/content", label: "Content", icon: "??", module: "content" },
  { to: "/admin/notifications", label: "Notifications", icon: "??", module: "notifications" },
  { to: "/admin/reports", label: "Reports", icon: "??", module: "reports" },
  { to: "/admin/audit", label: "Audit Logs", icon: "??", module: "audit" },
  { to: "/admin/banners", label: "Banners", icon: "??", module: "banners" },
  { to: "/admin/faq", label: "FAQs", icon: "?", module: "faq" },
  { to: "/admin/support-tickets", label: "Support Tickets", icon: "?", module: "support" },
  { to: "/admin/newsletter", label: "Newsletter", icon: "??", module: "newsletter" },
  { to: "/admin/loyalty", label: "Loyalty", icon: "?", module: "loyalty" },
  { to: "/admin/stock-movements", label: "Stock Movements", icon: "?", module: "stockMovements" },
  { to: "/admin/executive-dashboard", label: "Executive Panel", icon: "??", module: "executiveDashboard" },
  { to: "/admin/settings", label: "Settings", icon: "??", module: "settings" },
  { to: "/super-admin", label: "Super Admin", icon: "???", module: "superAdmin" },
];

const AdminSidebar = () => {
  const { role } = useAuth();
  const links = allLinks.filter(l => !l.module || canAccessModule(role, l.module));
  const labelText = role === "EXECUTIVE" ? "Executive Panel" : role === "SUPER_ADMIN" ? "Super Admin" : "Admin Panel";

  return (
  <aside style={{ position: "fixed", top: 0, left: 0, width: "240px", height: "100vh", background: "#0f172a", color: "#fff", display: "flex", flexDirection: "column", zIndex: 100 }}>
    <div style={{ padding: "20px 20px 16px", borderBottom: "1px solid #1e293b" }}>
      <div style={{ fontSize: "1.25rem", fontWeight: 700, color: "#16a34a" }}>Cauvery Store</div>
      <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "2px" }}>{labelText}</div>
    </div>
    <nav style={{ flex: 1, overflowY: "auto", padding: "8px 0" }}>
      {links.map(l => (
        <NavLink
          key={l.to}
          to={l.to}
          end={l.end}
          style={({ isActive }) => ({
            display: "flex", alignItems: "center", gap: "12px", padding: "10px 20px",
            color: isActive ? "#fff" : "#94a3b8", textDecoration: "none", fontSize: "0.875rem",
            background: isActive ? "#1e293b" : "transparent", borderLeft: isActive ? "3px solid #16a34a" : "3px solid transparent",
            transition: "all 0.15s",
          })}
        >
          <span style={{ fontSize: "1.1rem" }}>{l.icon}</span>
          <span>{l.label}</span>
        </NavLink>
      ))}
    </nav>
    <div style={{ padding: "12px 20px", borderTop: "1px solid #1e293b", fontSize: "0.75rem", color: "#475569" }}>
      v1.0.0
    </div>
  </aside>
  );
};

export default AdminSidebar;
