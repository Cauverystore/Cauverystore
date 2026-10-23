// Language helpers shared by the router, the selector and the pages.
// English is the default and has NO prefix in the URL (/product/12). Every other language is prefixed (/ta/product/12),
// so your existing links and Google's existing English pages keep working.

export const DEFAULT_LANG = "en";

// Fallback list used until /api/settings/languages answers (and if it never does).
export const FALLBACK_LANGUAGES = [
  { code: "en", nameNative: "English", nameEnglish: "English" },
  { code: "ta", nameNative: "தமிழ்", nameEnglish: "Tamil" },
  { code: "te", nameNative: "తెలుగు", nameEnglish: "Telugu" },
  { code: "hi", nameNative: "हिन्दी", nameEnglish: "Hindi" },
  { code: "kn", nameNative: "ಕನ್ನಡ", nameEnglish: "Kannada" },
  { code: "ml", nameNative: "മലയാളം", nameEnglish: "Malayalam" },
];

export const SUPPORTED = FALLBACK_LANGUAGES.map((l) => l.code);
const STORAGE_KEY = "cs_lang";

// Areas that stay English-only and are never given a language prefix (staff tools, payment return pages).
const UNPREFIXED = /^\/(admin|super-admin|seller\/(dashboard|products|inventory|orders|store|team|payouts|analytics|gst-)|executive|order-success)(\/|$)/;

export function isSupported(code) {
  return SUPPORTED.includes(code);
}

export function getStoredLang() {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return isSupported(v) ? v : null;
  } catch {
    return null;
  }
}

export function storeLang(code) {
  try { localStorage.setItem(STORAGE_KEY, code); } catch { /* storage blocked: language just won't be remembered */ }
}

/** "/ta/product/5" -> { lang: "ta", path: "/product/5" };  "/product/5" -> { lang: null, path: "/product/5" } */
export function splitLang(pathname) {
  const m = pathname.match(/^\/([a-z]{2})(\/.*)?$/);
  if (m && m[1] !== DEFAULT_LANG && isSupported(m[1])) {
    return { lang: m[1], path: m[2] || "/" };
  }
  return { lang: null, path: pathname };
}

/** Is this an area that should never carry a language prefix? */
export function isUnprefixed(path) {
  return UNPREFIXED.test(path);
}

/** withLang("/product/5", "ta") -> "/ta/product/5";  withLang("/product/5", "en") -> "/product/5" */
export function withLang(path, lang) {
  const { path: bare } = splitLang(path);
  if (!lang || lang === DEFAULT_LANG || isUnprefixed(bare)) return bare;
  return `/${lang}${bare === "/" ? "" : bare}`;
}
