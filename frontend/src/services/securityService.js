import api from "../api/axios";

const securityService = {
  getMe: () => api.get("/api/auth/me"),

  sendOtp: (email, purpose = "EMAIL_VERIFICATION") =>
    api.post("/api/auth/send-otp", { email, purpose }),
  verifyOtp: (email, purpose, otp) =>
    api.post("/api/auth/verify-otp", { email, purpose, otp }),

  enable2fa: () => api.post("/api/auth/enable-2fa"),
  confirm2fa: (otp) => api.post("/api/auth/confirm-2fa", { otp }),
  disable2fa: (otp) => api.post("/api/auth/disable-2fa", { otp }),

  getSessions: () => api.get("/api/auth/sessions"),
  revokeSession: (sessionId) => api.delete(`/api/auth/sessions/${sessionId}`),
  logoutAll: () => api.post("/api/auth/logout-all", {}),

  changePassword: (oldPassword, newPassword) =>
    api.post("/api/auth/change-password", { oldPassword, newPassword }),
};

export default securityService;
