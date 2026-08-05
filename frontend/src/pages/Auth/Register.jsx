import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
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

function getPasswordStrength(password) {
  const met = PASSWORD_REQUIREMENTS.filter((r) => r.test(password)).length;
  if (met <= 2) return { label: 'Weak', className: 'weak' };
  if (met <= 4) return { label: 'Medium', className: 'medium' };
  return { label: 'Strong', className: 'strong' };
}

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

const Register = () => {
  const navigate = useNavigate();
  const { register, loginWithGoogle } = useAuth();

  const [form, setForm] = useState({
    fullName: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: '',
  });
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [touched, setTouched] = useState({});
  const googleButtonRef = useRef(null);

  const handleGoogleCredential = useCallback(async (response) => {
    setError('');
    try {
      const data = await loginWithGoogle(response.credential, false);
      navigate(data.profileIncomplete ? '/complete-profile' : '/');
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Google sign-up failed. Please try again.');
    }
  }, [loginWithGoogle, navigate]);

  const handleNativeGoogleSignIn = useCallback(async () => {
    setError('');
    try {
      const result = await GoogleAuthNative.signIn();
      const data = await loginWithGoogle(result.idToken, false);
      navigate(data.profileIncomplete ? '/complete-profile' : '/');
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Google sign-up failed. Please try again.');
    }
  }, [loginWithGoogle, navigate]);

  useEffect(() => {
    if (isNativeApp) return;
    const clientId = process.env.REACT_APP_GOOGLE_CLIENT_ID;
    if (!clientId) return;

    let cancelled = false;
    const tryRender = () => {
      if (cancelled) return;
      if (window.google?.accounts?.id && googleButtonRef.current) {
        window.google.accounts.id.initialize({ client_id: clientId, callback: handleGoogleCredential });
        googleButtonRef.current.innerHTML = '';
        window.google.accounts.id.renderButton(googleButtonRef.current, { theme: 'outline', size: 'large', width: 320, text: 'signup_with' });
      } else {
        setTimeout(tryRender, 200);
      }
    };
    tryRender();
    return () => { cancelled = true; };
  }, [handleGoogleCredential]);

  const strength = getPasswordStrength(form.password);
  const passwordsMatch = form.password === form.confirmPassword;
  const emailValid = form.email === '' || isValidEmail(form.email);

  const update = (field, value) => setForm((prev) => ({ ...prev, [field]: value }));
  const blur = (field) => setTouched((prev) => ({ ...prev, [field]: true }));

  const canSubmit =
    form.fullName.trim() &&
    isValidEmail(form.email) &&
    form.phone.trim().length >= 10 &&
    form.password.length >= 8 &&
    passwordsMatch &&
    agreeTerms;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!passwordsMatch) {
      setError('Passwords do not match');
      return;
    }

    setLoading(true);
    try {
      await register({
        fullName: form.fullName,
        username: form.email.split('@')[0],
        email: form.email,
        phone: form.phone,
        password: form.password,
      });
      setSuccess('Account created successfully! Redirecting to login...');
      setTimeout(() => navigate('/login'), 2000);
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Registration failed. Please try again.');
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
            <h1 className="auth-title">Create Account</h1>
            <p className="auth-subtitle">Join Cauvery Store today</p>
          </div>

          {error && <div className="auth-alert error">{error}</div>}
          {success && <div className="auth-alert success">{success}</div>}

          <form onSubmit={handleSubmit} className="auth-form">
            <div className="auth-field">
              <label className="auth-field-label" htmlFor="register-name">Full Name</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128100;</span>
                <input
                  id="register-name"
                  type="text"
                  className={`auth-input${touched.fullName && !form.fullName.trim() ? ' error' : ''}`}
                  placeholder="Enter your full name"
                  value={form.fullName}
                  onChange={(e) => update('fullName', e.target.value)}
                  onBlur={() => blur('fullName')}
                  required
                />
              </div>
            </div>

            <div className="auth-field">
              <label className="auth-field-label" htmlFor="register-email">Email</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#9993;</span>
                <input
                  id="register-email"
                  type="email"
                  className={`auth-input${touched.email && !emailValid ? ' error' : touched.email && emailValid && form.email ? ' valid' : ''}`}
                  placeholder="Enter your email"
                  value={form.email}
                  onChange={(e) => update('email', e.target.value)}
                  onBlur={() => blur('email')}
                  required
                />
              </div>
              {touched.email && !emailValid && form.email && (
                <span className="auth-field-error">Please enter a valid email address</span>
              )}
            </div>

            <div className="auth-field">
              <label className="auth-field-label" htmlFor="register-phone">Phone</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128222;</span>
                <input
                  id="register-phone"
                  type="tel"
                  className={`auth-input${touched.phone && form.phone && form.phone.length < 10 ? ' error' : ''}`}
                  placeholder="Enter your phone number"
                  value={form.phone}
                  onChange={(e) => update('phone', e.target.value.replace(/\D/g, ''))}
                  onBlur={() => blur('phone')}
                  required
                />
              </div>
            </div>

            <div className="auth-field">
              <label className="auth-field-label" htmlFor="register-password">Password</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128274;</span>
                <input
                  id="register-password"
                  type={showPassword ? 'text' : 'password'}
                  className={`auth-input${touched.password && form.password.length < 8 ? ' error' : form.password.length >= 8 ? ' valid' : ''}`}
                  placeholder="Create a strong password"
                  value={form.password}
                  onChange={(e) => update('password', e.target.value)}
                  onBlur={() => blur('password')}
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

              {form.password && (
                <div className="auth-password-strength">
                  <div className="auth-password-strength-bar">
                    <div className={`auth-password-strength-fill ${strength.className}`}></div>
                  </div>
                  <span className="auth-password-strength-text">Strength: {strength.label}</span>
                </div>
              )}

              {touched.password && form.password.length > 0 && (
                <div className="auth-password-requirements">
                  {PASSWORD_REQUIREMENTS.map((req, i) => (
                    <div key={i} className={`auth-password-requirement${req.test(form.password) ? ' met' : ''}`}>
                      <span className="auth-password-requirement-icon">
                        {req.test(form.password) ? '\u2713' : '\u2022'}
                      </span>
                      {req.label}
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="auth-field">
              <label className="auth-field-label" htmlFor="register-confirm-password">Confirm Password</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128274;</span>
                <input
                  id="register-confirm-password"
                  type={showConfirmPassword ? 'text' : 'password'}
                  className={`auth-input${touched.confirmPassword && !passwordsMatch ? ' error' : touched.confirmPassword && passwordsMatch && form.confirmPassword ? ' valid' : ''}`}
                  placeholder="Confirm your password"
                  value={form.confirmPassword}
                  onChange={(e) => update('confirmPassword', e.target.value)}
                  onBlur={() => blur('confirmPassword')}
                  required
                />
                <button
                  type="button"
                  className="auth-password-toggle"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  tabIndex={-1}
                >
                  {showConfirmPassword ? 'Hide' : 'Show'}
                </button>
              </div>
              {touched.confirmPassword && form.confirmPassword && !passwordsMatch && (
                <span className="auth-field-error">Passwords do not match</span>
              )}
            </div>

            <div className="auth-checkbox">
              <input
                type="checkbox"
                id="terms"
                checked={agreeTerms}
                onChange={(e) => setAgreeTerms(e.target.checked)}
              />
              <label htmlFor="terms">
                I agree to the{' '}
                <Link to="/policies#terms" className="auth-link">Terms of Service</Link> and{' '}
                <Link to="/policies#privacy" className="auth-link">Privacy Policy</Link>
              </label>
            </div>

            <button
              type="submit"
              className={`auth-submit-btn${loading ? ' loading' : ''}`}
              disabled={loading || !canSubmit}
            >
              {loading ? 'Creating account...' : 'Create Account'}
            </button>
          </form>

          {isNativeApp && (
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
                Sign up with Google
              </button>
            </>
          )}

          {!isNativeApp && process.env.REACT_APP_GOOGLE_CLIENT_ID && (
            <>
              <div className="auth-divider">or continue with</div>
              <div ref={googleButtonRef} style={{ display: 'flex', justifyContent: 'center' }} />
            </>
          )}

          <div className="auth-footer">
            Already have an account?{' '}
            <Link to="/login" className="auth-link">Sign in</Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Register;
