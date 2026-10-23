import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import resourcesToBackend from "i18next-resources-to-backend";
import en from "./en.json";
import { DEFAULT_LANG, SUPPORTED, getStoredLang } from "./langUtils";

// English is bundled (always instant). The other five load only when someone picks them.
i18n
  .use(resourcesToBackend((lng) => import(`./${lng}.json`).then((m) => m.default || m)))
  .use(initReactI18next)
  .init({
    resources: { en: { translation: en } },
    partialBundledLanguages: true,
    lng: getStoredLang() || DEFAULT_LANG,
    fallbackLng: DEFAULT_LANG,
    supportedLngs: SUPPORTED,
    load: "languageOnly",
    interpolation: { escapeValue: false }, // React already escapes
    react: { useSuspense: false },          // never blank the page while a language file loads
  });

export default i18n;
