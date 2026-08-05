import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import "../styles/auth.css";

const SellerLogin = () => {
  const navigate = useNavigate();
  const { login, completeMfaLogin, cancelMfaLogin } = useAuth();
  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [mfaStep, setMfaStep] = useState(null);
  const [mfaCode, setMfaCode] = useState("");

  const handleLogin = async (e) => {
    e.preventDefault(); setError(""); setLoading(true);
    try {
      const data = await login(form.email, form.password, "seller");
      if (data && data.mfaRequired) {
        setMfaStep({ email: form.email });
        setLoading(false);
        return;
      }
      navigate("/seller/dashboard");
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || "Invalid credentials");
    }
    finally { setLoading(false); }
  };

  const handleMfaSubmit = async (e) => {
    e.preventDefault();
    if (!mfaCode.trim()) return;
    setLoading(true); setError("");
    try {
      const data = await completeMfaLogin(mfaCode.trim());
      navigate((data.role || "seller").toLowerCase() === "seller" ? "/seller/dashboard" : "/");
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || "Invalid verification code. Please try again.");
    } finally { setLoading(false); }
  };

  const handleCancelMfa = () => {
    cancelMfaLogin();
    setMfaStep(null);
    setMfaCode("");
    setError("");
  };

  if (mfaStep) {
    return (
      <div className="auth-page">
        <div className="auth-card">
          <div className="auth-header"><img src="/images/logo.jpg" alt="" style={{ height: "48px", width: "auto" }} /><h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: "0.5rem 0 0", color: "var(--color-primary, #16a34a)" }}>Cauvery Store</h1><p>Two-Factor Authentication</p></div>
          <p style={{ fontSize: "0.85rem", color: "#64748b", marginBottom: "0.5rem" }}>Enter the 6-digit code from your authenticator app to continue as {mfaStep.email}.</p>
          {error && <div className="auth-error">{error}</div>}
          <form onSubmit={handleMfaSubmit} className="auth-form">
            <div className="auth-field"><label>Verification Code</label><input type="text" value={mfaCode} onChange={e => setMfaCode(e.target.value.replace(/\D/g, "").slice(0, 6))} className="auth-input" placeholder="6-digit code" inputMode="numeric" autoFocus required /></div>
            <button type="submit" className="auth-btn" disabled={loading || mfaCode.length < 6}>{loading ? "Verifying..." : "Verify & Sign In"}</button>
          </form>
          <button type="button" onClick={handleCancelMfa} style={{ display: "block", margin: "0.75rem auto 0", background: "none", border: "none", cursor: "pointer", color: "#16a34a", fontSize: "0.85rem" }}>Use a different account</button>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-header"><img src="/images/logo.jpg" alt="" style={{ height: "48px", width: "auto" }} /><h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: "0.5rem 0 0", color: "var(--color-primary, #16a34a)" }}>Cauvery Store</h1><p>Seller Portal</p></div>
        {error && <div className="auth-error">{error}</div>}
        <form onSubmit={handleLogin} className="auth-form">
          <div className="auth-field"><label>Email</label><input type="text" value={form.email} onChange={e=>setForm({...form,email:e.target.value})} className="auth-input" required /></div>
          <div className="auth-field"><label>Password</label><input type="password" value={form.password} onChange={e=>setForm({...form,password:e.target.value})} className="auth-input" required /></div>
          <button type="submit" className="auth-btn" disabled={loading}>{loading?"Logging in...":"Login"}</button>
        </form>
        <div style={{ textAlign:"center", marginTop:"1rem", fontSize:"0.85rem", color:"#64748b" }}>
          New seller? <Link to="/seller/register" style={{ color:"var(--color-primary)", fontWeight:600 }}>Register here</Link>
        </div>
      </div>
    </div>
  );
};

export default SellerLogin;
