// Canonical URLs that follow the page's language.
//
// Each language version of a page is its own URL (/about, /ta/about, /te/about ...) and LangRoutes already
// links them together with hreflang. For hreflang to work, every version must name ITSELF as canonical.
// A canonical that always points at the English URL tells Google to ignore the translated pages.
//
//   canonicalFor("/about")  -> "https://cauverystore.in/about"      (English)
//   canonicalFor("/about")  -> "https://cauverystore.in/ta/about"   (Tamil)
//   canonicalFor("/")       -> "https://cauverystore.in/ta"         (Tamil home, same as the sitemap)
import i18n from "i18next";
import { DEFAULT_LANG, isSupported, withLang } from "./langUtils";

export const SITE_URL = "https://cauverystore.in";

// forceLang: pass "en" for a translated URL whose content is not really translated yet (it then
// names the English page as canonical, so Google does not treat it as a duplicate).
export function canonicalFor(path = "/", forceLang) {
  const code = String(forceLang || i18n.language || DEFAULT_LANG).split("-")[0];
  const lang = isSupported(code) ? code : DEFAULT_LANG;
  return SITE_URL + withLang(path, lang);
}