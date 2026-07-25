import api from "../api/axios";

export const getOrders = (params) => api.get("/api/orders", { params });
export const getMyOrders = getOrders;
export const placeOrder = (data) => api.post("/api/orders/place", data);
export const cancelOrder = (id) => api.put(`/api/orders/${id}/cancel`);
export const getOrderById = (id) => api.get(`/api/orders/${id}`);
export const getAdminOrders = (params) => api.get("/api/admin/orders", { params });
export const updateOrderStatus = (id, status) => api.put(`/api/admin/orders/${id}/status`, { status });
export const bulkCancelOrders = (ids) => api.post("/api/orders/bulk-cancel", { ids });
