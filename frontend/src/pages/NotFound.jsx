import React from "react";
import { Link } from "react-router-dom";
import { Helmet } from "react-helmet-async";

const ink = "#14302f";
const teal = "#0E5C5C";
const linkStyle = { color: ink, fontWeight: 600, fontSize: "0.9375rem", textDecoration: "underline", textDecorationColor: "#C8A24B", textDecorationThickness: "1.5px", textUnderlineOffset: "5px" };

const NotFound = () => (
  <div style={{ maxWidth: 1180, margin: "0 auto", padding: "clamp(3.5rem, 10vw, 7rem) clamp(1.25rem, 5vw, 3rem)" }}>
    {/* Tells Prerender.io to return a real 404 to crawlers instead of a 200 "soft 404". */}
    <Helmet>
      <title>Page not found - Cauvery Store</title>
      <meta name="prerender-status-code" content="404" />
      <meta name="robots" content="noindex" />
    </Helmet>
    <p style={{ fontSize: "0.8125rem", fontWeight: 600, color: teal, margin: "0 0 1.5rem", letterSpacing: "0.18em" }}>ERROR 404</p>
    <h1 style={{ fontFamily: "var(--font-display)", fontSize: "clamp(2.25rem, 6vw, 4.25rem)", fontWeight: 400, lineHeight: 1.05, letterSpacing: "-0.03em", color: ink, margin: "0 0 1.25rem", maxWidth: "14ch" }}>We can't find that page</h1>
    <p style={{ fontSize: "1.0625rem", lineHeight: 1.65, color: "#48595a", margin: "0 0 2.25rem", maxWidth: "46ch" }}>
      The link may be old, or the product may no longer be listed. Everything in the store is one tap away.
    </p>
    <div style={{ display: "flex", gap: "1rem 2rem", alignItems: "center", flexWrap: "wrap" }}>
      <Link to="/products" style={{ padding: "0.95rem 1.6rem", borderRadius: 999, background: teal, color: "#fff", fontWeight: 600, fontSize: "0.9375rem", textDecoration: "none", boxShadow: "0 14px 28px -16px rgba(14, 92, 92, 0.7)" }}>Browse products</Link>
      <Link to="/" style={linkStyle}>Go to homepage</Link>
      <Link to="/help" style={linkStyle}>Get help</Link>
    </div>
  </div>
);
export default NotFound;
