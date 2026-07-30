import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import '../../styles/auth.css';

const ROLES = [
  { value: 'customer', label: 'Customer' },
  { value: 'seller', label: 'Seller' },
  { value: 'executive', label: 'Executive' },
  { value: 'admin', label: 'Admin' },
  { value: 'super_admin', label: 'Super Admin' },
];

const navigateForRole = (navigate, role) => {
  if (role === 'customer') navigate('/');
  else if (role === 'seller') navigate('/seller/dashboard');
  else if (role === 'super_admin') navigate('/super-admin');
  else if (role === 'executive') navigate('/admin/executive-dashboard');
  else navigate('/admin');
};

const Login = () => {
  const navigate = useNavigate();
  const { login, loginWithGoogle } = useAuth();
  const [role, setRole] = useState('customer');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [remainingAttempts, setRemainingAttempts] = useState(null);
  const googleButtonRef = useRef(null);

  const handleGoogleCredential = useCallback(async (response) => {
    setError('');
    try {
      const data = await loginWithGoogle(response.credential, rememberMe);
      navigateForRole(navigate, (data.role || 'customer').toLowerCase());
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Google sign-in failed. Please try again.');
    }
  }, [loginWithGoogle, navigate, rememberMe]);

  useEffect(() => {
    if (role !== 'customer') return;
    const clientId = process.env.REACT_APP_GOOGLE_CLIENT_ID;
    if (!clientId) return;

    let cancelled = false;
    const tryRender = () => {
      if (cancelled) return;
      if (window.google?.accounts?.id && googleButtonRef.current) {
        window.google.accounts.id.initialize({ client_id: clientId, callback: handleGoogleCredential });
        googleButtonRef.current.innerHTML = '';
        window.google.accounts.id.renderButton(googleButtonRef.current, { theme: 'outline', size: 'large', width: 320 });
      } else {
        setTimeout(tryRender, 200);
      }
    };
    tryRender();
    return () => { cancelled = true; };
  }, [role, handleGoogleCredential]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setRemainingAttempts(null);
    try {
      await login(email, password, role, rememberMe);
      navigateForRole(navigate, role);
    } catch (err) {
      const msg = err.response?.data?.error || err.response?.data?.message || 'Login failed. Please check your credentials.';
      setError(msg);
      const match = msg.match(/(\d+) attempt\(s\) remaining/);
      if (match) setRemainingAttempts(parseInt(match[1]));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-header">
            <img src="/images/logo.jpg" alt="" className="auth-logo" style={{ height: "48px", width: "auto" }} />
            <div style={{ fontSize: "1.5rem", fontWeight: 700, color: "var(--color-primary, #16a34a)" }}>Cauvery Store</div>
            <h1 className="auth-title">Welcome back</h1>
            <p className="auth-subtitle">Sign in to your account</p>
          </div>

          {error && (
            <div className="auth-alert error">
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="auth-form">
            <div className="auth-field">
              <label className="auth-field-label">I am a</label>
              <select
                className="auth-input"
                style={{ padding: 'var(--spacing-3)', cursor: 'pointer' }}
                value={role}
                onChange={(e) => setRole(e.target.value)}
              >
                {ROLES.map((r) => (
                  <option key={r.value} value={r.value}>{r.label}</option>
                ))}
              </select>
            </div>

            <div className="auth-field">
              <label className="auth-field-label">Email or Username</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#9993;</span>
                <input
                  type="text"
                  className="auth-input"
                  placeholder="Enter your email or username"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  autoComplete="username"
                  required
                />
              </div>
            </div>

            <div className="auth-field">
              <label className="auth-field-label">Password</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128274;</span>
                <input
                  type={showPassword ? 'text' : 'password'}
                  className="auth-input"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
                <button
                  type="button"
                  className="auth-password-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                  tabIndex={-1}
                >
                  {showPassword ? 'Hide' : 'Show'}
                </button>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '0.25rem' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.85rem', color: 'var(--gray-600)', cursor: 'pointer' }}>
                <input type="checkbox" checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)}
                  style={{ width: 16, height: 16, accentColor: 'var(--color-primary)' }} />
                Remember Me
              </label>
              <Link to="/forgot-password" className="auth-link" style={{ fontSize: '0.85rem' }}>Forgot password?</Link>
            </div>

            <button
              type="submit"
              className={`auth-submit-btn${loading ? ' loading' : ''}`}
              disabled={loading}
            >
              {loading ? 'Signing in...' : 'Sign In'}
            </button>
          </form>

          {role === 'customer' && process.env.REACT_APP_GOOGLE_CLIENT_ID && (
            <>
              <div className="auth-divider">or continue with</div>
              <div ref={googleButtonRef} style={{ display: 'flex', justifyContent: 'center' }} />
            </>
          )}

          <div className="auth-footer">
            {role === 'customer' && (
              <span>
                Don't have an account?{' '}
                <Link to="/register" className="auth-link">Create account</Link>
              </span>
            )}
            {role !== 'customer' && (
              <span style={{ color: 'var(--color-text-tertiary)', fontSize: 'var(--font-size-xs)' }}>
                Account creation for {role}s is managed by administrators.
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
