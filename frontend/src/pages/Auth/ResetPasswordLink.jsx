import React, { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
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

const ResetPasswordLink = () => {
  const { t } = useTranslation();

  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';
  const { resetPasswordWithLink } = useAuth();

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const passwordsMatch = newPassword === confirmPassword;
  const passwordValid = PASSWORD_REQUIREMENTS.every((r) => r.test(newPassword));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!token) { setError(t("resetPasswordLink.thisResetLinkIsInvalid")); return; }
    if (!passwordValid) { setError(t("forgotPassword.passwordDoesNotMeetThe")); return; }
    if (!passwordsMatch) { setError(t("forgotPassword.passwordsDoNotMatch")); return; }
    setLoading(true);
    try {
      await resetPasswordWithLink(token, newPassword);
      setSuccess(t("forgotPassword.passwordResetSuccessfullyRedirecti"));
      setTimeout(() => navigate('/login'), 2000);
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || t("resetPasswordLink.failedToResetPasswordThis"));
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
            <h1 className="auth-title">{t("forgotPassword.resetPassword")}</h1>
            <p className="auth-subtitle">{t("resetPasswordLink.chooseANewPasswordFor")}</p>
          </div>

          {error && <div className="auth-alert error">{error}</div>}
          {success && <div className="auth-alert success">{success}</div>}

          {!token ? (
            <div className="auth-alert error">{t("resetPasswordLink.thisResetLinkIsMissing")}</div>
          ) : (
            <form onSubmit={handleSubmit} className="auth-form">
              <div className="auth-field">
                <label className="auth-field-label" htmlFor="rpl-new-password">{t("profile.newPassword")}</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#128274;</span>
                  <input
                    id="rpl-new-password"
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
                <label className="auth-field-label" htmlFor="rpl-confirm-password">{t("profile.confirmNewPassword")}</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#128274;</span>
                  <input
                    id="rpl-confirm-password"
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
                disabled={loading || !passwordValid || !passwordsMatch}
              >
                {loading ? t("forgotPassword.resetting") : t("forgotPassword.resetPassword")}
              </button>
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

export default ResetPasswordLink;
