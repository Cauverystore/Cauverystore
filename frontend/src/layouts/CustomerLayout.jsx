import React, { useEffect } from "react";
import { Outlet, useLocation } from "react-router-dom";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import MobileBottomBar from "../components/MobileBottomBar";

const CustomerLayout = () => {
  const { pathname } = useLocation();
  useEffect(() => { window.scrollTo({ top: 0, behavior: "auto" }); }, [pathname]);

  return (
    <>
      <a href="#main-content" className="skip-link" style={{ position: "absolute", left: "-9999px", top: 0, background: "#0E5C5C", color: "#fff", padding: "8px 16px", zIndex: 10000, borderRadius: "0 0 8px 0", fontWeight: 600 }} onFocus={(e) => (e.target.style.left = "0")} onBlur={(e) => (e.target.style.left = "-9999px")}>Skip to main content</a>
      <Navbar />
      <main id="main-content" style={{ minHeight: "70vh" }}>
        <Outlet />
      </main>
      <Footer />
      <MobileBottomBar />
    </>
  );
};
export default CustomerLayout;
