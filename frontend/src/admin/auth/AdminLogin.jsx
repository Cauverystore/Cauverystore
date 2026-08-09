import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import "../../styles/auth.css";

const AdminLogin = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { login } = useAuth();
  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (searchParams.get('reason') === 'session-expired' && !error) {
      setError("Your session expired. Please sign in again to continue.");
    }
  }, [searchParams]);

  const handleLogin = async (e) => {
    e.preventDefault(); setError(""); setLoading(true);
    try {
      const data = await login(form.email, form.password, "");
      if (data?.role === "SUPER_ADMIN") navigate("/super-admin");
      else navigate("/admin");
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || "Invalid email or password");
    }
    finally { setLoading(false); }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-header"><img src="/images/logo.jpg" alt="" style={{ height: "48px", width: "auto" }} /><h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: "0.5rem 0 0", color: "var(--color-primary, #16a34a)" }}>Cauvery Store</h1><p>Admin Portal</p></div>
        {error && <div className="auth-error">{error}</div>}
        <form onSubmit={handleLogin} className="auth-form">
          <div className="auth-field"><label>Email</label><input type="text" value={form.email} onChange={e=>setForm({...form,email:e.target.value})} className="auth-input" required /></div>
          <div className="auth-field"><label>Password</label><input type="password" value={form.password} onChange={e=>setForm({...form,password:e.target.value})} className="auth-input" required /></div>
          <button type="submit" className="auth-btn" disabled={loading}>{loading?"Logging in...":"Login"}</button>
        </form>
      </div>
    </div>
  );
};

export default AdminLogin;
