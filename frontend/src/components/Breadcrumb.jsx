import React from "react";
import { Link } from "react-router-dom";

const Breadcrumb = ({ items }) => (
  <nav aria-label="Breadcrumb" style={{ fontSize: "0.8rem", color: "var(--color-text-secondary)", marginBottom: "0.75rem", display: "flex", alignItems: "center", gap: "0.35rem", flexWrap: "wrap" }}>
    {items.map((item, i) => (
      <React.Fragment key={i}>
        {i > 0 && <span style={{ color: "var(--color-border)" }}>/</span>}
        {item.to ? (
          <Link to={item.to} style={{ color: "var(--color-primary)", textDecoration: "none" }}>{item.label}</Link>
        ) : (
          <span style={{ color: "var(--color-text)" }}>{item.label}</span>
        )}
      </React.Fragment>
    ))}
  </nav>
);

export default Breadcrumb;
