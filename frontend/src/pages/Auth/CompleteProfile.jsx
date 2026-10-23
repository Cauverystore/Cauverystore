import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../../api/axios';
import { useAuth } from '../../context/AuthContext';
import '../../styles/auth.css';
import { useTranslation } from "react-i18next";

const CompleteProfile = () => {
  const { t } = useTranslation();

  const navigate = useNavigate();
  const { user, refreshUser } = useAuth();
  const [phone, setPhone] = useState('');
  const [touched, setTouched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const phoneValid = phone.trim().length >= 10;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!phoneValid) {
      setTouched(true);
      return;
    }
    setLoading(true);
    setError('');
    try {
      await api.put('/api/users/profile', { phone });
      await refreshUser();
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || t("completeProfile.failedToSavePleaseTry"));
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
            <h1 className="auth-title">{t("completeProfile.oneLastStep")}</h1>
            <p className="auth-subtitle">
              {user?.fullName ? `Welcome, ${user.fullName}! ` : ''}{t("completeProfile.googleDidnTShareA")}</p>
          </div>

          {error && <div className="auth-alert error">{error}</div>}

          <form onSubmit={handleSubmit} className="auth-form">
            <div className="auth-field">
              <label className="auth-field-label" htmlFor="complete-profile-phone">{t("addressBook.phone")}</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128222;</span>
                <input
                  id="complete-profile-phone"
                  type="tel"
                  className={`auth-input${touched && !phoneValid ? ' error' : ''}`}
                  placeholder={t("completeProfile.enterYourPhoneNumber")}
                  value={phone}
                  onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))}
                  onBlur={() => setTouched(true)}
                  autoFocus
                  required
                />
              </div>
              {touched && !phoneValid && (
                <span className="auth-field-error">{t("completeProfile.enterAValid10Digit")}</span>
              )}
            </div>

            <button
              type="submit"
              className={`auth-submit-btn${loading ? ' loading' : ''}`}
              disabled={loading || !phoneValid}
            >
              {loading ? t("addressBook.saving") : t("completeProfile.continue")}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default CompleteProfile;
