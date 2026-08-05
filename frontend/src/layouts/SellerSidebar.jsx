import React from "react";
import { NavLink } from "react-router-dom";
import {
  LayoutDashboard, Package, PlusCircle, ClipboardList, ShoppingCart,
  BarChart3, Wallet, FileText, ShieldCheck, Store, ArrowLeft,
} from "lucide-react";

const links = [
  { to: "/seller/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { to: "/seller/products", label: "Products", icon: Package, end: true },
  { to: "/seller/products/add", label: "Add Product", icon: PlusCircle },
  { to: "/seller/inventory", label: "Inventory", icon: ClipboardList },
  { to: "/seller/orders", label: "Orders & Returns", icon: ShoppingCart },
  { to: "/seller/analytics", label: "Analytics", icon: BarChart3 },
  { to: "/seller/payouts", label: "Payouts", icon: Wallet },
  { to: "/seller/gst-invoices", label: "GST Invoices", icon: FileText },
  { to: "/seller/gst-compliance", label: "GST Compliance", icon: ShieldCheck },
  { to: "/seller/store", label: "Store Profile", icon: Store },
];

const SellerSidebar = ({ open, onNavigate }) => (
  <aside
    className={`cd-seller-sidebar${open ? " cd-open" : ""}`}
    style={{
      position: "fixed", top: 0, left: 0, width: "240px", height: "100vh",
      background: "#0f172a", color: "#fff", display: "flex", flexDirection: "column", zIndex: 100,
    }}
  >
    <div style={{ padding: "20px 20px 16px", borderBottom: "1px solid #1e293b" }}>
      <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
        <img src="/images/logo.jpg" alt="" style={{ height: "1.15rem", width: "auto" }} />
        <div>
          <div style={{ fontSize: "1.15rem", fontWeight: 700, color: "var(--color-primary, #0E5C5C)" }}>Cauvery Store</div>
          <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "1px" }}>Seller Centre</div>
        </div>
      </div>
    </div>

    <nav style={{ flex: 1, overflowY: "auto", padding: "8px 0" }}>
      {links.map((l) => (
        <NavLink
          key={l.to}
          to={l.to}
          end={l.end}
          onClick={onNavigate}
          style={({ isActive }) => ({
            display: "flex", alignItems: "center", gap: "12px", padding: "10px 20px",
            color: isActive ? "#fff" : "#94a3b8", textDecoration: "none", fontSize: "0.875rem",
            background: isActive ? "#1e293b" : "transparent",
            borderLeft: isActive ? "3px solid var(--color-primary, #0E5C5C)" : "3px solid transparent",
            transition: "all 0.15s",
          })}
        >
          <l.icon size={18} />
          <span>{l.label}</span>
        </NavLink>
      ))}
    </nav>

    {/* A seller is also a customer on this platform, so leaving the dashboard has to be
        one click away - without this the sidebar becomes a dead end for shopping. */}
    <div style={{ borderTop: "1px solid #1e293b", padding: "8px 0" }}>
      <NavLink
        to="/"
        onClick={onNavigate}
        style={{
          display: "flex", alignItems: "center", gap: "12px", padding: "10px 20px",
          color: "#94a3b8", textDecoration: "none", fontSize: "0.875rem",
        }}
      >
        <ArrowLeft size={18} />
        <span>Back to Store</span>
      </NavLink>
    </div>
  </aside>
);

export default SellerSidebar;
