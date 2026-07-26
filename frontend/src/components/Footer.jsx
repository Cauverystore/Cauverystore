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
          <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "0.75rem" }}>
            <img src="/images/logo.jpg" alt="" style={{ height: "1.1rem", width: "auto" }} />
            <span style={{ fontSize: "1.1rem", fontWeight: 700, color: "#fff" }}>Cauvery Store</span>
          </div>
          <h4 style={{ color: "#fff", fontSize: "0.85rem", fontWeight: 600, marginBottom: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
            Get to Know Us
          </h4>
          <div style={{ display: "flex", flexDirection: "column", gap: "0.4rem" }}>
            <Link to="/about" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>About Cauvery Store</Link>
            <Link to="/contact" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Contact Us</Link>
            <Link to="/help" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Help Center</Link>
            <Link to="/offers" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Offers & Deals</Link>
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
            <Link to="/refund-policy" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Returns, Refunds & FAQ</Link>
            <Link to="/policies" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Privacy & Terms</Link>
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
            <Link to="/contact" style={{ color: "#94a3b8", textDecoration: "none", fontSize: "0.85rem" }}>Partner Support</Link>
          </div>
        </div>

        {/* Connect With Us */}
        <div className="sn-footer-col">
          <h4 style={{ color: "#fff", fontSize: "0.85rem", fontWeight: 600, marginBottom: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
            Connect With Us
          </h4>
          <div style={{ display: "flex", gap: "0.5rem", marginBottom: "0.75rem" }}>
            {[
              { name: "Facebook", icon: <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M18 2h-3a5 5 0 0 0-5 5v3H7v4h3v8h4v-8h3l1-4h-4V7a1 1 0 0 1 1-1h3z"/></svg>, url: "https://facebook.com/cauverystore" },
              { name: "Twitter", icon: <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/></svg>, url: "https://x.com/cauverystore" },
              { name: "Instagram", icon: <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><rect x="2" y="2" width="20" height="20" rx="5" ry="5"/><path d="M16 11.37A4 4 0 1 1 12.63 8 4 4 0 0 1 16 11.37z"/><line x1="17.5" y1="6.5" x2="17.51" y2="6.5"/></svg>, url: "https://instagram.com/cauverystore" },
              { name: "YouTube", icon: <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="9.75 15.02 15.5 11.75 9.75 8.48 9.75 15.02"/><path d="M19.91 4.36c-1.07-.4-5.52-.86-7.91-.86s-6.84.46-7.91.86A2.56 2.56 0 0 0 2 6.76v10.48a2.56 2.56 0 0 0 2.09 2.4c1.07.4 5.52.86 7.91.86s6.84-.46 7.91-.86A2.56 2.56 0 0 0 22 17.24V6.76a2.56 2.56 0 0 0-2.09-2.4z"/></svg>, url: "https://youtube.com/@cauverystore" }
            ].map(({ name, icon, url }) => (
              <a key={name} href={url} target="_blank" rel="noopener noreferrer" aria-label={name} style={{
                display: "flex", alignItems: "center", justifyContent: "center",
                width: 32, height: 32, borderRadius: "50%",
                background: "rgba(255,255,255,0.08)", color: "#94a3b8",
                textDecoration: "none",
                transition: "background 0.2s, color 0.2s"
              }}
              onMouseEnter={(e) => { e.target.style.background = "var(--color-primary)"; e.target.style.color = "#fff"; }}
              onMouseLeave={(e) => { e.target.style.background = "rgba(255,255,255,0.08)"; e.target.style.color = "#94a3b8"; }}
              >
                {icon}
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
