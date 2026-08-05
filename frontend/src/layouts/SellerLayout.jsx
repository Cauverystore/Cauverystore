import React, { useState } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { Menu } from "lucide-react";
import SellerSidebar from "./SellerSidebar";
import { useAuth } from "../context/AuthContext";
import "../styles/seller-layout.css";

const PAGE_TITLES = [
  ["/seller/products/add", "Add Product"],
  ["/seller/products/edit", "Edit Product"],
  ["/seller/products/bulk-upload", "Bulk Upload"],
  ["/seller/products", "Products"],
  ["/seller/inventory", "Inventory"],
  ["/seller/orders", "Orders & Returns"],
  ["/seller/analytics", "Analytics"],
  ["/seller/payouts", "Payouts"],
  ["/seller/gst-invoices", "GST Invoices"],
  ["/seller/gst-compliance", "GST Compliance"],
  ["/seller/store", "Store Profile"],
  ["/seller/dashboard", "Dashboard"],
];

const SellerLayout = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { user } = useAuth();

  // Longest-prefix wins, so /seller/products/add doesn't resolve to "Products".
  const title = PAGE_TITLES.find(([path]) => pathname.startsWith(path))?.[1] || "Seller Centre";

  return (
    <div style={{ display: "flex", minHeight: "100vh", background: "#f1f5f9" }}>
      <SellerSidebar open={sidebarOpen} onNavigate={() => setSidebarOpen(false)} />
      {sidebarOpen && <div className="cd-seller-overlay" onClick={() => setSidebarOpen(false)} />}

      <div className="cd-seller-content" style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0, marginLeft: "240px" }}>
        <header
          style={{
            display: "flex", alignItems: "center", justifyContent: "space-between",
            height: "56px", padding: "0 24px", background: "#fff",
            borderBottom: "1px solid #e2e8f0", flexShrink: 0,
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
            <button
              className="cd-seller-hamburger"
              onClick={() => setSidebarOpen((o) => !o)}
              aria-label="Toggle menu"
              aria-expanded={sidebarOpen}
              style={{ background: "none", border: "none", cursor: "pointer", padding: "4px", color: "#1e293b" }}
            >
              <Menu size={22} />
            </button>
            <span style={{ fontSize: "1rem", fontWeight: 600, color: "#1e293b" }}>{title}</span>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
            <div
              style={{
                width: "32px", height: "32px", borderRadius: "50%",
                background: "var(--color-primary, #0E5C5C)", color: "#fff",
                display: "flex", alignItems: "center", justifyContent: "center",
                fontSize: "0.75rem", fontWeight: 700,
              }}
            >
              {(user?.fullName?.[0] || user?.username?.[0] || "S").toUpperCase()}
            </div>
            <button
              onClick={() => navigate("/logout")}
              style={{
                background: "none", border: "1px solid #e2e8f0", padding: "6px 16px",
                borderRadius: "6px", cursor: "pointer", fontSize: "0.85rem", color: "#475569",
              }}
            >
              Logout
            </button>
          </div>
        </header>

        {/* No padding here on purpose: these pages predate this shell and already set their
            own page padding, so adding more would double it. */}
        <main className="cd-seller-main" style={{ flex: 1, minWidth: 0 }}>
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default SellerLayout;
