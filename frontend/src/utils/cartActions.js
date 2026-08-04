import { addToCart } from "../services/cartService";

const isLoggedIn = () => !!localStorage.getItem("accessToken");

const loginRedirectPath = () => {
  const current = window.location.pathname + window.location.search;
  return `/login?redirect=${encodeURIComponent(current)}`;
};

export const requireLogin = (navigate) => navigate(loginRedirectPath());

export const addToCartOrLogin = async (navigate, product, quantity = 1) => {
  if (!isLoggedIn()) {
    requireLogin(navigate);
    return { needLogin: true };
  }
  try {
    await addToCart(product.id || product._id, quantity);
    return { ok: true };
  } catch (err) {
    if (err?.response?.status === 401 || err?.response?.status === 403) {
      requireLogin(navigate);
      return { needLogin: true };
    }
    return { ok: false };
  }
};
