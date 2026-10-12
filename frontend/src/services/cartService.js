import api from "../api/axios";

const notifyCartUpdated = (res) => {
  window.dispatchEvent(new Event("cart:updated"));
  return res;
};

export const getCart = () => api.get("/api/cart");
// variantId is the chosen option (a size, say); products sold in options cannot be added without one.
export const addToCart = (productId, quantity = 1, variantId = null) =>
  api.post(`/api/cart/add?productId=${productId}&quantity=${quantity}${variantId != null ? `&variantId=${variantId}` : ""}`).then(notifyCartUpdated);
export const updateQuantity = (itemId, quantity) => api.post(`/api/cart/update-quantity/${itemId}?quantity=${quantity}`).then(notifyCartUpdated);
export const removeItem = (itemId) => api.delete(`/api/cart/remove/${itemId}`).then(notifyCartUpdated);
export const clearCart = () => api.delete("/api/cart/clear").then(notifyCartUpdated);
export const moveToCart = (itemId) => api.post(`/api/cart/move-to-cart/${itemId}`).then(notifyCartUpdated);
