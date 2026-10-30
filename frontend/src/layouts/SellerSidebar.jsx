import React from "react";
import { NavLink } from "react-router-dom";
import {
  LayoutDashboard, Package, PlusCircle, ClipboardList, ShoppingCart,
  BarChart3, Wallet, FileText, ShieldCheck, Store, ArrowLeft, Users,
} from "lucide-react";
import { useSellerStore } from "../context/SellerStoreContext";
import useAltText from "../hooks/useAltText";

// perm: what a team member's role needs to see the item. ownerOnly: GST pages still read the
// signed-in person's own records, so they are for the account owner until they are moved over.
const links = [
  { to: "/seller/dashboard", label: "Dashboard", icon: LayoutDashboard, perm: "dashboard.view" },
  { to: "/seller/products", label: "Products", icon: Package, end: true, perm: "products.view" },
  { to: "/seller/products/add", label: "Add Product", icon: PlusCircle, perm: "products.manage" },
  { to: "/seller/inventory", label: "Inventory", icon: ClipboardList, perm: "products.view" },
  { to: "/seller/orders", label: "Orders & Returns", icon: ShoppingCart, perm: "orders.view" },
  { to: "/seller/analytics", label: "Analytics", icon: BarChart3, perm: "analytics.view" },
  { to: "/seller/payouts", label: "Payouts", icon: Wallet, perm: "payouts.view" },
  { to: "/seller/gst-invoices", label: "GST Invoices", icon: FileText, ownerOnly: true },
  { to: "/seller/gst-compliance", label: "GST Compliance", icon: ShieldCheck, ownerOnly: true },
  { to: "/seller/store", label: "Store Profile", icon: Store, perm: "store.manage" },
  { to: "/seller/team", label: "Team", icon: Users, perm: "team.manage" },
];

const StoreSwitcher = () => {
  const { stores, current, switchStore } = useSellerStore();
  if (!current) return null;
  return (
    <div style={{ padding: "10px 20px 12px", borderBottom: "1px solid #1e293b" }}>
      <div style={{ fontSize: "0.7rem", color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: 4 }}>Store</div>
      {stores.length > 1 ? (
        <select
          aria-label="Choose the store to work in"
          value={current.sellerId}
          onChange={(e) => switchStore(Number(e.target.value))}
          style={{ width: "100%", background: "#1e293b", color: "#fff", border: "1px solid #334155", borderRadius: 6, padding: "6px 8px", fontSize: "0.85rem" }}
        >
          {stores.map((s) => <option key={s.sellerId} value={s.sellerId}>{s.storeName}{s.owner ? "" : ` (${s.roleName})`}</option>)}
        </select>
      ) : (
        <div style={{ fontSize: "0.9rem", color: "#e2e8f0", fontWeight: 600 }}>{current.storeName}</div>
      )}
      {!current.owner && <div style={{ fontSize: "0.75rem", color: "#94a3b8", marginTop: 4 }}>Your role: {current.roleName}</div>}
    </div>
  );
};

const SellerSidebar = ({ open, onNavigate }) => {
  const altText = useAltText();
  const { current, can } = useSellerStore();
  const visible = links.filter((l) => (l.ownerOnly ? !current || current.owner : !l.perm || can(l.perm)));
  return (
  <aside
    className={`cd-seller-sidebar${open ? " cd-open" : ""}`}
    style={{
      position: "fixed", top: 0, left: 0, width: "240px", height: "100vh",
      background: "#0f172a", color: "#fff", display: "flex", flexDirection: "column", zIndex: 100,
    }}
  >
    <div style={{ padding: "20px 20px 16px", borderBottom: "1px solid #1e293b" }}>
      <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
        <img src="/images/logo.jpg" alt={altText.logo()} style={{ height: "1.15rem", width: "auto" }} />
        <div>
          <div style={{ fontSize: "1.15rem", fontWeight: 700, color: "var(--color-primary, #0E5C5C)" }}>Cauvery Store</div>
          <div style={{ fontSize: "0.75rem", color: "#64748b", marginTop: "1px" }}>Seller Centre</div>
        </div>
      </div>
    </div>

    <StoreSwitcher />

    <nav style={{ flex: 1, overflowY: "auto", padding: "8px 0" }}>
      {visible.map((l) => (
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
};

export default SellerSidebar;
