import React from 'react';
import { useAuth } from '../context/AuthContext';

const ImpersonationBanner = () => {
  const { isImpersonating, impersonatedUser, impersonationSession, stopImpersonation } = useAuth();

  if (!isImpersonating || !impersonatedUser) return null;

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        zIndex: 9999,
        background: 'linear-gradient(90deg, #f59e0b, #d97706)',
        color: '#fff',
        padding: '8px 24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '16px',
        fontSize: '0.85rem',
        fontWeight: 500,
        boxShadow: '0 2px 8px rgba(245,158,11,0.3)',
        flexWrap: 'wrap',
      }}
    >
      <span style={{ fontSize: '1rem' }}>🔒</span>
      <span>
        <strong>Impersonating:</strong> {impersonatedUser?.fullName || impersonatedUser?.name || impersonatedUser?.email || 'Unknown User'}
        {' '}({impersonatedUser?.role || 'N/A'})
        {impersonationSession?.reason && (
          <span> — <em>Reason: {impersonationSession.reason}</em></span>
        )}
      </span>
      <button
        onClick={stopImpersonation}
        style={{
          background: '#dc2626',
          color: '#fff',
          border: 'none',
          borderRadius: '6px',
          padding: '4px 14px',
          fontSize: '0.8rem',
          fontWeight: 600,
          cursor: 'pointer',
          transition: 'background 0.15s',
          whiteSpace: 'nowrap',
        }}
        onMouseEnter={(e) => { e.target.style.background = '#b91c1c'; }}
        onMouseLeave={(e) => { e.target.style.background = '#dc2626'; }}
      >
        Exit Impersonation
      </button>
    </div>
  );
};

export default ImpersonationBanner;
