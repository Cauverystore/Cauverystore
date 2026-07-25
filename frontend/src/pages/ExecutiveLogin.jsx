import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import "../styles/auth.css";

const ExecutiveLogin = () => {
  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  const handleSubmit = async (e) => {
    e.preventDefault(); setError(""); setLoading(true);
    try {
      await login(form.email, form.password, "executive");
      navigate("/admin/executive-dashboard");
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || "Invalid email or password");
    }
    finally { setLoading(false); }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h2>Executive Login</h2>
        <p className="auth-subtitle">Access executive dashboard</p>
        {error && <div className="auth-error">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="auth-field"><label>Email</label><input className="auth-input" type="text" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required /></div>
          <div className="auth-field"><label>Password</label><input className="auth-input" type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required /></div>
          <button className="auth-btn" type="submit" disabled={loading}>{loading ? "Logging in..." : "Login as Executive"}</button>
        </form>
      </div>
    </div>
  );
};
export default ExecutiveLogin;
