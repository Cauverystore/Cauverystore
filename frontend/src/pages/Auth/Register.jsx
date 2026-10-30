import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Capacitor } from '@capacitor/core';
import { useAuth } from '../../context/AuthContext';
import GoogleAuthNative from '../../utils/googleAuthNative';
import { trackSignUp, trackSignUpStarted, trackSignUpFailed } from '../../utils/analytics';
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

    // Must match the backend rule (PasswordPolicyService): any non-alphanumeric character.
    test: (v) => /[^a-zA-Z0-9]/.test(v)
  },
];

const COMMON_EMAIL_TYPOS = {
  'gmial.com': 'gmail.com',
  'gmai.com': 'gmail.com',
  'gmail.co': 'gmail.com',
  'gmail.con': 'gmail.com',
  'gamil.com': 'gmail.com',
  'gmaill.com': 'gmail.com',
  'yahooo.com': 'yahoo.com',
  'yaho.com': 'yahoo.com',
  'yahoo.co': 'yahoo.com',
  'hotmial.com': 'hotmail.com',
  'hotmail.con': 'hotmail.com',
  'outlok.com': 'outlook.com',
  'outlook.con': 'outlook.com',
};

function suggestEmail(email) {
  const at = email.lastIndexOf('@');
  if (at < 1) return null;
  const fixed = COMMON_EMAIL_TYPOS[email.slice(at + 1).toLowerCase()];
  return fixed ? `${email.slice(0, at)}@${fixed}` : null;
}

// Indian mobile numbers: accept pasted "+91 98765 43210" / "098765 43210" and keep the 10 digits.
function normalizePhone(raw) {
  let digits = raw.replace(/\D/g, '');
  if (digits.length >= 12 && digits.startsWith('91')) digits = digits.slice(2);
  if (digits.length >= 11 && digits.startsWith('0')) digits = digits.slice(1);
  return digits.slice(0, 10);
}

function getPasswordStrength(password) {
  const met = PASSWORD_REQUIREMENTS.filter((r) => r.test(password)).length;
  if (met <= 2) return { label: i18n.t("register.weak"), className: 'weak' };
  if (met <= 4) return { label: i18n.t("register.medium"), className: 'medium' };
  return { label: i18n.t("register.strong"), className: 'strong' };
}

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

