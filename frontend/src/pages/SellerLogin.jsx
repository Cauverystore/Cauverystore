import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import "../styles/auth.css";

const SellerLogin = () => {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e) => {
    e.preventDefault(); setError(""); setLoading(true);
    try {
      await login(form.email, form.password, "seller");
      navigate("/seller/dashboard");
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || "Invalid credentials");
    }
    finally { setLoading(false); }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-header"><h1>Cauvery Store</h1><p>Seller Portal</p></div>
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
