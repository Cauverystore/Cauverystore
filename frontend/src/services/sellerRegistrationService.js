import api from "../api/axios";

const sellerRegistrationService = {
  start: () => api.post("/api/seller-registration/start"),
  saveStep: (data) => api.post("/api/seller-registration/step", data),
  uploadDocument: (data) => api.post("/api/seller-registration/document", data),
  getStatus: () => api.get("/api/seller-registration/status"),
  getComplianceAlerts: () => api.get("/api/seller-registration/compliance/alerts"),
  submit: () => api.post("/api/seller-registration/submit"),
  verifyGstin: (gstin) => api.post("/api/seller-registration/verify/gstin", { gstin }),
  verifyBank: (accountNumber, ifsc, accountName) =>
    api.post("/api/seller-registration/verify/bank", { accountNumber, ifsc, accountName }),

  getDashboard: () => api.get("/api/seller/dashboard"),
  getProducts: (params) => api.get("/api/seller/products", { params }),
  createProduct: (data) => api.post("/api/seller/products", data),
  updateProduct: (id, data) => api.put(`/api/seller/products/${id}`, data),
  deleteProduct: (id) => api.delete(`/api/seller/products/${id}`),
  getOrders: () => api.get("/api/seller/orders"),
  updateOrderStatus: (id, data) => api.put(`/api/seller/orders/${id}/status`, data),
  getReturns: () => api.get("/api/seller/returns"),
  updateReturnStatus: (id, data) => api.put(`/api/seller/returns/${id}/status`, data),
  getAnalytics: () => api.get("/api/seller/analytics"),
  getStore: () => api.get("/api/seller/store"),
  updateStore: (data) => api.put("/api/seller/store", data),
  getNotifications: () => api.get("/api/seller/notifications"),
};

export default sellerRegistrationService;
