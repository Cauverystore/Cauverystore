import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { DEFAULT_LANG } from "../i18n/langUtils";
import { getLocalizedNames } from "../services/i18nService";

const cache = new Map(); // "ta:12" -> { name, shortDescription }

/**
 * Give it a list of products; get back the same list with name/shortDescription replaced by the
 * translation when one is published. Falls back silently to the original text on any problem.
 */
export default function useLocalizedProducts(products) {
  const { i18n } = useTranslation();
  const lang = (i18n.language || DEFAULT_LANG).slice(0, 2);
  const [, bump] = useState(0);

  const ids = (products || []).map((p) => p?.id ?? p?._id).filter(Boolean);
  const key = ids.join(",");

  useEffect(() => {
    if (lang === DEFAULT_LANG || !key) return undefined;
    const missing = ids.filter((id) => !cache.has(`${lang}:${id}`));
    if (missing.length === 0) return undefined;
    let cancelled = false;
    getLocalizedNames(missing.slice(0, 100), lang)
      .then((res) => {
        const data = res.data || {};
        missing.forEach((id) => cache.set(`${lang}:${id}`, data[id] || null));
        if (!cancelled) bump((n) => n + 1);
      })
      .catch(() => { /* keep original text */ });
    return () => { cancelled = true; };
  }, [key, lang]);

  if (lang === DEFAULT_LANG || !products) return products;
  return products.map((p) => {
    const t = cache.get(`${lang}:${p?.id ?? p?._id}`);
    if (!t) return p;
    return { ...p, name: t.name || p.name, shortDescription: t.shortDescription || p.shortDescription };
  });
}
