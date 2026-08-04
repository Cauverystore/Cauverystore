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

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

const ForgotPassword = () => {
  const navigate = useNavigate();
  const { requestPasswordReset, resetPassword, requestPasswordResetLink } = useAuth();

  const [step, setStep] = useState(1);
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const [loading, setLoading] = useState(false);
  const [linkLoading, setLinkLoading] = useState(false);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [success, setSuccess] = useState('');
  const [linkSent, setLinkSent] = useState(false);

  const passwordsMatch = newPassword === confirmPassword;
  const passwordValid = PASSWORD_REQUIREMENTS.every((r) => r.test(newPassword));

  const handleRequestOtp = async (e) => {
    e.preventDefault();
    setError(''); setInfo(''); setLinkSent(false);
    if (!isValidEmail(email)) { setError('Please enter a valid email address'); return; }
    setLoading(true);
    try {
      await requestPasswordReset(email);
      setInfo('An OTP has been sent to your email. It expires in 15 minutes.');
      setStep(2);
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Failed to send OTP. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleRequestLink = async () => {
    setError(''); setInfo('');
    if (!isValidEmail(email)) { setError('Please enter a valid email address'); return; }
    setLinkLoading(true);
    try {
      await requestPasswordResetLink(email);
      setLinkSent(true);
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Failed to send reset link. Please try again.');
    } finally {
      setLinkLoading(false);
    }
  };

  const handleResendOtp = async () => {
    setError(''); setInfo(''); setLoading(true);
    try {
      await requestPasswordReset(email);
      setInfo('A new OTP has been sent to your email.');
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Failed to resend OTP.');
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (e) => {
    e.preventDefault();
    setError(''); setInfo('');
    if (!otp.trim()) { setError('Please enter the OTP sent to your email'); return; }
    if (!passwordValid) { setError('Password does not meet the requirements below'); return; }
    if (!passwordsMatch) { setError('Passwords do not match'); return; }
    setLoading(true);
    try {
      await resetPassword(email, otp.trim(), newPassword);
      setSuccess('Password reset successfully! Redirecting to login...');
      setTimeout(() => navigate('/login'), 2000);
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Failed to reset password. Please check your OTP.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-header">
            <img src="/images/logo.jpg" alt="" className="auth-logo" style={{ height: '48px', width: 'auto' }} />
            <div style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--color-primary, #16a34a)' }}>Cauvery Store</div>
            <h1 className="auth-title">{step === 1 ? 'Forgot Password' : 'Reset Password'}</h1>
            <p className="auth-subtitle">
              {step === 1
                ? 'Enter your email and we’ll send you a reset code'
                : `Enter the code sent to ${email}`}
            </p>
          </div>

          {error && <div className="auth-alert error">{error}</div>}
          {info && !error && <div className="auth-alert info">{info}</div>}
          {success && <div className="auth-alert success">{success}</div>}
          {linkSent && !error && (
            <div className="auth-alert info">A reset link has been sent to your email. It expires in 30 minutes.</div>
          )}

          {step === 1 ? (
            <form onSubmit={handleRequestOtp} className="auth-form">
              <div className="auth-field">
                <label className="auth-field-label" htmlFor="fp-email">Email</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#9993;</span>
                  <input
                    id="fp-email"
                    type="email"
                    className="auth-input"
                    placeholder="Enter your email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>
              </div>

              <button type="submit" className={`auth-submit-btn${loading ? ' loading' : ''}`} disabled={loading || linkLoading}>
                {loading ? 'Sending...' : 'Send Reset Code (OTP)'}
              </button>
              <button
                type="button"
                className="auth-submit-btn"
                style={{ marginTop: '0.6rem', background: 'transparent', color: 'var(--color-primary, #16a34a)', border: '1px solid var(--color-primary, #16a34a)' }}
                onClick={handleRequestLink}
                disabled={loading || linkLoading}
              >
                {linkLoading ? 'Sending...' : 'Email Me a Reset Link Instead'}
              </button>
            </form>
          ) : (
            <form onSubmit={handleResetPassword} className="auth-form">
              <div className="auth-field">
                <label className="auth-field-label" htmlFor="fp-otp">OTP Code</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#128274;</span>
                  <input
                    id="fp-otp"
                    type="text"
                    className="auth-input"
                    placeholder="Enter 6-digit code"
                    value={otp}
                    onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    maxLength={6}
                    required
                  />
                </div>
              </div>

              <div className="auth-field">
                <label className="auth-field-label" htmlFor="fp-new-password">New Password</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#128274;</span>
                  <input
                    id="fp-new-password"
                    type={showPassword ? 'text' : 'password'}
                    className="auth-input"
                    placeholder="Create a strong password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    required
                  />
                  <button type="button" className="auth-password-toggle" onClick={() => setShowPassword(!showPassword)} tabIndex={-1}>
                    {showPassword ? 'Hide' : 'Show'}
                  </button>
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
                <label className="auth-field-label" htmlFor="fp-confirm-password">Confirm New Password</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#128274;</span>
                  <input
                    id="fp-confirm-password"
                    type={showPassword ? 'text' : 'password'}
                    className="auth-input"
                    placeholder="Confirm your password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required
                  />
                </div>
                {confirmPassword && !passwordsMatch && (
                  <span className="auth-field-error">Passwords do not match</span>
                )}
              </div>

              <button
                type="submit"
                className={`auth-submit-btn${loading ? ' loading' : ''}`}
                disabled={loading || !otp.trim() || !passwordValid || !passwordsMatch}
              >
                {loading ? 'Resetting...' : 'Reset Password'}
              </button>

              <button type="button" className="auth-link" style={{ background: 'none', border: 'none', cursor: 'pointer', marginTop: '0.75rem', fontSize: '0.85rem' }} onClick={handleResendOtp} disabled={loading}>
                Didn't get a code? Resend
              </button>
            </form>
          )}

          <div className="auth-footer">
            Remember your password?{' '}
            <Link to="/login" className="auth-link">Sign in</Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ForgotPassword;
