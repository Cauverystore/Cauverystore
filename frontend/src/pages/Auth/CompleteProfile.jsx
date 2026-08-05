import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../../api/axios';
import { useAuth } from '../../context/AuthContext';
import '../../styles/auth.css';

const CompleteProfile = () => {
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
      setError(err.response?.data?.error || err.response?.data?.message || 'Failed to save. Please try again.');
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
            <h1 className="auth-title">One last step</h1>
            <p className="auth-subtitle">
              {user?.fullName ? `Welcome, ${user.fullName}! ` : ''}
              Google didn't share a phone number with us — we need one for order and delivery updates.
            </p>
          </div>

          {error && <div className="auth-alert error">{error}</div>}

          <form onSubmit={handleSubmit} className="auth-form">
            <div className="auth-field">
              <label className="auth-field-label" htmlFor="complete-profile-phone">Phone</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">&#128222;</span>
                <input
                  id="complete-profile-phone"
                  type="tel"
                  className={`auth-input${touched && !phoneValid ? ' error' : ''}`}
                  placeholder="Enter your phone number"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))}
                  onBlur={() => setTouched(true)}
                  autoFocus
                  required
                />
              </div>
              {touched && !phoneValid && (
                <span className="auth-field-error">Enter a valid 10-digit phone number</span>
              )}
            </div>

            <button
              type="submit"
              className={`auth-submit-btn${loading ? ' loading' : ''}`}
              disabled={loading || !phoneValid}
            >
              {loading ? 'Saving...' : 'Continue'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default CompleteProfile;
