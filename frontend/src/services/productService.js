import api from "../api/axios";

export const getAllProducts = () => api.get("/api/products");
export const getProductById = (id) => api.get(`/api/products/${id}`);
export const searchProducts = (params) => api.get("/api/products/search", { params });
export const getProducts = (params) => api.get("/api/products", { params });
export const getAdminProducts = (params) => api.get("/api/admin/products", { params });
export const deleteProduct = (id) => api.delete(`/api/admin/products/${id}`);
export const bulkDeleteProducts = (ids) => api.post("/api/admin/products/bulk-delete", { ids });
export const getCategories = () => api.get("/api/categories");
