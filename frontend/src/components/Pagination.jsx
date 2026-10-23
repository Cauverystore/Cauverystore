import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import i18n from "../i18n";

const Pagination = ({ page, totalPages, onPage }) => {
  const { t } = useTranslation();

  const [goTo, setGoTo] = useState("");
  if (totalPages <= 1) return null;
  const handleGoTo = () => {
    const p = parseInt(goTo, 10);
    if (p >= 1 && p <= totalPages) onPage(p);
    setGoTo("");
  };
  return (
    <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", justifyContent: "center", marginTop: "1.5rem" }}>
      <button disabled={page === 1} onClick={() => onPage(1)} style={btnStyle}>{t("pagination.first")}</button>
      <button disabled={page === 1} onClick={() => onPage(page - 1)} style={btnStyle}>{t("pagination.prev")}</button>
      <span style={{ fontSize: "0.9rem" }}>{t("pagination.page")}{" "}{page}{" "}{t("pagination.of")}{" "}{totalPages}</span>
      <input value={goTo} onChange={(e) => setGoTo(e.target.value)} onKeyDown={(e) => e.key === "Enter" && handleGoTo()} placeholder={t("pagination.go")} aria-label={i18n.t("msgs.goToPage", { total: totalPages })} style={{ width: "50px", padding: "0.3rem", fontSize: "0.85rem", border: "1px solid #ccc", borderRadius: "4px", textAlign: "center" }} />
      <button disabled={page === totalPages} onClick={() => onPage(page + 1)} style={btnStyle}>{t("pagination.next")}</button>
      <button disabled={page === totalPages} onClick={() => onPage(totalPages)} style={btnStyle}>{t("pagination.last")}</button>
    </div>
  );
};

const btnStyle = { padding: "0.35rem 0.75rem", border: "1px solid #e2e8f0", background: "#fff", borderRadius: "4px", cursor: "pointer", fontSize: "0.85rem" };
export default Pagination;
