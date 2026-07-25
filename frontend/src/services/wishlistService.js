import api from "../api/axios";

export const addToWishlist = (productId) => api.post(`/api/wishlist/add/${productId}`);
export const getWishlist = () => api.get("/api/wishlist");
export const removeFromWishlist = (productId) => api.delete(`/api/wishlist/remove/${productId}`);
