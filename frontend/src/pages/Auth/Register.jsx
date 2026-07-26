import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import '../../styles/auth.css';

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
  const { register } = useAuth();

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
              <label className="auth-field-label">Full Name</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128100;</span>
                <input
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
              <label className="auth-field-label">Email</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#9993;</span>
                <input
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
              <label className="auth-field-label">Phone</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128222;</span>
                <input
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
              <label className="auth-field-label">Password</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128274;</span>
                <input
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
              <label className="auth-field-label">Confirm Password</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128274;</span>
                <input
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
                <Link to="/terms" className="auth-link">Terms of Service</Link> and{' '}
                <Link to="/privacy" className="auth-link">Privacy Policy</Link>
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
