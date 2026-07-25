import React, { useState } from "react";
import { Link } from "react-router-dom";
import api from "../api/axios";

const Footer = () => {
  const [email, setEmail] = useState("");
  const [msg, setMsg] = useState("");

  const handleSubscribe = async (e) => {
    e.preventDefault();
    try {
      const res = await api.post("/api/newsletter/subscribe", { email });
      setMsg(res.data.message || "Subscribed!");
      setEmail("");
      setTimeout(() => setMsg(""), 3000);
    } catch { setMsg("Failed to subscribe"); setTimeout(() => setMsg(""), 3000); }
  };

  return (
  <>
    {/* Newsletter */}
    <section style={{
      background: "var(--green-800, #166534)", padding: "2.5rem 1.5rem",
      color: "#fff"
    }}>
      <div style={{
        maxWidth: "var(--container-max, 1200px)", margin: "0 auto",
        display: "flex", justifyContent: "space-between", alignItems: "center",
        gap: "2rem", flexWrap: "wrap"
      }}>
        <div style={{ flex: 1, minWidth: "250px" }}>
          <h3 style={{ fontSize: "1.2rem", fontWeight: 700, margin: "0 0 0.35rem" }}>Stay in the loop</h3>
          <p style={{ fontSize: "0.85rem", opacity: 0.85, margin: 0 }}>
            Get exclusive deals, new launches, and personalized recommendations straight to your inbox.
          </p>
          {msg && <p style={{ fontSize:"0.8rem", marginTop:"0.5rem", color:"#fbbf24" }}>{msg}</p>}
        </div>
        <form onSubmit={handleSubscribe} style={{
          display: "flex", gap: "0.5rem", flexShrink: 0, width: "100%", maxWidth: "420px"
        }}>
          <input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="Enter your email address" required style={{
            flex: 1, padding: "0.6rem 0.85rem", border: "none", borderRadius: "6px",
            fontSize: "0.85rem", outline: "none"
          }} />
          <button type="submit" style={{
            padding: "0.6rem 1.25rem", background: "#fa8900", color: "#fff",
            border: "none", borderRadius: "6px", fontWeight: 600, fontSize: "0.85rem",
            cursor: "pointer", whiteSpace: "nowrap"
          }}>Subscribe</button>
        </form>
      </div>
    </section>

    {/* Main Footer */}
    <footer style={{
      background: "#0f172a", color: "#94a3b8", padding: "2.5rem 1.5rem 1.5rem"
    }}>
      <div style={{
        maxWidth: "var(--container-max, 1200px)", margin: "0 auto",
        display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))",
        gap: "2rem"
      }}>
        {/* Get to Know Us */}
        <div className="sn-footer-col">
          <h4 style={{ color: "#fff", fontSize: "0.85rem", fontWeight: 600, marginBottom: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
            Get to Know Us
          </h4>
          <div style={{ display: "flex", flexDirection: "column", gap: "0.4rem" }}>
            <Link to="/about" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>About Cauvery Store</Link>
            <Link to="/about" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Careers</Link>
            <Link to="/about" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Press Releases</Link>
            <Link to="/about" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Sustainability</Link>
            <Link to="/about" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Investor Relations</Link>
          </div>
        </div>

        {/* Customer Service */}
        <div className="sn-footer-col">
          <h4 style={{ color: "#fff", fontSize: "0.85rem", fontWeight: 600, marginBottom: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
            Customer Service
          </h4>
          <div style={{ display: "flex", flexDirection: "column", gap: "0.4rem" }}>
            <Link to="/contact" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Contact Us</Link>
            <Link to="/orders" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Your Orders</Link>
            <Link to="/orders" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Returns & Refunds</Link>
            <Link to="/contact" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Shipping Info</Link>
            <Link to="/contact" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>FAQs</Link>
          </div>
        </div>

        {/* Seller Services */}
        <div className="sn-footer-col">
          <h4 style={{ color: "#fff", fontSize: "0.85rem", fontWeight: 600, marginBottom: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
            Seller Services
          </h4>
          <div style={{ display: "flex", flexDirection: "column", gap: "0.4rem" }}>
            <Link to="/seller/dashboard" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Sell on Cauvery Store</Link>
            <Link to="/seller/dashboard" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Seller Dashboard</Link>
            <Link to="/about" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Fulfillment</Link>
            <Link to="/about" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Advertise</Link>
            <Link to="/about" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Pricing</Link>
          </div>
        </div>

        {/* Connect With Us */}
        <div className="sn-footer-col">
          <h4 style={{ color: "#fff", fontSize: "0.85rem", fontWeight: 600, marginBottom: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
            Connect With Us
          </h4>
          <div style={{ display: "flex", gap: "0.5rem", marginBottom: "0.75rem" }}>
            {["Facebook", "Twitter", "Instagram", "YouTube"].map((name) => (
              <a key={name} href="#" aria-label={name} style={{
                display: "flex", alignItems: "center", justifyContent: "center",
                width: 32, height: 32, borderRadius: "50%",
                background: "rgba(255,255,255,0.08)", color: "#94a3b8",
                textDecoration: "none", fontSize: "0.8rem", fontWeight: 700,
                transition: "background 0.2s, color 0.2s"
              }}
              onMouseEnter={(e) => { e.target.style.background = "var(--color-primary)"; e.target.style.color = "#fff"; }}
              onMouseLeave={(e) => { e.target.style.background = "rgba(255,255,255,0.08)"; e.target.style.color = "#94a3b8"; }}
              >
                {name === "Facebook" ? "f" : name === "Twitter" ? "??" : name === "Instagram" ? "ig" : "yt"}
              </a>
            ))}
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "0.35rem" }}>
            <span style={{
              display: "inline-flex", alignItems: "center", gap: "0.35rem",
              padding: "0.3rem 0.6rem", border: "1px solid rgba(255,255,255,0.15)",
              borderRadius: "6px", fontSize: "0.75rem", color: "#94a3b8", cursor: "pointer", width: "fit-content"
            }}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="5" y="2" width="14" height="20" rx="2" ry="2"/><line x1="12" y1="18" x2="12" y2="18"/></svg>
              App Store
            </span>
            <span style={{
              display: "inline-flex", alignItems: "center", gap: "0.35rem",
              padding: "0.3rem 0.6rem", border: "1px solid rgba(255,255,255,0.15)",
              borderRadius: "6px", fontSize: "0.75rem", color: "#94a3b8", cursor: "pointer", width: "fit-content"
            }}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>
              Google Play
            </span>
          </div>
        </div>
      </div>

      {/* Bottom Bar */}
      <div style={{
        maxWidth: "var(--container-max, 1200px)", margin: "1.5rem auto 0",
        paddingTop: "1rem", borderTop: "1px solid #1e293b",
        display: "flex", justifyContent: "space-between", alignItems: "center",
        flexWrap: "wrap", gap: "1rem", fontSize: "0.8rem"
      }}>
        <div style={{ display: "flex", gap: "0.75rem", alignItems: "center", color: "#64748b" }}>
          <span>Visa</span><span style={{ opacity: 0.3 }}>|</span>
          <span>MC</span><span style={{ opacity: 0.3 }}>|</span>
          <span>Amex</span><span style={{ opacity: 0.3 }}>|</span>
          <span>UPI</span><span style={{ opacity: 0.3 }}>|</span>
          <span>Net Banking</span>
        </div>
        <p style={{ margin: 0, color: "#64748b" }}>
          &copy; {new Date().getFullYear()} Cauvery Store. All rights reserved.
        </p>
      </div>
    </footer>

    {/* Back to Top */}
    <button onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })} title="Back to top" aria-label="Back to top" style={{
      position: "fixed", bottom: "80px", right: "1.5rem", zIndex: 50,
      width: 40, height: 40, borderRadius: "50%", background: "var(--color-primary, #16a34a)",
      color: "#fff", border: "none", cursor: "pointer", display: "flex",
      alignItems: "center", justifyContent: "center", boxShadow: "0 2px 8px rgba(0,0,0,0.2)"
    }}>
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
        <path d="M18 15l-6-6-6 6"/>
      </svg>
    </button>
  </>
);
};

export default Footer;
