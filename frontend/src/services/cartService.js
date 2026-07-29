import api from "../api/axios";

export const getCart = () => api.get("/api/cart");
export const addToCart = (productId, quantity = 1) => api.post(`/api/cart/add?productId=${productId}&quantity=${quantity}`);
export const updateQuantity = (itemId, quantity) => api.post(`/api/cart/update-quantity/${itemId}?quantity=${quantity}`);
export const removeItem = (itemId) => api.delete(`/api/cart/remove/${itemId}`);
export const clearCart = () => api.delete("/api/cart/clear");
export const moveToCart = (itemId) => api.post(`/api/cart/move-to-cart/${itemId}`);
