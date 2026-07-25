import api from "../api/axios";

const homeService = {
  getHomeData: () => api.get("/api/home"),
  getBanners: () => api.get("/api/banners"),
  getFeaturedProducts: () => api.get("/api/products/featured"),
  getCategories: () => api.get("/api/categories"),
  getContactInfo: () => api.get("/api/contact"),
  submitContact: (data) => api.post("/api/contact", data),
  getAboutInfo: () => api.get("/api/about"),
};
export default homeService;
