import React, { useEffect, useMemo } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { useTranslation } from "react-i18next";
import { DEFAULT_LANG, SUPPORTED, getStoredLang, isUnprefixed, splitLang, storeLang } from "./langUtils";

const SITE = "https://cauverystore.in";
// Pages that should be offered to Google in every language (hreflang). Private/transactional pages are left out.
const ALTERNATE_OK = /^\/($|products$|product\/[^/]+$|category\/[^/]+$|offers|about$|help$|policies$|contact$|privacy-policy$|terms-and-conditions$|shipping-policy$|refund-policy$)/;

/**
 * Wrap your existing <Routes> with this. It works out the language from the URL, tells i18next,
 * and hands <Routes> the URL WITHOUT the "/ta" part, so none of your route definitions change.
 *
 *   <LangRoutes><Routes> ...unchanged... </Routes></LangRoutes>
 */
export default function LangRoutes({ children }) {
  const location = useLocation();
  const { i18n } = useTranslation();

  const { lang: urlLang, path } = splitLang(location.pathname);
  const stored = getStoredLang();
  // URL wins. With no prefix: English if they chose it (or never chose), otherwise their saved language.
  const active = urlLang || DEFAULT_LANG;
  const needsPrefix = !urlLang && stored && stored !== DEFAULT_LANG && !isUnprefixed(path);

  useEffect(() => {
    if (needsPrefix) return;
    if (i18n.language !== active) i18n.changeLanguage(active);
    document.documentElement.lang = active;
    if (urlLang) storeLang(urlLang);
  }, [active, needsPrefix, urlLang, i18n]);

  const innerLocation = useMemo(() => ({ ...location, pathname: path }), [location, path]);

  if (needsPrefix) {
    // e.g. an old English link opened by someone who chose Tamil: send them to /ta/... (query and #anchor kept).
    return <Navigate replace to={`/${stored}${path === "/" ? "" : path}${location.search}${location.hash}`} />;
  }

  const showAlternates = ALTERNATE_OK.test(path);
  return (
    <>
      {showAlternates && (
        <Helmet>
          {SUPPORTED.map((code) => (
            <link key={code} rel="alternate" hrefLang={code} href={`${SITE}${code === DEFAULT_LANG ? "" : `/${code}`}${path === "/" ? (code === DEFAULT_LANG ? "/" : "") : path}`} />
          ))}
          <link rel="alternate" hrefLang="x-default" href={`${SITE}${path}`} />
        </Helmet>
      )}
      {React.Children.map(children, (child) =>
        React.isValidElement(child) ? React.cloneElement(child, { location: innerLocation, key: i18n.language }) : child
      )}
    </>
  );
}
