import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate, Link, useSearchParams } from 'react-router-dom';
import { Capacitor } from '@capacitor/core';
import { useAuth } from '../../context/AuthContext';
import GoogleAuthNative from '../../utils/googleAuthNative';
import '../../styles/auth.css';
import { useTranslation } from "react-i18next";
import i18n from "../../i18n";
import useAltText from "../../hooks/useAltText";

const isNativeApp = Capacitor.isNativePlatform();

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

/**
 * Who may sign in with Google.
 *
 * The backend never restricted this - authenticateWithGoogle looks the account up by email and
 * logs it in with whatever role it already holds, only assigning CUSTOMER when creating someone
 * new. The gate was here in the form, and it left sellers with no Google option at all even
 * though their account already carries customer capability.
 *
 * Staff stay off the list deliberately. An executive, admin or super-admin account reachable
 * through a consumer Google account means a compromised mailbox is a compromised store, and
 * those accounts are few enough that a password is not the friction it is for shoppers.
 */
const GOOGLE_ROLES = ['customer', 'seller'];

const ROLES = [
  {
    value: 'customer',

    get label() {
      return i18n.t("login.customer");
    }
  },
  {
    value: 'seller',

    get label() {
      return i18n.t("login.seller");
    }
  },
  {
    value: 'executive',

    get label() {
      return i18n.t("login.executive");
    }
  },
  {
    value: 'admin',

    get label() {
      return i18n.t("login.admin");
    }
  },
  {
    value: 'super_admin',

    get label() {
      return i18n.t("login.superAdmin");
    }
  },
];

const navigateForRole = (navigate, role) => {
  if (role === 'customer') navigate('/');
  else if (role === 'seller') navigate('/seller/dashboard');
  else if (role === 'super_admin') navigate('/super-admin');
  else if (role === 'executive') navigate('/admin/executive-dashboard');
  else navigate('/admin');
};

