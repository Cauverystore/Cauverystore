import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { verifyOtp } from "../api/auth";
import "../styles/auth.css";

const OtpVerification = () => {
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
    } catch (err) { setError(err.response?.data?.message || "Invalid OTP"); }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h2>Verify OTP</h2>
        <p className="auth-subtitle">Enter the OTP sent to {email}</p>
        {error && <div className="auth-error">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="auth-field"><label>Email</label><input className="auth-input" value={email} onChange={(e) => setEmail(e.target.value)} /></div>
          <div className="auth-field"><label>OTP</label><input className="auth-input" value={otp} onChange={(e) => setOtp(e.target.value)} placeholder="Enter 6-digit OTP" /></div>
          <button className="auth-btn" type="submit">Verify</button>
        </form>
      </div>
    </div>
  );
};
export default OtpVerification;
