import React from "react";
import { Link } from "react-router-dom";

const NotFound = () => (
  <div style={{ textAlign: "center", padding: "4rem 1.5rem" }}>
    <h1 style={{ fontSize: "5rem", fontWeight: 700, color: "#16a34a", margin: 0 }}>404</h1>
    <p style={{ fontSize: "1.25rem", color: "#475569", marginBottom: "2rem" }}>Page not found</p>
    <Link to="/" style={{ padding: "0.6rem 1.5rem", background: "#16a34a", color: "#fff", borderRadius: 6, textDecoration: "none" }}>Go Home</Link>
  </div>
);
export default NotFound;