const Login = () => {
  const altText = useAltText();
  const { t } = useTranslation();

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

  useEffect(() => {
    if (searchParams.get('reason') === 'session-expired' && !error) {
      setError(t("login.yourSessionExpiredPleaseSign"));
    }
  }, [searchParams]);

  const handleGoogleCredential = useCallback(async (response) => {
    setError('');
    try {
      const data = await loginWithGoogle(response.credential, rememberMe);
      if (data.profileIncomplete) navigate('/complete-profile');
      else goAfterLogin((data.role || 'customer').toLowerCase());
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || t("login.googleSignInFailedPlease"));
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
      setError(err.response?.data?.error || err.response?.data?.message || t("login.googleSignInFailedPlease"));
    }
  }, [loginWithGoogle, rememberMe, goAfterLogin, navigate]);

  useEffect(() => {
    if (!GOOGLE_ROLES.includes(role) || isNativeApp) return;
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
      const msg = err.response?.data?.error || err.response?.data?.message || i18n.t("msgs.loginFailed");
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
      setError(err.response?.data?.error || err.response?.data?.message || t("login.invalidVerificationCodePleaseTry"));
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
    if (!newPasswordValid) { setError(t("forgotPassword.passwordDoesNotMeetThe")); return; }
    if (!newPasswordsMatch) { setError(t("forgotPassword.passwordsDoNotMatch")); return; }
    setLoading(true);
    try {
      const data = await completeForcedPasswordReset(forcedReset.email, forcedReset.oldPassword, newPassword, rememberMe);
      goAfterLogin((data.role || role).toLowerCase());
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || t("login.failedToSetNewPassword"));
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
              <img src="/images/logo.jpg" alt={altText.logo()} className="auth-logo" style={{ height: "48px", width: "auto" }} />
              <div style={{ fontSize: "1.5rem", fontWeight: 700, color: "var(--color-primary, #0E5C5C)" }}>Cauvery Store</div>
              <h1 className="auth-title">{t("login.twoFactorAuthentication")}</h1>
              <p className="auth-subtitle">{t("login.enterThe6DigitCode")}{" "}{email}.</p>
            </div>

            {error && (
              <div className="auth-alert error">
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleMfaSubmit} className="auth-form">
              <div className="auth-field">
                <label className="auth-field-label" htmlFor="login-mfa-code">{t("login.verificationCode")}</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#128274;</span>
                  <input
                    id="login-mfa-code"
                    type="text"
                    className="auth-input"
                    placeholder={t("profile.6DigitCode")}
                    value={mfaCode}
                    onChange={(e) => setMfaCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                    inputMode="numeric"
                    autoFocus
                    required
                  />
                </div>
              </div>

              <button type="submit" className={`auth-submit-btn${loading ? ' loading' : ''}`} disabled={loading || mfaCode.length < 6}>
                {loading ? t("login.verifying") : t("login.verifySignIn")}
              </button>
            </form>

            <button
              type="button"
              onClick={handleCancelMfa}
              className="auth-link"
              style={{ display: 'block', margin: '0.75rem auto 0', background: 'none', border: 'none', cursor: 'pointer' }}
            >{t("login.useADifferentAccount")}</button>
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
              <img src="/images/logo.jpg" alt={altText.logo()} className="auth-logo" style={{ height: "48px", width: "auto" }} />
              <div style={{ fontSize: "1.5rem", fontWeight: 700, color: "var(--color-primary, #0E5C5C)" }}>Cauvery Store</div>
              <h1 className="auth-title">{t("login.setANewPassword")}</h1>
              <p className="auth-subtitle">{t("login.yourPasswordWasResetBy")}</p>
            </div>

            {error && (
              <div className="auth-alert error">
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleCompleteForcedReset} className="auth-form">
              <div className="auth-field">
                <label className="auth-field-label" htmlFor="login-new-password">{t("profile.newPassword")}</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#128274;</span>
                  <input
                    id="login-new-password"
                    type="password"
                    className="auth-input"
                    placeholder={t("forgotPassword.createAStrongPassword")}
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
                <label className="auth-field-label" htmlFor="login-confirm-password">{t("profile.confirmNewPassword")}</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">&#128274;</span>
                  <input
                    id="login-confirm-password"
                    type="password"
                    className="auth-input"
                    placeholder={t("forgotPassword.confirmYourPassword")}
                    value={confirmNewPassword}
                    onChange={(e) => setConfirmNewPassword(e.target.value)}
                    required
                  />
                </div>
                {confirmNewPassword && !newPasswordsMatch && (
                  <span className="auth-field-error">{t("forgotPassword.passwordsDoNotMatch")}</span>
                )}
              </div>

              <button
                type="submit"
                className={`auth-submit-btn${loading ? ' loading' : ''}`}
                disabled={loading || !newPasswordValid || !newPasswordsMatch}
              >
                {loading ? t("addressBook.saving") : t("login.setNewPasswordSignIn")}
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
            <img src="/images/logo.jpg" alt={altText.logo()} className="auth-logo" style={{ height: "48px", width: "auto" }} />
            <div style={{ fontSize: "1.5rem", fontWeight: 700, color: "var(--color-primary, #0E5C5C)" }}>Cauvery Store</div>
            <h1 className="auth-title">{t("login.welcomeBack")}</h1>
            <p className="auth-subtitle">{t("login.signInToYourAccount")}</p>
          </div>

          {error && (
            <div className="auth-alert error">
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="auth-form">
            <div className="auth-field">
              <label className="auth-field-label" htmlFor="login-role">{t("login.iAmA")}</label>
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

            {GOOGLE_ROLES.includes(role) && isNativeApp && (
            <>
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
                </svg>{t("login.signInWithGoogle")}</button>
              <div className="auth-divider">{t("login.orSignInWithYour")}</div>
            </>
          )}

            {GOOGLE_ROLES.includes(role) && !isNativeApp && process.env.REACT_APP_GOOGLE_CLIENT_ID && (
            <>
              <div ref={googleButtonRef} style={{ display: 'flex', justifyContent: 'center' }} />
              <div className="auth-divider">{t("login.orSignInWithYour")}</div>
            </>
            )}

            <div className="auth-field">
              <label className="auth-field-label" htmlFor="login-email">{t("login.emailOrUsername")}</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#9993;</span>
                <input
                  id="login-email"
                  type="text"
                  className="auth-input"
                  placeholder={t("login.enterYourEmailOrUsername")}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  autoComplete="username"
                  required
                />
              </div>
            </div>

            <div className="auth-field">
              <label className="auth-field-label" htmlFor="login-password">{t("login.password")}</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128274;</span>
                <input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  className="auth-input"
                  placeholder={t("login.enterYourPassword")}
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
                  {showPassword ? t("forgotPassword.hide") : t("forgotPassword.show")}
                </button>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '0.25rem' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.85rem', color: 'var(--gray-600)', cursor: 'pointer' }}>
                <input type="checkbox" checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)}
                  style={{ width: 16, height: 16, accentColor: 'var(--color-primary)' }} />{t("login.rememberMe")}</label>
              <Link to="/forgot-password" className="auth-link" style={{ fontSize: '0.85rem' }}>{t("login.forgotPassword")}</Link>
            </div>

            <button
              type="submit"
              className={`auth-submit-btn${loading ? ' loading' : ''}`}
              disabled={loading}
            >
              {loading ? t("login.signingIn") : t("nav.signIn")}
            </button>
          </form>

          <div className="auth-footer">
            {role === 'customer' && (
              <span>{t("login.donTHaveAnAccount")}{' '}
                <Link to="/register" className="auth-link">{t("login.createAccount")}</Link>
              </span>
            )}
            {role !== 'customer' && (
              <span style={{ color: 'var(--color-text-tertiary)', fontSize: 'var(--font-size-xs)' }}>{t("login.accountCreationFor")}{" "}{role}{t("login.sIsManagedByAdministrators")}</span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
