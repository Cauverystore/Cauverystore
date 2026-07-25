import api from "./axios";

export const login = (username, password) =>
  api.post("/api/auth/login", { username, password });

export const register = (userData) =>
  api.post("/api/auth/register", userData);

export const logout = () => {
  localStorage.removeItem("admin_token");
  localStorage.removeItem("accessToken");
  localStorage.removeItem("user");
};

export const authFetch = () =>
  api.get("/api/auth/me");

export const refreshToken = () =>
  api.post("/api/auth/refresh");

export const loginAdmin = (username, password) =>
  api.post("/api/auth/admin/login", { username, password });

export const loginSeller = (username, password) =>
  api.post("/api/auth/seller/login", { username, password });

export const loginExecutive = (username, password) =>
  api.post("/api/auth/executive/login", { username, password });

export const sendOtp = (email) =>
  api.post("/api/auth/send-otp", { email });

export const verifyOtp = (email, otp) =>
  api.post("/api/auth/verify-otp", { email, otp });
