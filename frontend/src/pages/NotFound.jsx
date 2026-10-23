import React from "react";
import { Link } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { useTranslation } from "react-i18next";

const ink = "#14302f";
const teal = "#0E5C5C";
const linkStyle = { color: ink, fontWeight: 600, fontSize: "0.9375rem", textDecoration: "underline", textDecorationColor: "#C8A24B", textDecorationThickness: "1.5px", textUnderlineOffset: "5px" };

const NotFound = () => {
  const { t } = useTranslation();

  return (
    <div style={{ maxWidth: 1180, margin: "0 auto", padding: "clamp(3.5rem, 10vw, 7rem) clamp(1.25rem, 5vw, 3rem)" }}>
      {/* Tells Prerender.io to return a real 404 to crawlers instead of a 200 "soft 404". */}
      <Helmet>
        <title>{t("notFound.pageNotFoundCauveryStore")}</title>
        <meta name="prerender-status-code" content="404" />
        <meta name="robots" content="noindex" />
      </Helmet>
      <p style={{ fontSize: "0.8125rem", fontWeight: 600, color: teal, margin: "0 0 1.5rem", letterSpacing: "0.18em" }}>{t("notFound.error404")}</p>
      <h1 style={{ fontFamily: "var(--font-display)", fontSize: "clamp(2.25rem, 6vw, 4.25rem)", fontWeight: 400, lineHeight: 1.05, letterSpacing: "-0.03em", color: ink, margin: "0 0 1.25rem", maxWidth: "14ch" }}>{t("notFound.weCanTFindThat")}</h1>
      <p style={{ fontSize: "1.0625rem", lineHeight: 1.65, color: "#48595a", margin: "0 0 2.25rem", maxWidth: "46ch" }}>{t("notFound.theLinkMayBeOld")}</p>
      <div style={{ display: "flex", gap: "1rem 2rem", alignItems: "center", flexWrap: "wrap" }}>
        <Link to="/products" style={{ padding: "0.95rem 1.6rem", borderRadius: 999, background: teal, color: "#fff", fontWeight: 600, fontSize: "0.9375rem", textDecoration: "none", boxShadow: "0 14px 28px -16px rgba(14, 92, 92, 0.7)" }}>{t("cartDrawer.browseProducts")}</Link>
        <Link to="/" style={linkStyle}>{t("notFound.goToHomepage")}</Link>
        <Link to="/help" style={linkStyle}>{t("notFound.getHelp")}</Link>
      </div>
    </div>
  );
};
export default NotFound;
