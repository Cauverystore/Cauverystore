import React from "react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { canAccessModule } from "../utils/rolePermissions";
import {
  LayoutDashboard, BarChart3, Package, Tags, Building2, LayoutList,
  TrendingDown, Warehouse, Truck, FileText, Undo2, Zap,
  ShoppingCart, CreditCard, Users, ClipboardList, Ticket, Ship,
  Star, HelpCircle, FileEdit, Bell, FileSpreadsheet, FolderArchive,
  Image, Mail, Trophy, ArrowUpDown, ShieldCheck, Settings, Shield,
} from "lucide-react";

const allLinks = [
  { to: "/admin", label: "Dashboard", icon: LayoutDashboard, end: true, module: null },
  { to: "/admin/analytics", label: "Analytics", icon: BarChart3, module: "analytics" },
  { to: "/admin/products", label: "Products", icon: Package, module: "products" },
  { to: "/admin/categories", label: "Categories", icon: Tags, module: "categories" },
  { to: "/admin/brands", label: "Brands", icon: Building2, module: "brands" },
  { to: "/admin/product-dashboard", label: "Product Dashboard", icon: LayoutList, module: "productDashboard" },
  { to: "/admin/inventory-dashboard", label: "Inventory Dashboard", icon: TrendingDown, module: "inventoryDashboard" },
  { to: "/admin/warehouses", label: "Warehouses", icon: Warehouse, module: "warehouses" },
  { to: "/admin/suppliers", label: "Suppliers", icon: Truck, module: "suppliers" },
  { to: "/admin/purchase-orders", label: "Purchase Orders", icon: FileText, module: "purchaseOrders" },
  { to: "/admin/returns", label: "Returns", icon: Undo2, module: "returns" },
  { to: "/admin/bulk-operations", label: "Bulk Operations", icon: Zap, module: "bulkOperations" },
  { to: "/admin/orders", label: "Orders", icon: ShoppingCart, module: "orders" },
  { to: "/admin/refunds", label: "Refunds", icon: CreditCard, module: "refunds" },
  { to: "/admin/customers", label: "Customers", icon: Users, module: "customers" },
  { to: "/admin/inventory", label: "Inventory", icon: ClipboardList, module: "inventory" },
  { to: "/admin/coupons", label: "Coupons", icon: Ticket, module: "coupons" },
  { to: "/admin/shipping", label: "Shipping", icon: Ship, module: "shipping" },
  { to: "/admin/reviews", label: "Reviews", icon: Star, module: "reviews" },
  { to: "/admin/qna", label: "Q&A", icon: HelpCircle, module: "qna" },
  { to: "/admin/content", label: "Content", icon: FileEdit, module: "content" },
  { to: "/admin/notifications", label: "Notifications", icon: Bell, module: "notifications" },
  { to: "/admin/reports", label: "Reports", icon: FileSpreadsheet, module: "reports" },
  { to: "/admin/audit", label: "Audit Logs", icon: FolderArchive, module: "audit" },
  { to: "/admin/banners", label: "Banners", icon: Image, module: "banners" },
  { to: "/admin/faq", label: "FAQs", icon: HelpCircle, module: "faq" },
  { to: "/admin/support-tickets", label: "Support Tickets", icon: Ticket, module: "support" },
  { to: "/admin/newsletter", label: "Newsletter", icon: Mail, module: "newsletter" },
  { to: "/admin/loyalty", label: "Loyalty", icon: Trophy, module: "loyalty" },
  { to: "/admin/stock-movements", label: "Stock Movements", icon: ArrowUpDown, module: "stockMovements" },
  { to: "/admin/executive-dashboard", label: "Executive Panel", icon: ShieldCheck, module: "executiveDashboard" },
  { to: "/admin/settings", label: "Settings", icon: Settings, module: "settings" },
  { to: "/super-admin", label: "Super Admin", icon: Shield, module: "superAdmin" },
];

const AdminSidebar = () => {
  const { role } = useAuth();
  const links = allLinks.filter(l => !l.module || canAccessModule(role, l.module));
  const labelText = role === "EXECUTIVE" ? "Executive Panel" : role === "SUPER_ADMIN" ? "Super Admin" : "Admin Panel";

  return (
  <aside style={{ position: "fixed", top: 0, left: 0, width: "240px", height: "100vh", background: "#0f172a", color: "#fff", display: "flex", flexDirection: "column", zIndex: 100 }}>
    <div style={{ padding: "20px 20px 16px", borderBottom: "1px solid #1e293b" }}>
      <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
        <img src="/images/logo.jpg" alt="" style={{ height: "1.15rem", width: "auto" }} />
        <div>
          <div style={{ fontSize: "1.15rem", fontWeight: 700, color: "var(--color-primary, #0E5C5C)" }}>Cauvery Store</div>
          <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "1px" }}>{labelText}</div>
        </div>
      </div>
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
            background: isActive ? "#1e293b" : "transparent", borderLeft: isActive ? "3px solid var(--color-primary, #0E5C5C)" : "3px solid transparent",
            transition: "all 0.15s",
          })}
        >
          <l.icon size={18} />
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
