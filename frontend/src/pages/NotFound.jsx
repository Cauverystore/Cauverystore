import React from "react";
import { Link } from "react-router-dom";
import { Helmet } from "react-helmet-async";

const linkStyle = { padding: "0.6rem 1.5rem", borderRadius: 6, textDecoration: "none", fontWeight: 600 };

const NotFound = () => (
  <div style={{ textAlign: "center", padding: "4rem 1.5rem" }}>
    {/* Tells Prerender.io to return a real 404 to crawlers instead of a 200 "soft 404". */}
    <Helmet>
      <title>Page not found - Cauvery Store</title>
      <meta name="prerender-status-code" content="404" />
      <meta name="robots" content="noindex" />
    </Helmet>
    <p style={{ fontSize: "0.9rem", fontWeight: 700, color: "#16a34a", margin: "0 0 0.5rem", letterSpacing: "0.05em" }}>ERROR 404</p>
    <h1 style={{ fontSize: "1.8rem", fontWeight: 700, color: "#0f172a", margin: "0 0 0.75rem" }}>We can't find that page</h1>
    <p style={{ fontSize: "1rem", color: "#475569", margin: "0 auto 2rem", maxWidth: 460 }}>
      The link may be old, or the product may no longer be listed. Everything our sellers offer is one tap away.
    </p>
    <div style={{ display: "flex", gap: "0.75rem", justifyContent: "center", flexWrap: "wrap" }}>
      <Link to="/products" style={{ ...linkStyle, background: "#16a34a", color: "#fff" }}>Browse products</Link>
      <Link to="/" style={{ ...linkStyle, border: "1px solid #16a34a", color: "#16a34a" }}>Go to homepage</Link>
      <Link to="/help" style={{ ...linkStyle, color: "#475569" }}>Get help</Link>
    </div>
  </div>
);
export default NotFound;
