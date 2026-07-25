import api from "../api/axios";

const promoService = {
  validatePromo: (code, cartTotal) => api.post("/api/promo/validate", { code, cartTotal }),
  getActiveOffers: () => api.get("/api/promo/offers"),
  getOfferById: (id) => api.get(`/api/promo/offers/${id}`),
};
export default promoService;
