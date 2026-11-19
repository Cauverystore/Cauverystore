import React, { useEffect, useMemo } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { useTranslation } from "react-i18next";
import { DEFAULT_LANG, SUPPORTED, getStoredLang, isUnprefixed, splitLang, storeLang } from "./langUtils";

const SITE = "https://cauverystore.in";
// Pages that should be offered to Google in every language (hreflang). Private/transactional pages are left out.
// Kept in step with SitemapService: /faq (not /help, whose canonical is /faq) and no /offers while it is empty.
// Product pages are left out on purpose: the sitemap lists a product only in the languages that have a
// published translation, and it carries those hreflang links, so the page must not claim more than that.
const ALTERNATE_OK = /^\/($|products$|category\/[^/]+$|about$|faq$|policies$|contact$|privacy-policy$|terms-and-conditions$|shipping-policy$|refund-policy$)/;

// Sign-in, account, cart and dashboard pages: nothing for a search engine to show, and several of them
// render the homepage's title, so Google reports them as soft 404s or duplicates of the homepage.
// They are marked noindex here, in one place, for every language. /seller/register is the public
// "Sell on Cauvery Store" page and stays indexable.
const NOINDEX = /^\/(login|register|forgot-password|reset-password|logout|unauthorized|otp-verify|complete-profile|executive\/login|seller\/login|seller\/invite|profile|address|wishlist|cart|checkout|orders|order-success|notifications|loyalty|support-tickets|payment-methods|compare|search|admin|super-admin|seller(?!\/register\/?$))(\/|$)/;

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
      {NOINDEX.test(path) && (
        <Helmet>
          <meta name="robots" content="noindex, follow" />
        </Helmet>
      )}
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
