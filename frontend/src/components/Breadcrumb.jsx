import React from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";

const Breadcrumb = ({ items }) => {
  const { t } = useTranslation();

  return (
    <nav aria-label={t("breadcrumb.breadcrumb")} style={{ fontSize: "0.8rem", color: "var(--color-text-secondary)", marginBottom: "0.75rem", display: "flex", alignItems: "center", gap: "0.35rem", flexWrap: "wrap" }}>
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
};

export default Breadcrumb;