const Register = () => {
  const altText = useAltText();
  const { t } = useTranslation();

  const navigate = useNavigate();
  const { register, login, loginWithGoogle } = useAuth();

  const [form, setForm] = useState({
    fullName: '',
    email: '',
    phone: '',
    password: '',
  });
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [emailTaken, setEmailTaken] = useState(false);
  const [success, setSuccess] = useState('');
  const [touched, setTouched] = useState({});
  const googleButtonRef = useRef(null);
  const startedRef = useRef(false);

  const handleGoogleCredential = useCallback(async (response) => {
    setError('');
    try {
      const data = await loginWithGoogle(response.credential, false);
      navigate(data.profileIncomplete ? '/complete-profile' : '/');
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || t("register.googleSignUpFailedPlease"));
    }
  }, [loginWithGoogle, navigate]);

  const handleNativeGoogleSignIn = useCallback(async () => {
    setError('');
    try {
      const result = await GoogleAuthNative.signIn();
      const data = await loginWithGoogle(result.idToken, false);
      navigate(data.profileIncomplete ? '/complete-profile' : '/');
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || t("register.googleSignUpFailedPlease"));
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
  const allRequirementsMet = PASSWORD_REQUIREMENTS.every((r) => r.test(form.password));
  const emailValid = form.email === '' || isValidEmail(form.email);
  // Only after the field loses focus, so a correct address typed letter by letter never flashes a suggestion.
  const emailSuggestion = emailValid && touched.email ? suggestEmail(form.email.trim()) : null;
  const phoneValid = form.phone.length === 10;

  const update = (field, value) => {
    if (!startedRef.current) {
      startedRef.current = true;
      trackSignUpStarted('email');
    }
    if (field === 'email') setEmailTaken(false);
    setForm((prev) => ({ ...prev, [field]: value }));
  };
  const blur = (field) => setTouched((prev) => ({ ...prev, [field]: true }));

  const canSubmit =
    form.fullName.trim() &&
    isValidEmail(form.email.trim()) &&
    phoneValid &&
    allRequirementsMet &&
    agreeTerms;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setEmailTaken(false);
    setSuccess('');

    const email = form.email.trim().toLowerCase();

    setLoading(true);
    try {
      await register({
        fullName: form.fullName.trim(),
        username: email.split('@')[0],
        email,
        phone: form.phone,
        password: form.password,
      });
      trackSignUp('email');
    } catch (err) {
      const message = err.response?.data?.error || err.response?.data?.message || t("register.registrationFailedPleaseTryAgain");
      const taken = /already registered/i.test(message);
      setEmailTaken(taken);
      setError(taken ? t("register.emailAlreadyRegistered") : message);
      trackSignUpFailed('email', taken ? 'email_taken' : 'error');
      setLoading(false);
      return;
    }

    // Account exists now. Sign the customer straight in instead of making them
    // retype the credentials they just chose; fall back to the login page if that fails.
    try {
      const data = await login(email, form.password, 'customer', false);
      if (data && data.mfaRequired) {
        navigate('/login');
      } else {
        navigate(data && data.profileIncomplete ? '/complete-profile' : '/');
      }
    } catch {
      setSuccess(t("register.accountCreatedSuccessfullyRedirect"));
      setTimeout(() => navigate('/login'), 1500);
    } finally {
      setLoading(false);
    }
  };

  const googleSection = (
    <>
      {isNativeApp && (
        <button
          type="button"
          className="auth-social-btn"
          onClick={handleNativeGoogleSignIn}
          style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem', width: '100%' }}
        >
          <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
            <path fill="#FFC107" d="M43.611 20.083H42V20H24v8h11.303c-1.649 4.657-6.08 8-11.303 8-6.627 0-12-5.373-12-12s5.373-12 12-12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 12.955 4 4 12.955 4 24s8.955 20 20 20 20-8.955 20-20c0-1.341-.138-2.65-.389-3.917z" />
            <path fill="#FF3D00" d="m6.306 14.691 6.571 4.819C14.655 15.108 18.961 12 24 12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 16.318 4 9.656 8.337 6.306 14.691z" />
            <path fill="#4CAF50" d="M24 44c5.166 0 9.86-1.977 13.409-5.192l-6.19-5.238A11.91 11.91 0 0 1 24 36c-5.202 0-9.619-3.317-11.283-7.946l-6.522 5.025C9.505 39.556 16.227 44 24 44z" />
            <path fill="#1976D2" d="M43.611 20.083H42V20H24v8h11.303a12.04 12.04 0 0 1-4.087 5.571l.003-.002 6.19 5.238C36.971 39.205 44 34 44 24c0-1.341-.138-2.65-.389-3.917z" />
          </svg>{t("register.signUpWithGoogle")}</button>
      )}
      {!isNativeApp && process.env.REACT_APP_GOOGLE_CLIENT_ID && (
        <div ref={googleButtonRef} style={{ display: 'flex', justifyContent: 'center' }} />
      )}
      {(isNativeApp || process.env.REACT_APP_GOOGLE_CLIENT_ID) && (
        <div className="auth-divider">{t("register.orSignUpWithEmail")}</div>
      )}
    </>
  );

  return (
    <div className="auth-page">
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-header">
            <img src="/images/logo.jpg" alt={altText.logo()} className="auth-logo" style={{ height: "48px", width: "auto" }} />
            <div style={{ fontSize: "1.5rem", fontWeight: 700, color: "var(--color-primary, #0E5C5C)" }}>Cauvery Store</div>
            <h1 className="auth-title">{t("register.createAccount")}</h1>
            <p className="auth-subtitle">{t("register.joinCauveryStoreToday")}</p>
          </div>

          {error && (
            <div className="auth-alert error" role="alert">
              {error}
              {emailTaken && (
                <>
                  {' '}
                  <Link to="/login" className="auth-link">{t("forgotPassword.signIn")}</Link>
                  {' · '}
                  <Link to="/forgot-password" className="auth-link">{t("register.forgotYourPassword")}</Link>
                </>
              )}
            </div>
          )}
          {success && <div className="auth-alert success">{success}</div>}

          {googleSection}

          <form onSubmit={handleSubmit} className="auth-form">
            <div className="auth-field">
              <label className="auth-field-label" htmlFor="register-name">{t("checkout.fullName")}</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128100;</span>
                <input
                  id="register-name"
                  type="text"
                  className={`auth-input${touched.fullName && !form.fullName.trim() ? ' error' : ''}`}
                  placeholder={t("register.enterYourFullName")}
                  value={form.fullName}
                  onChange={(e) => update('fullName', e.target.value)}
                  onBlur={() => blur('fullName')}
                  autoComplete="name"
                  required
                />
              </div>
            </div>

            <div className="auth-field">
              <label className="auth-field-label" htmlFor="register-email">{t("contactUs.email")}</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#9993;</span>
                <input
                  id="register-email"
                  type="email"
                  className={`auth-input${touched.email && !emailValid ? ' error' : touched.email && emailValid && form.email ? ' valid' : ''}`}
                  placeholder={t("forgotPassword.enterYourEmail")}
                  value={form.email}
                  onChange={(e) => update('email', e.target.value)}
                  onBlur={() => blur('email')}
                  autoComplete="email"
                  autoCapitalize="none"
                  autoCorrect="off"
                  spellCheck={false}
                  required
                />
              </div>
              {touched.email && !emailValid && form.email && (
                <span className="auth-field-error">{t("forgotPassword.pleaseEnterAValidEmail")}</span>
              )}
              {emailSuggestion && (
                <span className="auth-field-error" style={{ color: 'inherit' }}>
                  <span>
                    {t("register.didYouMean")}{' '}
                    <button
                      type="button"
                      className="auth-link"
                      style={{ background: 'none', border: 0, padding: 0, font: 'inherit' }}
                      onClick={() => update('email', emailSuggestion)}
                    >
                      {emailSuggestion}
                    </button>?
                  </span>
                </span>
              )}
            </div>

            <div className="auth-field">
              <label className="auth-field-label" htmlFor="register-phone">{t("addressBook.phone")}</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128222;</span>
                <input
                  id="register-phone"
                  type="tel"
                  inputMode="numeric"
                  autoComplete="tel-national"
                  className={`auth-input${touched.phone && !phoneValid ? ' error' : ''}`}
                  placeholder={t("completeProfile.enterYourPhoneNumber")}
                  value={form.phone}
                  onChange={(e) => update('phone', normalizePhone(e.target.value))}
                  onBlur={() => blur('phone')}
                  required
                />
              </div>
            </div>

            <div className="auth-field">
              <label className="auth-field-label" htmlFor="register-password">{t("login.password")}</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128274;</span>
                <input
                  id="register-password"
                  type={showPassword ? 'text' : 'password'}
                  className={`auth-input${touched.password && !allRequirementsMet ? ' error' : allRequirementsMet ? ' valid' : ''}`}
                  placeholder={t("forgotPassword.createAStrongPassword")}
                  value={form.password}
                  onChange={(e) => update('password', e.target.value)}
                  onBlur={() => blur('password')}
                  autoComplete="new-password"
                  required
                />
                <button
                  type="button"
                  className="auth-password-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-pressed={showPassword}
                  aria-label={showPassword ? t("forgotPassword.hide") : t("forgotPassword.show")}
                >
                  {showPassword ? t("forgotPassword.hide") : t("forgotPassword.show")}
                </button>
              </div>

              {form.password && (
                <div className="auth-password-strength">
                  <div className="auth-password-strength-bar">
                    <div className={`auth-password-strength-fill ${strength.className}`}></div>
                  </div>
                  <span className="auth-password-strength-text">{t("register.strength")}{" "}{strength.label}</span>
                </div>
              )}

              {(
                <div className="auth-password-requirements" aria-live="polite">
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

            <div className="auth-checkbox">
              <input
                type="checkbox"
                id="terms"
                checked={agreeTerms}
                onChange={(e) => setAgreeTerms(e.target.checked)}
              />
              <label htmlFor="terms">{t("register.iAgreeToThe")}{' '}
                <Link to="/terms-and-conditions" className="auth-link">{t("footer.terms")}</Link>{" "}{t("returnsandRefundPolicy.and")}{' '}
                <Link to="/privacy-policy" className="auth-link">{t("footer.privacy")}</Link>
              </label>
            </div>

            <button
              type="submit"
              className={`auth-submit-btn${loading ? ' loading' : ''}`}
              disabled={loading || !canSubmit}
            >
              {loading ? t("register.creatingAccount") : t("register.createAccount")}
            </button>
          </form>

          <div className="auth-footer">{t("register.alreadyHaveAnAccount")}{' '}
            <Link to="/login" className="auth-link">{t("forgotPassword.signIn")}</Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Register;
