import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import api from "../utils/axios";

const PasswordExpiryBanner = () => {
  const { isAuthenticated } = useAuth();
  const [info, setInfo] = useState(null);

  useEffect(() => {
    if (!isAuthenticated) {
      setInfo(null);
      return;
    }
    let cancelled = false;
    api.get("/api/auth/me")
      .then((res) => {
        if (cancelled) return;
        const expiresAt = res.data?.passwordExpiresAt;
        if (!expiresAt) return;
        const days = Math.ceil((new Date(expiresAt).getTime() - Date.now()) / 86400000);
        if (days <= 7) setInfo({ days });
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [isAuthenticated]);

  if (!info) return null;

  const expired = info.days <= 0;
  return (
    <div style={{
      position: "fixed", top: 0, left: 0, right: 0, zIndex: 9998,
      background: expired ? "#dc2626" : "#f59e0b",
      color: "#fff", textAlign: "center", padding: "0.5rem 1rem",
      fontSize: "0.85rem", fontWeight: 500
    }}>
      {expired ? "Your password has expired." : `Your password expires in ${info.days} day${info.days === 1 ? "" : "s"}.`}{" "}
      <Link to="/profile?tab=security" style={{ color: "#fff", textDecoration: "underline", fontWeight: 700 }}>
        Update password now
      </Link>
    </div>
  );
};

export default PasswordExpiryBanner;
