import React from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const PAGE_TITLES = {
  "/admin": "Dashboard",
  "/admin/dashboard": "Dashboard",
  "/admin/analytics": "Analytics",
  "/admin/products": "Products",
  "/admin/products/add": "Add Product",
  "/admin/orders": "Orders",
  "/admin/refunds": "Refunds",
  "/admin/users": "Users",
  "/admin/customers": "Customers",
  "/admin/categories": "Categories",
  "/admin/brands": "Brands",
  "/admin/inventory": "Inventory",
  "/admin/coupons": "Coupons",
  "/admin/shipping": "Shipping",
  "/admin/reviews": "Reviews",
  "/admin/qna": "Q&A",
  "/admin/content": "Content",
  "/admin/notifications": "Notifications",
  "/admin/reports": "Reports",
  "/admin/audit": "Audit Logs",
  "/admin/executive-dashboard": "Executive Panel",
  "/admin/settings": "Settings",
  "/admin/product-approvals": "Product Approvals",
  "/admin/seller-approvals": "Seller Approvals",
};

const AdminNavbar = () => {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { logout, user, role } = useAuth();

  const title = Object.entries(PAGE_TITLES).find(([path]) => pathname.startsWith(path))?.[1] || "Dashboard";

  const handleLogout = async () => { await logout(); navigate("/admin/login"); };

  return (
    <header style={{
      display: "flex", alignItems: "center", justifyContent: "space-between",
      height: "56px", padding: "0 24px", background: "#fff",
      borderBottom: "1px solid #e2e8f0", flexShrink: 0
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
        <span style={{ fontSize: "1rem", fontWeight: 600, color: "#1e293b" }}>{title}</span>
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
        <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
          <div style={{
            width: "32px", height: "32px", borderRadius: "50%",
            background: "var(--color-primary, #0E5C5C)", color: "#fff",
            display: "flex", alignItems: "center", justifyContent: "center",
            fontSize: "0.75rem", fontWeight: 700
          }}>
            {(user?.fullName?.[0] || user?.username?.[0] || "A").toUpperCase()}
          </div>
          <div style={{ display: "flex", flexDirection: "column", lineHeight: 1.2 }}>
            <span style={{ fontSize: "0.82rem", fontWeight: 600, color: "#1e293b" }}>
              {user?.fullName || user?.username || "Admin"}
            </span>
            <span style={{ fontSize: "0.7rem", color: "#94a3b8" }}>{role}</span>
          </div>
        </div>
        <button onClick={handleLogout} style={{
          background: "none", border: "1px solid #e2e8f0", padding: "6px 16px",
          borderRadius: "6px", cursor: "pointer", fontSize: "0.85rem", color: "#475569"
        }}>Logout</button>
      </div>
    </header>
  );
};

export default AdminNavbar;
