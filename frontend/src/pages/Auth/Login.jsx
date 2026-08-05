import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate, Link, useSearchParams } from 'react-router-dom';
import { Capacitor } from '@capacitor/core';
import { useAuth } from '../../context/AuthContext';
import GoogleAuthNative from '../../utils/googleAuthNative';
import '../../styles/auth.css';

const isNativeApp = Capacitor.isNativePlatform();

const PASSWORD_REQUIREMENTS = [
  { label: 'At least 8 characters', test: (v) => v.length >= 8 },
  { label: 'Contains uppercase letter', test: (v) => /[A-Z]/.test(v) },
  { label: 'Contains lowercase letter', test: (v) => /[a-z]/.test(v) },
  { label: 'Contains a number', test: (v) => /\d/.test(v) },
  { label: 'Contains special character', test: (v) => /[!@#$%^&*(),.?":{}|<>]/.test(v) },
];

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
  const [searchParams] = useSearchParams();
  const { login, loginWithGoogle, completeForcedPasswordReset, completeMfaLogin, cancelMfaLogin } = useAuth();
  const [role, setRole] = useState('customer');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [remainingAttempts, setRemainingAttempts] = useState(null);
  const googleButtonRef = useRef(null);

  const [mfaStep, setMfaStep] = useState(null);
  const [mfaCode, setMfaCode] = useState('');

  const [forcedReset, setForcedReset] = useState(null);
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');
  const newPasswordValid = PASSWORD_REQUIREMENTS.every((r) => r.test(newPassword));
  const newPasswordsMatch = newPassword === confirmNewPassword;

  const goAfterLogin = useCallback((role) => {
    const redirect = searchParams.get('redirect');
    if (redirect && redirect.startsWith('/')) navigate(redirect);
    else navigateForRole(navigate, role);
  }, [navigate, searchParams]);

  const handleGoogleCredential = useCallback(async (response) => {
    setError('');
    try {
      const data = await loginWithGoogle(response.credential, rememberMe);
      if (data.profileIncomplete) navigate('/complete-profile');
      else goAfterLogin((data.role || 'customer').toLowerCase());
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Google sign-in failed. Please try again.');
    }
  }, [loginWithGoogle, rememberMe, goAfterLogin, navigate]);

  const handleNativeGoogleSignIn = useCallback(async () => {
    setError('');
    try {
      const result = await GoogleAuthNative.signIn();
      const data = await loginWithGoogle(result.idToken, rememberMe);
      if (data.profileIncomplete) navigate('/complete-profile');
      else goAfterLogin((data.role || 'customer').toLowerCase());
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Google sign-in failed. Please try again.');
    }
  }, [loginWithGoogle, rememberMe, goAfterLogin, navigate]);

  useEffect(() => {
    if (role !== 'customer' || isNativeApp) return;
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
      const data = await login(email, password, role, rememberMe);
      if (data && data.mfaRequired) {
        setMfaStep({ email });
        setLoading(false);
        return;
      }
      goAfterLogin(role);
    } catch (err) {
      if (err.response?.data?.passwordResetRequired) {
        setForcedReset({ email: err.response.data.email || email, oldPassword: password });
        setLoading(false);
        return;
      }
      const msg = err.response?.data?.error || err.response?.data?.message || 'Login failed. Please check your credentials.';
      setError(msg);
      const match = msg.match(/(\d+) attempt\(s\) remaining/);
      if (match) setRemainingAttempts(parseInt(match[1]));
    } finally {
      setLoading(false);
    }
  };

  const handleMfaSubmit = async (e) => {
    e.preventDefault();
    if (!mfaCode.trim()) return;
    setLoading(true);
    setError('');
    try {
      const data = await completeMfaLogin(mfaCode.trim());
      goAfterLogin((data.role || role).toLowerCase());
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Invalid verification code. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelMfa = () => {
    cancelMfaLogin();
    setMfaStep(null);
    setMfaCode('');
    setError('');
  };

  const handleCompleteForcedReset = async (e) => {
    e.preventDefault();
    setError('');
    if (!newPasswordValid) { setError('Password does not meet the requirements below'); return; }
    if (!newPasswordsMatch) { setError('Passwords do not match'); return; }
    setLoading(true);
    try {
      const data = await completeForcedPasswordReset(forcedReset.email, forcedReset.oldPassword, newPassword, rememberMe);
      goAfterLogin((data.role || role).toLowerCase());
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Failed to set new password.');
    } finally {
      setLoading(false);
    }
  };

  if (mfaStep) {
    return (
      <div className="auth-page">
        <div className="auth-container">
          <div className="auth-card">
            <div className="auth-header">
              <img src="/images/logo.jpg" alt="" className="auth-logo" style={{ height: "48px", width: "auto" }} />
              <div style={{ fontSize: "1.5rem", fontWeight: 700, color: "var(--color-primary, #16a34a)" }}>Cauvery Store</div>
              <h1 className="auth-title">Two-Factor Authentication</h1>
              <p className="auth-subtitle">Enter the 6-digit code from your authenticator app to continue as {email}.</p>
            </div>

            {error && (
              <div className="auth-alert error">
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleMfaSubmit} className="auth-form">
              <div className="auth-field">
                <label className="auth-field-label" htmlFor="login-mfa-code">Verification Code</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#128274;</span>
                  <input
                    id="login-mfa-code"
                    type="text"
                    className="auth-input"
                    placeholder="6-digit code"
                    value={mfaCode}
                    onChange={(e) => setMfaCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                    inputMode="numeric"
                    autoFocus
                    required
                  />
                </div>
              </div>

              <button type="submit" className={`auth-submit-btn${loading ? ' loading' : ''}`} disabled={loading || mfaCode.length < 6}>
                {loading ? 'Verifying...' : 'Verify & Sign In'}
              </button>
            </form>

            <button
              type="button"
              onClick={handleCancelMfa}
              className="auth-link"
              style={{ display: 'block', margin: '0.75rem auto 0', background: 'none', border: 'none', cursor: 'pointer' }}
            >
              Use a different account
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (forcedReset) {
    return (
      <div className="auth-page">
        <div className="auth-container">
          <div className="auth-card">
            <div className="auth-header">
              <img src="/images/logo.jpg" alt="" className="auth-logo" style={{ height: "48px", width: "auto" }} />
              <div style={{ fontSize: "1.5rem", fontWeight: 700, color: "var(--color-primary, #16a34a)" }}>Cauvery Store</div>
              <h1 className="auth-title">Set a new password</h1>
              <p className="auth-subtitle">Your password was reset by an administrator. Please choose a new password to continue.</p>
            </div>

            {error && (
              <div className="auth-alert error">
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleCompleteForcedReset} className="auth-form">
              <div className="auth-field">
                <label className="auth-field-label" htmlFor="login-new-password">New Password</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#128274;</span>
                  <input
                    id="login-new-password"
                    type="password"
                    className="auth-input"
                    placeholder="Create a strong password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    required
                  />
                </div>
                {newPassword && (
                  <div className="auth-password-requirements">
                    {PASSWORD_REQUIREMENTS.map((req, i) => (
                      <div key={i} className={`auth-password-requirement${req.test(newPassword) ? ' met' : ''}`}>
                        <span className="auth-password-requirement-icon">{req.test(newPassword) ? '✓' : '•'}</span>
                        {req.label}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="auth-field">
                <label className="auth-field-label" htmlFor="login-confirm-password">Confirm New Password</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#128274;</span>
                  <input
                    id="login-confirm-password"
                    type="password"
                    className="auth-input"
                    placeholder="Confirm your password"
                    value={confirmNewPassword}
                    onChange={(e) => setConfirmNewPassword(e.target.value)}
                    required
                  />
                </div>
                {confirmNewPassword && !newPasswordsMatch && (
                  <span className="auth-field-error">Passwords do not match</span>
                )}
              </div>

              <button
                type="submit"
                className={`auth-submit-btn${loading ? ' loading' : ''}`}
                disabled={loading || !newPasswordValid || !newPasswordsMatch}
              >
                {loading ? 'Saving...' : 'Set New Password & Sign In'}
              </button>
            </form>
          </div>
        </div>
      </div>
    );
  }

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
              <label className="auth-field-label" htmlFor="login-role">I am a</label>
              <select
                id="login-role"
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
              <label className="auth-field-label" htmlFor="login-email">Email or Username</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#9993;</span>
                <input
                  id="login-email"
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
              <label className="auth-field-label" htmlFor="login-password">Password</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128274;</span>
                <input
                  id="login-password"
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

          {role === 'customer' && isNativeApp && (
            <>
              <div className="auth-divider">or continue with</div>
              <button
                type="button"
                className="auth-social-btn"
                onClick={handleNativeGoogleSignIn}
                style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
              >
                <svg width="18" height="18" viewBox="0 0 48 48">
                  <path fill="#FFC107" d="M43.611 20.083H42V20H24v8h11.303c-1.649 4.657-6.08 8-11.303 8-6.627 0-12-5.373-12-12s5.373-12 12-12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 12.955 4 4 12.955 4 24s8.955 20 20 20 20-8.955 20-20c0-1.341-.138-2.65-.389-3.917z" />
                  <path fill="#FF3D00" d="m6.306 14.691 6.571 4.819C14.655 15.108 18.961 12 24 12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 16.318 4 9.656 8.337 6.306 14.691z" />
                  <path fill="#4CAF50" d="M24 44c5.166 0 9.86-1.977 13.409-5.192l-6.19-5.238A11.91 11.91 0 0 1 24 36c-5.202 0-9.619-3.317-11.283-7.946l-6.522 5.025C9.505 39.556 16.227 44 24 44z" />
                  <path fill="#1976D2" d="M43.611 20.083H42V20H24v8h11.303a12.04 12.04 0 0 1-4.087 5.571l.003-.002 6.19 5.238C36.971 39.205 44 34 44 24c0-1.341-.138-2.65-.389-3.917z" />
                </svg>
                Sign in with Google
              </button>
            </>
          )}

          {role === 'customer' && !isNativeApp && process.env.REACT_APP_GOOGLE_CLIENT_ID && (
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
