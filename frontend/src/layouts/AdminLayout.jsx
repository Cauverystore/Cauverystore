import React, { useState } from "react";
import { Outlet } from "react-router-dom";
import AdminSidebar from "./AdminSidebar";
import AdminNavbar from "./AdminNavbar";
import { ToastProvider } from "../admin/context/ToastContext";
import "../styles/admin.css";
import "../styles/design-system.css";

const AdminLayout = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <ToastProvider>
      <div style={{ display: "flex", minHeight: "100vh", background: "#f1f5f9" }}>
        <AdminSidebar open={sidebarOpen} onNavigate={() => setSidebarOpen(false)} />
        {sidebarOpen && <div className="cd-admin-overlay" onClick={() => setSidebarOpen(false)} />}
        <div className="cd-admin-content" style={{ flex: 1, display: "flex", flexDirection: "column", marginLeft: "240px" }}>
          <AdminNavbar onMenuClick={() => setSidebarOpen(o => !o)} />
          <main style={{ padding: "24px", flex: 1 }}>
            <Outlet />
          </main>
        </div>
      </div>
    </ToastProvider>
  );
};

export default AdminLayout;