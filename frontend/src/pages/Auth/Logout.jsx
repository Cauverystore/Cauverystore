import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import '../../styles/auth.css';

const Logout = () => {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const [message, setMessage] = useState('Logging out...');

  useEffect(() => {
    const performLogout = async () => {
      try {
        await logout();
        setMessage('You have been logged out successfully.');
      } catch {
        setMessage('Logout completed.');
      }
      setTimeout(() => navigate('/login', { replace: true }), 1000);
    };
    performLogout();
  }, [logout, navigate]);

  return (
    <div className="auth-page">
      <div className="auth-container">
        <div className="auth-card" style={{ textAlign: 'center' }}>
          <div className="auth-header">
            <img src="/images/logo.jpg" alt="" className="auth-logo" style={{ height: "48px", width: "auto" }} />
            <div style={{ fontSize: "1.5rem", fontWeight: 700, color: "var(--color-primary, #16a34a)" }}>Cauvery Store</div>
            <h1 className="auth-title">Goodbye!</h1>
            <p className="auth-subtitle">{message}</p>
          </div>
          <div className="auth-loading">
            <div className="auth-loading-spinner"></div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Logout;
