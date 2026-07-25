import React from "react";
import { Outlet } from "react-router-dom";
import AdminSidebar from "./AdminSidebar";
import AdminNavbar from "./AdminNavbar";
import { ToastProvider } from "../admin/context/ToastContext";
import "../styles/admin.css";

const AdminLayout = () => (
  <ToastProvider>
    <div style={{ display: "flex", minHeight: "100vh", background: "#f1f5f9" }}>
      <AdminSidebar />
      <div style={{ flex: 1, display: "flex", flexDirection: "column", marginLeft: "240px" }}>
        <AdminNavbar />
        <main style={{ padding: "24px", flex: 1 }}>
          <Outlet />
        </main>
      </div>
    </div>
  </ToastProvider>
);

export default AdminLayout;