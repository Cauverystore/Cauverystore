import api from "../api/axios";

const userService = {
  getProfile: () => api.get("/api/users/profile"),
  updateProfile: (data) => api.put("/api/users/profile", data),
  updateProfilePicture: (data) => api.post("/api/users/profile/picture", data),

  getAddresses: () => api.get("/api/users/addresses"),
  addAddress: (data) => api.post("/api/users/addresses", data),
  updateAddress: (id, data) => api.put(`/api/users/addresses/${id}`, data),
  deleteAddress: (id) => api.delete(`/api/users/addresses/${id}`),
  getDefaultAddress: () => api.get("/api/users/addresses/default"),
  getBillingAddress: () => api.get("/api/users/addresses/billing"),

  getPaymentMethods: () => api.get("/api/users/payment-methods"),
  addPaymentMethod: (data) => api.post("/api/users/payment-methods", data),
  deletePaymentMethod: (id) => api.delete(`/api/users/payment-methods/${id}`),

  getPreferences: () => api.get("/api/users/preferences"),
  updatePreferences: (data) => api.put("/api/users/preferences", data),

  getOrderHistory: () => api.get("/api/users/orders"),
  getMyReviews: () => api.get("/api/users/reviews"),
  getWishlist: () => api.get("/api/users/wishlist"),

  getNotifications: () => api.get("/api/users/notifications"),
  markNotificationRead: (id) => api.put(`/api/users/notifications/${id}/read`),
  getLoyaltyPoints: () => api.get("/api/users/loyalty"),

  getAdminUsers: (params) => api.get("/api/admin/users", { params }),
  updateUserRole: (id, role) => api.put(`/api/admin/users/${id}/role`, { role }),
  blockUser: (id) => api.put(`/api/admin/users/${id}/block`),
  unblockUser: (id) => api.put(`/api/admin/users/${id}/unblock`),
  deleteUser: (id) => api.delete(`/api/admin/users/${id}`),

  getAdminAddresses: () => api.get("/api/admin/addresses"),
  restoreAddress: (id) => api.post(`/api/admin/addresses/${id}/restore`),
};
export default userService;
