import React, { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import api from "../api/axios";
import { useAuth } from "../context/AuthContext";
import { STORE_KEY } from "../context/SellerStoreContext";

// Where an emailed team invitation lands. Shows which store and role, then asks the person to
// sign in (or sign up) with the invited email and accept.
const card = { maxWidth: 480, margin: "3rem auto", background: "#fff", padding: "2rem", borderRadius: 12, boxShadow: "0 4px 20px rgba(0,0,0,0.06)", textAlign: "center" };
const btn = { display: "inline-block", padding: "0.65rem 1.4rem", background: "#0E5C5C", color: "#fff", border: "none", borderRadius: 8, cursor: "pointer", fontSize: "0.95rem", fontWeight: 600, textDecoration: "none" };

const SellerInvite = () => {
  const [params] = useSearchParams();
  const token = params.get("token") || "";
  const { isAuthenticated, user, refreshToken } = useAuth();
  const navigate = useNavigate();
  const [info, setInfo] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get("/api/seller/team/invitations/lookup", { params: { token } })
      .then((r) => setInfo(r.data))
      .catch(() => setInfo({ valid: false, reason: "This invitation could not be checked. Please try again." }));
  }, [token]);

  // Sign-in returns here afterwards (the login page follows ?redirect=).
  const here = `/seller/invite?token=${encodeURIComponent(token)}`;

  const accept = async () => {
    setBusy(true); setError("");
    try {
      const r = await api.post("/api/seller/team/invitations/accept", { token });
      try { localStorage.setItem(STORE_KEY, String(r.data.sellerId)); } catch { /* storage blocked */ }
      await refreshToken().catch(() => {});
      navigate("/seller/dashboard");
    } catch (e) {
      setError(e.response?.data?.error || "The invitation could not be accepted.");
    }
    setBusy(false);
  };

  if (!info) return <div style={card}>Checking your invitation...</div>;

  return (
    <div style={{ padding: "0 16px" }}>
      <div style={card}>
        {info.valid ? (
          <>
            <h1 style={{ fontSize: "1.4rem", margin: "0 0 0.5rem" }}>Join {info.storeName}</h1>
            <p style={{ color: "#4b5563" }}>You've been invited to help run <strong>{info.storeName}</strong> on Cauvery Store as <strong>{info.roleName}</strong>.</p>
            <p style={{ color: "#6b7280", fontSize: "0.85rem" }}>The invitation is for {info.email}.</p>
            {error && <p style={{ color: "#dc2626", fontSize: "0.9rem" }}>{error}</p>}
            {isAuthenticated ? (
              <>
                <p style={{ color: "#6b7280", fontSize: "0.85rem" }}>Signed in as {user?.email}.</p>
                <button type="button" onClick={accept} disabled={busy} style={{ ...btn, opacity: busy ? 0.7 : 1 }}>{busy ? "Joining..." : "Accept invitation"}</button>
              </>
            ) : (
              <div style={{ display: "flex", gap: 10, justifyContent: "center", flexWrap: "wrap", marginTop: "1rem" }}>
                <Link to={`/login?redirect=${encodeURIComponent(here)}`} style={btn}>Sign in</Link>
                <Link to={`/register?redirect=${encodeURIComponent(here)}`} style={{ ...btn, background: "#fff", color: "#0E5C5C", border: "1px solid #0E5C5C" }}>Create an account</Link>
              </div>
            )}
          </>
        ) : (
          <>
            <h1 style={{ fontSize: "1.3rem", margin: "0 0 0.5rem" }}>Invitation not available</h1>
            <p style={{ color: "#4b5563" }}>{info.reason}</p>
            <Link to="/" style={{ ...btn, marginTop: "1rem" }}>Go to the store</Link>
          </>
        )}
      </div>
    </div>
  );
};

export default SellerInvite;
