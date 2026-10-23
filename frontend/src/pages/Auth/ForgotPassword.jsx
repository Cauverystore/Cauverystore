import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import '../../styles/auth.css';
import { useTranslation } from "react-i18next";
import i18n from "../../i18n";

const PASSWORD_REQUIREMENTS = [
  {
    get label() {
      return i18n.t("forgotPassword.atLeast8Characters");
    },

    test: (v) => v.length >= 8
  },
  {
    get label() {
      return i18n.t("forgotPassword.containsUppercaseLetter");
    },

    test: (v) => /[A-Z]/.test(v)
  },
  {
    get label() {
      return i18n.t("forgotPassword.containsLowercaseLetter");
    },

    test: (v) => /[a-z]/.test(v)
  },
  {
    get label() {
      return i18n.t("forgotPassword.containsANumber");
    },

    test: (v) => /\d/.test(v)
  },
  {
    get label() {
      return i18n.t("forgotPassword.containsSpecialCharacter");
    },

    test: (v) => /[!@#$%^&*(),.?":{}|<>]/.test(v)
  },
];

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

const ForgotPassword = () => {
  const { t } = useTranslation();

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
    if (!isValidEmail(email)) { setError(t("forgotPassword.pleaseEnterAValidEmail")); return; }
    setLoading(true);
    try {
      await requestPasswordReset(email);
      setInfo(t("forgotPassword.anOtpHasBeenSent"));
      setStep(2);
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || t("forgotPassword.failedToSendOtpPlease"));
    } finally {
      setLoading(false);
    }
  };

  const handleRequestLink = async () => {
    setError(''); setInfo('');
    if (!isValidEmail(email)) { setError(t("forgotPassword.pleaseEnterAValidEmail")); return; }
    setLinkLoading(true);
    try {
      await requestPasswordResetLink(email);
      setLinkSent(true);
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || t("forgotPassword.failedToSendResetLink"));
    } finally {
      setLinkLoading(false);
    }
  };

  const handleResendOtp = async () => {
    setError(''); setInfo(''); setLoading(true);
    try {
      await requestPasswordReset(email);
      setInfo(t("forgotPassword.aNewOtpHasBeen"));
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || t("forgotPassword.failedToResendOtp"));
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (e) => {
    e.preventDefault();
    setError(''); setInfo('');
    if (!otp.trim()) { setError(t("forgotPassword.pleaseEnterTheOtpSent")); return; }
    if (!passwordValid) { setError(t("forgotPassword.passwordDoesNotMeetThe")); return; }
    if (!passwordsMatch) { setError(t("forgotPassword.passwordsDoNotMatch")); return; }
    setLoading(true);
    try {
      await resetPassword(email, otp.trim(), newPassword);
      setSuccess(t("forgotPassword.passwordResetSuccessfullyRedirecti"));
      setTimeout(() => navigate('/login'), 2000);
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || t("forgotPassword.failedToResetPasswordPlease"));
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
            <div style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--color-primary, #0E5C5C)' }}>Cauvery Store</div>
            <h1 className="auth-title">{step === 1 ? t("forgotPassword.forgotPassword") : t("forgotPassword.resetPassword")}</h1>
            <p className="auth-subtitle">
              {step === 1
                ? t("forgotPassword.enterYourEmailAndWe")
                : `Enter the code sent to ${email}`}
            </p>
          </div>

          {error && <div className="auth-alert error">{error}</div>}
          {info && !error && <div className="auth-alert info">{info}</div>}
          {success && <div className="auth-alert success">{success}</div>}
          {linkSent && !error && (
            <div className="auth-alert info">{t("forgotPassword.aResetLinkHasBeen")}</div>
          )}

          {step === 1 ? (
            <form onSubmit={handleRequestOtp} className="auth-form">
              <div className="auth-field">
                <label className="auth-field-label" htmlFor="fp-email">{t("contactUs.email")}</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#9993;</span>
                  <input
                    id="fp-email"
                    type="email"
                    className="auth-input"
                    placeholder={t("forgotPassword.enterYourEmail")}
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>
              </div>

              <button type="submit" className={`auth-submit-btn${loading ? ' loading' : ''}`} disabled={loading || linkLoading}>
                {loading ? t("contactUs.sending") : t("forgotPassword.sendResetCodeOtp")}
              </button>
              <button
                type="button"
                className="auth-submit-btn"
                style={{ marginTop: '0.6rem', background: 'transparent', color: 'var(--color-primary, #0E5C5C)', border: '1px solid var(--color-primary, #0E5C5C)' }}
                onClick={handleRequestLink}
                disabled={loading || linkLoading}
              >
                {linkLoading ? t("contactUs.sending") : t("forgotPassword.emailMeAResetLink")}
              </button>
            </form>
          ) : (
            <form onSubmit={handleResetPassword} className="auth-form">
              <div className="auth-field">
                <label className="auth-field-label" htmlFor="fp-otp">{t("forgotPassword.otpCode")}</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#128274;</span>
                  <input
                    id="fp-otp"
                    type="text"
                    className="auth-input"
                    placeholder={t("forgotPassword.enter6DigitCode")}
                    value={otp}
                    onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    maxLength={6}
                    required
                  />
                </div>
              </div>

              <div className="auth-field">
                <label className="auth-field-label" htmlFor="fp-new-password">{t("profile.newPassword")}</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#128274;</span>
                  <input
                    id="fp-new-password"
                    type={showPassword ? 'text' : 'password'}
                    className="auth-input"
                    placeholder={t("forgotPassword.createAStrongPassword")}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    required
                  />
                  <button type="button" className="auth-password-toggle" onClick={() => setShowPassword(!showPassword)} tabIndex={-1}>
                    {showPassword ? t("forgotPassword.hide") : t("forgotPassword.show")}
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
                <label className="auth-field-label" htmlFor="fp-confirm-password">{t("profile.confirmNewPassword")}</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#128274;</span>
                  <input
                    id="fp-confirm-password"
                    type={showPassword ? 'text' : 'password'}
                    className="auth-input"
                    placeholder={t("forgotPassword.confirmYourPassword")}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required
                  />
                </div>
                {confirmPassword && !passwordsMatch && (
                  <span className="auth-field-error">{t("forgotPassword.passwordsDoNotMatch")}</span>
                )}
              </div>

              <button
                type="submit"
                className={`auth-submit-btn${loading ? ' loading' : ''}`}
                disabled={loading || !otp.trim() || !passwordValid || !passwordsMatch}
              >
                {loading ? t("forgotPassword.resetting") : t("forgotPassword.resetPassword")}
              </button>

              <button type="button" className="auth-link" style={{ background: 'none', border: 'none', cursor: 'pointer', marginTop: '0.75rem', fontSize: '0.85rem' }} onClick={handleResendOtp} disabled={loading}>{t("forgotPassword.didnTGetACode")}</button>
            </form>
          )}

          <div className="auth-footer">{t("forgotPassword.rememberYourPassword")}{' '}
            <Link to="/login" className="auth-link">{t("forgotPassword.signIn")}</Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ForgotPassword;
