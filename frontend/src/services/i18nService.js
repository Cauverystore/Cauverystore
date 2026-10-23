import api from "../api/axios";

// Thin wrappers over the new endpoints. Every one of these may fail (older backend, network): callers must cope.
export const getLanguages = () => api.get("/api/settings/languages");
export const getMyLanguage = () => api.get("/api/user/language");
export const saveMyLanguage = (language) => api.put("/api/user/language", { language });

export const getLocalizedProduct = (id, lang) => api.get(`/api/products/${id}/localized`, { params: { lang } });
export const getLocalizedNames = (ids, lang) =>
  api.get("/api/products/localized-names", { params: { ids: ids.join(","), lang } });
export const getCategoryNames = (lang) => api.get("/api/categories/localized-names", { params: { lang } });
export const searchMultilingual = (q, lang, limit = 40) =>
  api.get("/api/products/search/multilingual", { params: { q, lang, limit } });
