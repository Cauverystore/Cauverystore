import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import api from "../api/axios";
import { DEFAULT_LANG } from "../i18n/langUtils";
import { getCategoryNames } from "../services/i18nService";

let catsPromise = null;            // /api/categories, fetched once
const namesCache = new Map();      // "ta" -> { "3": "..." }

/**
 * labelOf(x) returns the category name in the chosen language, or the original name when
 * no translation is published. x can be a category object, a category id or an English name.
 */
export default function useCategoryNames() {
  const { i18n } = useTranslation();
  const lang = (i18n.language || DEFAULT_LANG).slice(0, 2);
  const [, bump] = useState(0);
  const [cats, setCats] = useState([]);

  useEffect(() => {
    if (lang === DEFAULT_LANG) return undefined;
    let off = false;
    if (!catsPromise) catsPromise = api.get("/api/categories").then((r) => (Array.isArray(r.data) ? r.data : r.data?.content || [])).catch(() => { catsPromise = null; return []; });
    catsPromise.then((c) => { if (!off) setCats(c); });
    if (!namesCache.has(lang)) {
      getCategoryNames(lang)
        .then((r) => { namesCache.set(lang, r.data || {}); if (!off) bump((n) => n + 1); })
        .catch(() => {});
    }
    return () => { off = true; };
  }, [lang]);

  return (x) => {
    const original = typeof x === "object" && x ? x.name || x.title || "" : typeof x === "string" ? x : "";
    if (lang === DEFAULT_LANG) return original || String(x ?? "");
    const names = namesCache.get(lang) || {};
    let id = typeof x === "object" && x ? x.id ?? x._id : typeof x === "number" ? x : undefined;
    if (id === undefined && original) id = (cats.find((c) => c.name === original) || {}).id;
    return (id !== undefined && names[id]) || original || String(x ?? "");
  };
}
