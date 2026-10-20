import React, { useState } from "react";
import { Link } from "react-router-dom";
import api from "../api/axios";
import { OPEN_COOKIE_SETTINGS } from "./CookieBanner";

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
    } catch { setMsg("We couldn't sign you up just now. Please try again."); setTimeout(() => setMsg(""), 3000); }
  };

  return (
  <>
    {/* Newsletter */}
    <section style={{
      background: "var(--color-primary, #0E5C5C)", padding: "2.5rem 1.5rem",
      color: "#fff"
    }}>
      <div style={{
        maxWidth: "var(--container-max, 1200px)", margin: "0 auto",
        display: "flex", justifyContent: "space-between", alignItems: "center",
        gap: "2rem", flexWrap: "wrap"
      }}>
        <div style={{ flex: 1, minWidth: "250px" }}>
          <h3 style={{ fontFamily: "var(--font-display)", fontSize: "1.5rem", fontWeight: 500, letterSpacing: "-0.015em", margin: "0 0 0.35rem" }}>New arrivals and offers, in your inbox</h3>
          <p style={{ fontSize: "0.85rem", opacity: 0.85, margin: 0 }}>
            Hear about new products and offers first. Unsubscribe any time.
          </p>
          {msg && <p style={{ fontSize:"0.8rem", marginTop:"0.5rem", color:"#f1dfae" }}>{msg}</p>}
        </div>
        <form onSubmit={handleSubscribe} style={{
          display: "flex", gap: "0.5rem", flexShrink: 0, width: "100%", maxWidth: "420px"
        }}>
          <input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="Your email address" aria-label="Email address for newsletter" required style={{
            flex: 1, padding: "0.6rem 1rem", border: "none", borderRadius: "999px",
            fontSize: "0.85rem", outline: "none"
          }} />
          <button type="submit" style={{
            padding: "0.6rem 1.25rem", background: "#C8A24B", color: "#fff",
            border: "none", borderRadius: "999px", fontWeight: 600, fontSize: "0.85rem",
            cursor: "pointer", whiteSpace: "nowrap"
          }}>Subscribe</button>
        </form>
      </div>
    </section>

    {/* Main Footer */}
    <footer style={{
      background: "#0b2b2b", color: "#a9c2c0", padding: "2.5rem 1.5rem 1.5rem"
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
            <Link to="/about" style={{ color: "#a9c2c0", textDecoration: "none", fontSize: "0.85rem" }}>About Cauvery Store</Link>
            <Link to="/contact" style={{ color: "#a9c2c0", textDecoration: "none", fontSize: "0.85rem" }}>Contact Us</Link>
            <Link to="/faq" style={{ color: "#a9c2c0", textDecoration: "none", fontSize: "0.85rem" }}>Help Center</Link>
            <Link to="/offers" style={{ color: "#a9c2c0", textDecoration: "none", fontSize: "0.85rem" }}>Offers & Deals</Link>
          </div>
        </div>

        {/* Customer Service */}
        <div className="sn-footer-col">
          <h4 style={{ color: "#fff", fontSize: "0.85rem", fontWeight: 600, marginBottom: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
            Customer Service
          </h4>
          <div style={{ display: "flex", flexDirection: "column", gap: "0.4rem" }}>
            <Link to="/contact" style={{ color: "#a9c2c0", textDecoration: "none", fontSize: "0.85rem" }}>Contact Us</Link>
            <Link to="/faq" style={{ color: "#a9c2c0", textDecoration: "none", fontSize: "0.85rem" }}>FAQ</Link>
            <Link to="/orders" style={{ color: "#a9c2c0", textDecoration: "none", fontSize: "0.85rem" }}>Your Orders</Link>
            {/* One link per policy: payment partners and shoppers look for each by name. */}
            <Link to="/refund-policy" style={{ color: "#a9c2c0", textDecoration: "none", fontSize: "0.85rem" }}>Cancellations & Refunds</Link>
            <Link to="/shipping-policy" style={{ color: "#a9c2c0", textDecoration: "none", fontSize: "0.85rem" }}>Shipping Policy</Link>
            <Link to="/terms-and-conditions" style={{ color: "#a9c2c0", textDecoration: "none", fontSize: "0.85rem" }}>Terms & Conditions</Link>
            <Link to="/privacy-policy" style={{ color: "#a9c2c0", textDecoration: "none", fontSize: "0.85rem" }}>Privacy Policy</Link>
            <button type="button" onClick={() => window.dispatchEvent(new Event(OPEN_COOKIE_SETTINGS))} style={{ color: "#a9c2c0", background: "none", border: "none", padding: 0, textAlign: "left", cursor: "pointer", fontSize: "0.85rem", fontFamily: "inherit" }}>Cookie Settings</button>
          </div>
        </div>

        {/* Seller Services */}
        <div className="sn-footer-col">
          <h4 style={{ color: "#fff", fontSize: "0.85rem", fontWeight: 600, marginBottom: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
            Seller Services
          </h4>
          <div style={{ display: "flex", flexDirection: "column", gap: "0.4rem" }}>
            <Link to="/seller/register" style={{ color: "#a9c2c0", textDecoration: "none", fontSize: "0.85rem" }}>Sell on Cauvery Store</Link>
            <Link to="/seller/dashboard" style={{ color: "#a9c2c0", textDecoration: "none", fontSize: "0.85rem" }}>Seller Dashboard</Link>
            <Link to="/contact" style={{ color: "#a9c2c0", textDecoration: "none", fontSize: "0.85rem" }}>Partner Support</Link>
          </div>
        </div>

        {/* Connect With Us */}
        <div className="sn-footer-col">
          <h4 style={{ color: "#fff", fontSize: "0.85rem", fontWeight: 600, marginBottom: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
            Connect With Us
          </h4>
          <div style={{ display: "flex", gap: "0.5rem", marginBottom: "0.75rem" }}>
            {[
              { name: "Facebook", icon: <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M18 2h-3a5 5 0 0 0-5 5v3H7v4h3v8h4v-8h3l1-4h-4V7a1 1 0 0 1 1-1h3z"/></svg>, url: "https://www.facebook.com/profile.php?id=61587653639339" },
              { name: "Instagram", icon: <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="2" width="20" height="20" rx="5" ry="5"/><circle cx="12" cy="12" r="4"/><line x1="17.5" y1="6.5" x2="17.51" y2="6.5"/></svg>, url: "https://www.instagram.com/cauverystore.in/" },
              { name: "YouTube", icon: <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path fillRule="evenodd" d="M23.5 6.19a3.02 3.02 0 0 0-2.12-2.14C19.5 3.55 12 3.55 12 3.55s-7.5 0-9.38.5A3.02 3.02 0 0 0 .5 6.19C0 8.07 0 12 0 12s0 3.93.5 5.81a3.02 3.02 0 0 0 2.12 2.14c1.88.5 9.38.5 9.38.5s7.5 0 9.38-.5a3.02 3.02 0 0 0 2.12-2.14C24 15.93 24 12 24 12s0-3.93-.5-5.81zM9.55 15.57V8.43L15.82 12l-6.27 3.57z"/></svg>, url: "https://www.youtube.com/@cauverystore" }
            ].map(({ name, icon, url }) => (
              <a key={name} href={url} target="_blank" rel="noopener noreferrer" aria-label={name} style={{
                display: "flex", alignItems: "center", justifyContent: "center",
                width: 32, height: 32, borderRadius: "50%",
                background: "rgba(255,255,255,0.08)", color: "#a9c2c0",
                textDecoration: "none",
                transition: "background 0.2s, color 0.2s"
              }}
              onMouseEnter={(e) => { e.currentTarget.style.background = "var(--color-primary)"; e.currentTarget.style.color = "#fff"; }}
              onMouseLeave={(e) => { e.currentTarget.style.background = "rgba(255,255,255,0.08)"; e.currentTarget.style.color = "#a9c2c0"; }}
              >
                {icon}
              </a>
            ))}
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "0.35rem" }}>
            <span style={{
              display: "inline-flex", alignItems: "center", gap: "0.35rem",
              padding: "0.3rem 0.6rem", border: "1px solid rgba(255,255,255,0.15)",
              borderRadius: "6px", fontSize: "0.75rem", color: "#a9c2c0", width: "fit-content"
            }}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="5" y="2" width="14" height="20" rx="2" ry="2"/><line x1="12" y1="18" x2="12" y2="18"/></svg>
              App Store
            </span>
            <span style={{
              display: "inline-flex", alignItems: "center", gap: "0.35rem",
              padding: "0.3rem 0.6rem", border: "1px solid rgba(255,255,255,0.15)",
              borderRadius: "6px", fontSize: "0.75rem", color: "#a9c2c0", width: "fit-content"
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
        paddingTop: "1rem", borderTop: "1px solid rgba(255,255,255,0.1)",
        display: "flex", justifyContent: "space-between", alignItems: "center",
        flexWrap: "wrap", gap: "1rem", fontSize: "0.8rem"
      }}>
        <div style={{ display: "flex", gap: "0.75rem", alignItems: "center", color: "#86a3a1" }}>
          <span>Visa</span><span style={{ opacity: 0.3 }}>|</span>
          <span>MC</span><span style={{ opacity: 0.3 }}>|</span>
          <span>Amex</span><span style={{ opacity: 0.3 }}>|</span>
          <span>UPI</span><span style={{ opacity: 0.3 }}>|</span>
          <span>Net Banking</span><span style={{ opacity: 0.3 }}>|</span>
          <span>Wallets</span>
        </div>
        <p style={{ margin: 0, color: "#86a3a1" }}>
          &copy; {new Date().getFullYear()} Cauvery Store. All rights reserved.
        </p>
      </div>
    </footer>

    {/* Back to Top */}
    <button onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })} title="Back to top" aria-label="Back to top" style={{
      position: "fixed", bottom: "calc(80px + var(--bottom-bar-offset, 0px))", right: "1.5rem", zIndex: 50,
      width: 40, height: 40, borderRadius: "50%", background: "var(--color-primary, #0E5C5C)",
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
