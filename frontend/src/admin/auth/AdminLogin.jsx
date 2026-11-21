import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import "../../styles/auth.css";
import useAltText from "../../hooks/useAltText";

/** Accounts that may use the admin panel. Anyone else is signed straight back out. */
export const STAFF_ROLES = ["ADMIN", "SUPER_ADMIN", "EXECUTIVE"];

export const isStaffLogin = (data) => {
  const roles = Array.isArray(data?.roles) && data.roles.length > 0 ? data.roles : [data?.role];
  return roles.some((r) => STAFF_ROLES.includes(String(r || "").toUpperCase()));
};

export const adminHomeFor = (data) => (data?.role === "SUPER_ADMIN" ? "/super-admin" : "/admin");

const errorText = (err, fallback) =>
  err?.response?.data?.error || err?.response?.data?.message || fallback;

const AdminLogin = () => {
  const altText = useAltText();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { login, logout, completeMfaLogin, cancelMfaLogin } = useAuth();
  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [mfaStep, setMfaStep] = useState(null);
  const [mfaCode, setMfaCode] = useState("");

  useEffect(() => {
    if (searchParams.get('reason') === 'session-expired' && !error) {
      setError("Your session expired. Please sign in again to continue.");
    }
  }, [searchParams]);

  // Signed in, but with an account that is not allowed in the admin panel.
  const finish = async (data) => {
    if (!isStaffLogin(data)) {
      try { await logout(); } catch { /* already signed out */ }
      setMfaStep(null);
      setError("This account does not have access to the admin panel.");
      return;
    }
    navigate(adminHomeFor(data));
  };

  const handleLogin = async (e) => {
    e.preventDefault(); setError(""); setLoading(true);
    try {
      const data = await login(form.email, form.password, "");
      if (data?.mfaRequired) {
        if (!isStaffLogin(data)) {
          cancelMfaLogin();
          setError("This account does not have access to the admin panel.");
          return;
        }
        setMfaCode("");
        setMfaStep({ email: data.email || form.email });
        return;
      }
      await finish(data);
    } catch (err) {
      setError(errorText(err, "Invalid email or password"));
    } finally { setLoading(false); }
  };

  const handleMfaSubmit = async (e) => {
    e.preventDefault(); setError(""); setLoading(true);
    try {
      const data = await completeMfaLogin(mfaCode.trim());
      await finish(data);
    } catch (err) {
      const msg = errorText(err, "That code did not work. Please try again.");
      setError(msg);
      setMfaCode("");
      // The sign-in ticket ran out or the account locked: start again from the password.
      if (/expired|locked/i.test(msg)) { cancelMfaLogin(); setMfaStep(null); }
    } finally { setLoading(false); }
  };

  const handleCancelMfa = () => {
    cancelMfaLogin(); setMfaStep(null); setMfaCode(""); setError("");
  };

  const header = (subtitle) => (
    <div className="auth-header"><img src="/images/logo.jpg" alt={altText.logo()} style={{ height: "48px", width: "auto" }} /><h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: "0.5rem 0 0", color: "var(--color-primary, #16a34a)" }}>Cauvery Store</h1><p>{subtitle}</p></div>
  );

  const linkStyle = { display: "block", margin: "0.75rem auto 0", background: "none", border: "none", cursor: "pointer", color: "var(--color-primary, #00788C)", fontSize: "0.85rem", textAlign: "center" };

  if (mfaStep) {
    return (
      <div className="auth-page">
        <div className="auth-card">
          {header("Two-step sign-in")}
          <p style={{ fontSize: "0.85rem", color: "#64748b", marginBottom: "0.5rem" }}>Enter the 6-digit code from your authenticator app to continue as {mfaStep.email}.</p>
          {error && <div className="auth-error" role="alert">{error}</div>}
          <form onSubmit={handleMfaSubmit} className="auth-form">
            <div className="auth-field"><label htmlFor="admin-mfa-code">Verification code</label><input id="admin-mfa-code" type="text" value={mfaCode} onChange={e => setMfaCode(e.target.value.replace(/\D/g, "").slice(0, 6))} className="auth-input" placeholder="6-digit code" inputMode="numeric" autoComplete="one-time-code" autoFocus required /></div>
            <button type="submit" className="auth-btn" disabled={loading || mfaCode.length < 6}>{loading ? "Checking..." : "Verify and sign in"}</button>
          </form>
          <button type="button" onClick={handleCancelMfa} style={linkStyle}>Use a different account</button>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        {header("Admin Portal")}
        {error && <div className="auth-error" role="alert">{error}</div>}
        <form onSubmit={handleLogin} className="auth-form">
          <div className="auth-field"><label htmlFor="admin-email">Email</label><input id="admin-email" type="text" value={form.email} onChange={e=>setForm({...form,email:e.target.value})} className="auth-input" autoComplete="username" required /></div>
          <div className="auth-field"><label htmlFor="admin-password">Password</label><input id="admin-password" type="password" value={form.password} onChange={e=>setForm({...form,password:e.target.value})} className="auth-input" autoComplete="current-password" required /></div>
          <button type="submit" className="auth-btn" disabled={loading}>{loading?"Logging in...":"Login"}</button>
        </form>
        <Link to="/forgot-password" style={linkStyle}>Forgot password?</Link>
      </div>
    </div>
  );
};

export default AdminLogin;
