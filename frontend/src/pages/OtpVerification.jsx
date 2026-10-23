import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { verifyOtp } from "../api/auth";
import "../styles/auth.css";
import { useTranslation } from "react-i18next";

const OtpVerification = () => {
  const { t } = useTranslation();

  const [email, setEmail] = useState(localStorage.getItem("pendingEmail") || "");
  const [otp, setOtp] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault(); setError("");
    try {
      const res = await verifyOtp(email, otp);
      localStorage.setItem("accessToken", res.data.token || res.data.accessToken);
      localStorage.removeItem("pendingEmail");
      navigate("/");
    } catch (err) { setError(err.response?.data?.message || t("otpVerification.invalidOtp")); }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h2>{t("otpVerification.verifyOtp")}</h2>
        <p className="auth-subtitle">{t("otpVerification.enterTheOtpSentTo")}{" "}{email}</p>
        {error && <div className="auth-error">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="auth-field"><label>{t("contactUs.email")}</label><input className="auth-input" value={email} onChange={(e) => setEmail(e.target.value)} /></div>
          <div className="auth-field"><label>OTP</label><input className="auth-input" value={otp} onChange={(e) => setOtp(e.target.value)} placeholder={t("otpVerification.enter6DigitOtp")} /></div>
          <button className="auth-btn" type="submit">{t("otpVerification.verify")}</button>
        </form>
      </div>
    </div>
  );
};
export default OtpVerification;
