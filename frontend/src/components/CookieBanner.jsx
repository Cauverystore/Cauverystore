import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import "../styles/site-extras.css";
import { useTranslation } from "react-i18next";

// index.html reads the same key to set Google's consent default before analytics loads.
const STORAGE_KEY = "cs_cookie_consent";
export const OPEN_COOKIE_SETTINGS = "cs:open-cookie-settings";

const readChoice = () => {
  try { return localStorage.getItem(STORAGE_KEY); } catch { return null; }
};

const applyChoice = (choice) => {
  if (typeof window.gtag !== "function") return;
  const value = choice === "accepted" ? "granted" : "denied";
  try {
    window.gtag("consent", "update", {
      analytics_storage: value, ad_storage: value,
      ad_user_data: value, ad_personalization: value
    });
  } catch {
    /* analytics must never break the store */
  }
};

const CookieBanner = () => {
  const { t } = useTranslation();

  const [open, setOpen] = useState(() => !readChoice());

  useEffect(() => {
    const reopen = () => setOpen(true);
    window.addEventListener(OPEN_COOKIE_SETTINGS, reopen);
    return () => window.removeEventListener(OPEN_COOKIE_SETTINGS, reopen);
  }, []);

  const choose = (choice) => {
    try { localStorage.setItem(STORAGE_KEY, choice); } catch { /* storage blocked */ }
    applyChoice(choice);
    setOpen(false);
  };

  if (!open) return null;

  return (
    <div className="cs-cookie-banner" role="region" aria-label={t("cookieBanner.cookieChoice")}>
      <p>{t("cookieBanner.weUseEssentialCookiesTo")}{" "}
        <Link to="/privacy-policy">{t("footer.privacy")}</Link>
      </p>
      <div className="cs-cookie-actions">
        <button type="button" className="cs-btn cs-btn-secondary" onClick={() => choose("declined")}>{t("cookieBanner.essentialOnly")}</button>
        <button type="button" className="cs-btn cs-btn-primary" onClick={() => choose("accepted")}>{t("cookieBanner.acceptAll")}</button>
      </div>
    </div>
  );
};

export default CookieBanner;
