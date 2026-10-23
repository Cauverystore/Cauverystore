import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import '../../styles/auth.css';
import { useTranslation } from "react-i18next";

const Unauthorized = () => {
  const { t } = useTranslation();

  const { isAuthenticated } = useAuth();

  const containerStyle = {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: 'linear-gradient(135deg, #edf8f7 0%, #ffffff 50%, #edf8f7 100%)',
    padding: '24px',
  };

  const cardStyle = {
    background: '#fff',
    border: '1px solid #e5e7eb',
    borderRadius: '16px',
    padding: '48px',
    boxShadow: '0 10px 25px rgba(0,0,0,0.08)',
    textAlign: 'center',
    maxWidth: '460px',
    width: '100%',
  };

  const codeStyle = {
    fontSize: '96px',
    fontWeight: 800,
    color: '#dc2626',
    lineHeight: 1,
    marginBottom: '8px',
  };

  const iconStyle = {
    fontSize: '48px',
    marginBottom: '16px',
  };

  return (
    <div style={containerStyle}>
      <div style={cardStyle}>
        <div style={iconStyle}>&#128274;</div>
        <div style={codeStyle}>403</div>
        <h1 style={{ fontSize: '24px', fontWeight: 700, margin: '0 0 8px', color: '#111827' }}>{t("unauthorized.accessDenied")}</h1>
        <p style={{ fontSize: '15px', color: '#6b7280', margin: '0 0 32px', lineHeight: 1.6 }}>{t("unauthorized.youDonTHavePermission")}</p>
        <div style={{ display: 'flex', gap: '12px', justifyContent: 'center' }}>
          <Link
            to={isAuthenticated ? '/' : '/login'}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '8px',
              padding: '12px 28px',
              background: '#0E5C5C',
              color: '#fff',
              border: 'none',
              borderRadius: '10px',
              fontSize: '15px',
              fontWeight: 600,
              textDecoration: 'none',
              cursor: 'pointer',
              transition: 'all 0.15s',
            }}
            onMouseEnter={(e) => { e.target.style.background = '#0a4a4a'; e.target.style.transform = 'translateY(-1px)'; }}
            onMouseLeave={(e) => { e.target.style.background = '#0E5C5C'; e.target.style.transform = 'translateY(0)'; }}
          >
            {isAuthenticated ? t("unauthorized.goToHome") : t("unauthorized.goToLogin")}
          </Link>
        </div>
      </div>
    </div>
  );
};

export default Unauthorized;
