import api from "../api/axios";

export const createPaymentOrder = (data) => api.post("/api/payment/create", data);
export const verifyPayment = (data) => api.post("/api/payment/verify", data);
