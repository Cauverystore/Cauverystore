import React, { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { FALLBACK_LANGUAGES, splitLang, storeLang, withLang } from "../i18n/langUtils";
import { getLanguages, saveMyLanguage } from "../services/i18nService";
import "../styles/language-selector.css";

export default function LanguageSelector({ className = "" }) {
  const { t, i18n } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const [languages, setLanguages] = useState(FALLBACK_LANGUAGES);

  useEffect(() => {
    getLanguages()
      .then((res) => { if (Array.isArray(res.data) && res.data.length) setLanguages(res.data); })
      .catch(() => { /* keep the built-in list */ });
  }, []);

  const current = (i18n.language || "en").slice(0, 2);

  const change = (e) => {
    const next = e.target.value;
    storeLang(next);
    // Same page, other language. No reload: the router swaps the URL and i18next swaps the text.
    const { path } = splitLang(location.pathname);
    navigate(`${withLang(path, next)}${location.search}${location.hash}`);
    if (localStorage.getItem("accessToken")) saveMyLanguage(next).catch(() => { /* preference is a nicety */ });
  };

  return (
    <label className={`cs-lang ${className}`}>
      <span className="cs-lang-sr">{t("lang.select")}</span>
      <select value={current} onChange={change} aria-label={t("lang.select")}>
        {languages.map((l) => (
          <option key={l.code} value={l.code} lang={l.code}>{l.nameNative}</option>
        ))}
      </select>
    </label>
  );
}
