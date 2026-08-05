import React, { createContext, useState, useEffect, useCallback, useContext, useRef } from 'react';
import API from '../utils/axios';

export const AuthContext = createContext(null);

const IMPERSONATION_STORAGE_KEY = 'impersonation_session';

const STORAGE_KEYS = {
  accessToken: 'accessToken',
  user: 'user',
  role: 'role',
  roles: 'roles',
  rememberMe: 'rememberMe',
};

const SESSION_TIMEOUT_MS = 30 * 60 * 1000;

function decodeToken(token) {
  try {
    const payload = token.split('.')[1];
    return JSON.parse(atob(payload));
  } catch { return null; }
}

function isTokenExpired(token) {
  const decoded = decodeToken(token);
  if (!decoded || !decoded.exp) return true;
  return decoded.exp * 1000 < Date.now();
}

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [role, setRole] = useState(null);
  const [roles, setRoles] = useState([]);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [isImpersonating, setIsImpersonating] = useState(false);
  const [impersonationSession, setImpersonationSession] = useState(null);
  const [impersonatedUser, setImpersonatedUser] = useState(null);
  const [lastActivity, setLastActivity] = useState(Date.now());
  const [showSessionWarning, setShowSessionWarning] = useState(false);
  const [pendingMfa, setPendingMfa] = useState(null);
  const timeoutRef = useRef(null);
  const warningRef = useRef(null);

  const clearLocalAuth = useCallback(() => {
    localStorage.removeItem(STORAGE_KEYS.accessToken);
    localStorage.removeItem(STORAGE_KEYS.user);
    localStorage.removeItem(STORAGE_KEYS.role);
    localStorage.removeItem(STORAGE_KEYS.roles);
    localStorage.removeItem('admin_token');
  }, []);

  const logout = useCallback(async () => {
    try {
      await API.post('/api/auth/logout');
    } catch { /* proceed with local logout even if API call fails */ }
    clearLocalAuth();
    setToken(null);
    setUser(null);
    setRole(null);
    setRoles([]);
    setIsAuthenticated(false);
    setError('');
    setShowSessionWarning(false);
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    if (warningRef.current) clearTimeout(warningRef.current);
  }, [clearLocalAuth]);

  const refreshToken = useCallback(async () => {
    try {
      const res = await API.post('/api/auth/refresh');
      const newAccess = res.data.accessToken || res.data.token;
      localStorage.setItem(STORAGE_KEYS.accessToken, newAccess);
      setToken(newAccess);
      return newAccess;
    } catch {
      await logout();
      throw new Error('Refresh failed');
    }
  }, [logout]);

  const persistAuth = (data, fallbackUser, rememberMe) => {
    const accessToken = data.accessToken || data.token;
    const userData = data.user || fallbackUser;
    const userRoleFinal = data.role || 'CUSTOMER';
    const rolesFinal = Array.isArray(data.roles) && data.roles.length > 0 ? data.roles : [userRoleFinal];

    localStorage.setItem(STORAGE_KEYS.accessToken, accessToken);
    localStorage.setItem(STORAGE_KEYS.user, JSON.stringify(userData));
    localStorage.setItem(STORAGE_KEYS.role, userRoleFinal);
    localStorage.setItem(STORAGE_KEYS.roles, JSON.stringify(rolesFinal));
    localStorage.setItem(STORAGE_KEYS.rememberMe, rememberMe ? 'true' : 'false');

    setToken(accessToken);
    setUser(userData);
    setRole(userRoleFinal);
    setRoles(rolesFinal);
    setIsAuthenticated(true);
    setLastActivity(Date.now());

    API.get('/api/auth/me')
      .then((meRes) => {
        const me = meRes.data;
        if (me) {
          setUser(me);
          localStorage.setItem(STORAGE_KEYS.user, JSON.stringify(me));
        }
      })
      .catch(() => {});
  };

  const login = useCallback(async (email, password, userRole, rememberMe = false) => {
    setError('');
    const res = await API.post('/api/auth/login', { email, password, role: userRole });
    const data = res.data;

    if (data.mfaRequired) {
      setPendingMfa({ email, preferredRole: userRole, rememberMe });
      return data;
    }

    const fallbackUser = { email, fullName: data.username || email };
    persistAuth(data, fallbackUser, rememberMe);
    return data;
  }, []);

  const completeMfaLogin = useCallback(async (otp) => {
    if (!pendingMfa) throw new Error('Two-factor verification is required before login');
    setError('');
    const res = await API.post('/api/auth/login-2fa', { email: pendingMfa.email, otp });
    const data = res.data;
    const fallbackUser = { email: pendingMfa.email, fullName: data.username || pendingMfa.email };
    persistAuth(data, fallbackUser, pendingMfa.rememberMe);
    setPendingMfa(null);
    return data;
  }, [pendingMfa]);

  const cancelMfaLogin = useCallback(() => {
    setPendingMfa(null);
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const res = await API.get('/api/auth/me');
      const me = res.data;
      if (me) {
        setUser(me);
        localStorage.setItem(STORAGE_KEYS.user, JSON.stringify(me));
      }
      return me;
    } catch {
      return null;
    }
  }, []);

  const loginWithGoogle = useCallback(async (credential, rememberMe = false) => {
    setError('');
    const res = await API.post('/api/auth/google', { credential });
    const data = res.data;

    const accessToken = data.accessToken || data.token;
    const userData = data.user || { email: data.email, fullName: data.username || data.email };
    const userRoleFinal = data.role || 'CUSTOMER';
    const rolesFinal = Array.isArray(data.roles) && data.roles.length > 0 ? data.roles : [userRoleFinal];

    localStorage.setItem(STORAGE_KEYS.accessToken, accessToken);
    localStorage.setItem(STORAGE_KEYS.user, JSON.stringify(userData));
    localStorage.setItem(STORAGE_KEYS.role, userRoleFinal);
    localStorage.setItem(STORAGE_KEYS.roles, JSON.stringify(rolesFinal));
    localStorage.setItem(STORAGE_KEYS.rememberMe, rememberMe ? 'true' : 'false');

    setToken(accessToken);
    setUser(userData);
    setRole(userRoleFinal);
    setRoles(rolesFinal);
    setIsAuthenticated(true);
    setLastActivity(Date.now());

    return data;
  }, []);

  const register = useCallback(async (userData) => {
    setError('');
    const res = await API.post('/api/auth/register', userData);
    return res.data;
  }, []);

  const requestPasswordReset = useCallback(async (email) => {
    const res = await API.post('/api/auth/request-password-reset', { email });
    return res.data;
  }, []);

  const resetPassword = useCallback(async (email, otp, newPassword) => {
    const res = await API.post('/api/auth/reset-password', { email, otp, newPassword });
    return res.data;
  }, []);

  const requestPasswordResetLink = useCallback(async (email) => {
    const res = await API.post('/api/auth/request-password-reset-link', { email });
    return res.data;
  }, []);

  const resetPasswordWithLink = useCallback(async (token, newPassword) => {
    const res = await API.post('/api/auth/reset-password-link', { token, newPassword });
    return res.data;
  }, []);

  const completeForcedPasswordReset = useCallback(async (email, oldPassword, newPassword, rememberMe = false) => {
    setError('');
    const res = await API.post('/api/auth/complete-forced-reset', { email, oldPassword, newPassword });
    const data = res.data;

    const accessToken = data.accessToken || data.token;
    const userData = data.user || { email: data.email, fullName: data.username || data.email };
    const userRoleFinal = data.role || 'CUSTOMER';
    const rolesFinal = Array.isArray(data.roles) && data.roles.length > 0 ? data.roles : [userRoleFinal];

    localStorage.setItem(STORAGE_KEYS.accessToken, accessToken);
    localStorage.setItem(STORAGE_KEYS.user, JSON.stringify(userData));
    localStorage.setItem(STORAGE_KEYS.role, userRoleFinal);
    localStorage.setItem(STORAGE_KEYS.roles, JSON.stringify(rolesFinal));
    localStorage.setItem(STORAGE_KEYS.rememberMe, rememberMe ? 'true' : 'false');

    setToken(accessToken);
    setUser(userData);
    setRole(userRoleFinal);
    setRoles(rolesFinal);
    setIsAuthenticated(true);
    setLastActivity(Date.now());

    return data;
  }, []);

  const resetActivityTimer = useCallback(() => {
    setLastActivity(Date.now());
    setShowSessionWarning(false);
    if (warningRef.current) clearTimeout(warningRef.current);
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
  }, []);

  useEffect(() => {
    if (!isAuthenticated) return;
    if (localStorage.getItem(STORAGE_KEYS.rememberMe) !== 'true') {
      warningRef.current = setTimeout(() => setShowSessionWarning(true), SESSION_TIMEOUT_MS - 60000);
      timeoutRef.current = setTimeout(() => { logout(); }, SESSION_TIMEOUT_MS);
    }
    const handleActivity = () => {
      if (localStorage.getItem(STORAGE_KEYS.rememberMe) !== 'true') {
        resetActivityTimer();
        warningRef.current = setTimeout(() => setShowSessionWarning(true), SESSION_TIMEOUT_MS - 60000);
        timeoutRef.current = setTimeout(() => { logout(); }, SESSION_TIMEOUT_MS);
      }
    };
    window.addEventListener('mousemove', handleActivity, { passive: true });
    window.addEventListener('keydown', handleActivity, { passive: true });
    window.addEventListener('click', handleActivity, { passive: true });
    window.addEventListener('scroll', handleActivity, { passive: true });
    return () => {
      window.removeEventListener('mousemove', handleActivity);
      window.removeEventListener('keydown', handleActivity);
      window.removeEventListener('click', handleActivity);
      window.removeEventListener('scroll', handleActivity);
      if (warningRef.current) clearTimeout(warningRef.current);
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, [isAuthenticated, resetActivityTimer, logout]);

  useEffect(() => {
    try {
      const savedAccessToken = localStorage.getItem(STORAGE_KEYS.accessToken);
      const savedUser = localStorage.getItem(STORAGE_KEYS.user);
      const savedRole = localStorage.getItem(STORAGE_KEYS.role);
      const savedRoles = localStorage.getItem(STORAGE_KEYS.roles);

      const savedImpersonation = localStorage.getItem(IMPERSONATION_STORAGE_KEY);
      if (savedImpersonation) {
        try {
          const parsed = JSON.parse(savedImpersonation);
          if (parsed.session && parsed.user) {
            setImpersonationSession(parsed.session);
            setImpersonatedUser(parsed.user);
            setIsImpersonating(true);
          }
        } catch { /* ignore invalid stored data */ }
      }

      if (savedAccessToken && !isTokenExpired(savedAccessToken)) {
        setToken(savedAccessToken);
        try { setUser(savedUser ? JSON.parse(savedUser) : null); } catch { setUser(null); }
        setRole(savedRole);
        try { setRoles(savedRoles ? JSON.parse(savedRoles) : (savedRole ? [savedRole] : [])); } catch { setRoles(savedRole ? [savedRole] : []); }
        setIsAuthenticated(true);
        setLastActivity(Date.now());
      } else if (savedAccessToken) {
        API.post('/api/auth/refresh')
          .then((res) => {
            const newAccess = res.data.accessToken || res.data.token;
            localStorage.setItem(STORAGE_KEYS.accessToken, newAccess);
            setToken(newAccess);
            try { setUser(savedUser ? JSON.parse(savedUser) : null); } catch { setUser(null); }
            setRole(savedRole);
            try { setRoles(savedRoles ? JSON.parse(savedRoles) : (savedRole ? [savedRole] : [])); } catch { setRoles(savedRole ? [savedRole] : []); }
            setIsAuthenticated(true);
            setLastActivity(Date.now());
          })
          .catch(() => {
            clearLocalAuth();
            setIsAuthenticated(false);
          })
          .finally(() => setLoading(false));
        return;
      } else {
        setIsAuthenticated(false);
      }
    } catch { setIsAuthenticated(false); }
    finally { setLoading(false); }
  }, [clearLocalAuth]);

  const extendSession = useCallback(() => {
    refreshToken().then(() => {
      setShowSessionWarning(false);
      resetActivityTimer();
    }).catch(() => { logout(); });
  }, [refreshToken, resetActivityTimer, logout]);

  const getAuthHeaders = useCallback(() => {
    const currentToken = token || localStorage.getItem(STORAGE_KEYS.accessToken);
    return { Authorization: 'Bearer ' + currentToken };
  }, [token]);

  const startImpersonation = useCallback(async (targetUserId, reason) => {
    const res = await API.post('/api/super-admin/impersonate/start', { targetUserId, reason });
    const session = res.data.session || res.data;
    const targetUser = res.data.target || res.data.user || session.target || session.user || {};
    setImpersonationSession(session);
    setImpersonatedUser(targetUser);
    setIsImpersonating(true);
    localStorage.setItem(IMPERSONATION_STORAGE_KEY, JSON.stringify({ session, user: targetUser }));
    return session;
  }, []);

  const stopImpersonation = useCallback(async (sessionId) => {
    try { await API.post('/api/super-admin/impersonate/stop', { sessionId }); } catch { /* proceed */ }
    setImpersonationSession(null);
    setImpersonatedUser(null);
    setIsImpersonating(false);
    localStorage.removeItem(IMPERSONATION_STORAGE_KEY);
  }, []);

  const value = {
    user, token, role, roles, isAuthenticated, loading, error,
    login, loginWithGoogle, register, logout, refreshToken, getAuthHeaders, isTokenExpired,
    pendingMfa, completeMfaLogin, cancelMfaLogin, refreshUser,
    requestPasswordReset, resetPassword, completeForcedPasswordReset,
    requestPasswordResetLink, resetPasswordWithLink,
    isImpersonating, impersonationSession, impersonatedUser,
    startImpersonation, stopImpersonation,
    showSessionWarning, extendSession, lastActivity,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
      {showSessionWarning && (
        <div style={{
          position: 'fixed', bottom: '1rem', right: '1rem', zIndex: 9999,
          background: '#fff', border: '1px solid #e2e8f0', borderRadius: '12px',
          padding: '1rem 1.25rem', boxShadow: '0 10px 25px rgba(0,0,0,0.12)',
          maxWidth: '360px', animation: 'slideUp 0.3s ease'
        }}>
          <p style={{ margin: '0 0 0.5rem', fontSize: '0.85rem', color: '#1e293b', fontWeight: 600 }}>
            Session Expiring Soon
          </p>
          <p style={{ margin: '0 0 0.75rem', fontSize: '0.8rem', color: '#64748b' }}>
            Your session will expire in 1 minute due to inactivity.
          </p>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button onClick={extendSession} style={{
              padding: '0.4rem 0.85rem', border: 'none', borderRadius: '6px',
              background: 'var(--color-primary, #16a34a)', color: '#fff',
              fontSize: '0.8rem', fontWeight: 600, cursor: 'pointer'
            }}>Stay Logged In</button>
            <button onClick={logout} style={{
              padding: '0.4rem 0.85rem', border: '1px solid #d1d5db', borderRadius: '6px',
              background: '#fff', color: '#475569', fontSize: '0.8rem', cursor: 'pointer'
            }}>Logout</button>
          </div>
        </div>
      )}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
};
