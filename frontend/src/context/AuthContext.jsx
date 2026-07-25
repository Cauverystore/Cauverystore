import React, { createContext, useState, useEffect, useCallback, useContext, useRef } from 'react';
import API from '../utils/axios';

export const AuthContext = createContext(null);

const IMPERSONATION_STORAGE_KEY = 'impersonation_session';

const STORAGE_KEYS = {
  accessToken: 'accessToken',
  refreshToken: 'refreshToken',
  user: 'user',
  role: 'role',
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
  const [refreshTokenValue, setRefreshTokenValue] = useState(null);
  const [role, setRole] = useState(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [isImpersonating, setIsImpersonating] = useState(false);
  const [impersonationSession, setImpersonationSession] = useState(null);
  const [impersonatedUser, setImpersonatedUser] = useState(null);
  const [lastActivity, setLastActivity] = useState(Date.now());
  const [showSessionWarning, setShowSessionWarning] = useState(false);
  const timeoutRef = useRef(null);
  const warningRef = useRef(null);

  const clearLocalAuth = useCallback(() => {
    localStorage.removeItem(STORAGE_KEYS.accessToken);
    localStorage.removeItem(STORAGE_KEYS.refreshToken);
    localStorage.removeItem(STORAGE_KEYS.user);
    localStorage.removeItem(STORAGE_KEYS.role);
    localStorage.removeItem('admin_token');
  }, []);

  const logout = useCallback(async () => {
    try {
      const storedRefresh = localStorage.getItem(STORAGE_KEYS.refreshToken);
      if (storedRefresh) {
        await API.post('/api/auth/logout', { refreshToken: storedRefresh });
      }
    } catch { /* proceed with local logout even if API call fails */ }
    clearLocalAuth();
    setToken(null);
    setRefreshTokenValue(null);
    setUser(null);
    setRole(null);
    setIsAuthenticated(false);
    setError('');
    setShowSessionWarning(false);
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    if (warningRef.current) clearTimeout(warningRef.current);
  }, [clearLocalAuth]);

  const refreshToken = useCallback(async () => {
    const storedRefresh = localStorage.getItem(STORAGE_KEYS.refreshToken);
    if (!storedRefresh) {
      await logout();
      throw new Error('No refresh token available');
    }
    try {
      const res = await API.post('/api/auth/refresh', { refreshToken: storedRefresh });
      const newAccess = res.data.accessToken || res.data.token;
      const newRefresh = res.data.refreshToken || storedRefresh;
      localStorage.setItem(STORAGE_KEYS.accessToken, newAccess);
      localStorage.setItem(STORAGE_KEYS.refreshToken, newRefresh);
      setToken(newAccess);
      setRefreshTokenValue(newRefresh);
      return newAccess;
    } catch {
      await logout();
      throw new Error('Refresh failed');
    }
  }, [logout]);

  const login = useCallback(async (email, password, userRole, rememberMe = false) => {
    setError('');
    const res = await API.post('/api/auth/login', { email, password, role: userRole });
    const data = res.data;

    const accessToken = data.accessToken || data.token;
    const newRefreshToken = data.refreshToken || '';
    const userData = data.user || { email, fullName: data.username || email };
    const userRoleFinal = data.role || (userRole ? userRole.toUpperCase() : 'CUSTOMER');

    localStorage.setItem(STORAGE_KEYS.accessToken, accessToken);
    localStorage.setItem(STORAGE_KEYS.refreshToken, newRefreshToken);
    localStorage.setItem(STORAGE_KEYS.user, JSON.stringify(userData));
    localStorage.setItem(STORAGE_KEYS.role, userRoleFinal);
    localStorage.setItem(STORAGE_KEYS.rememberMe, rememberMe ? 'true' : 'false');

    setToken(accessToken);
    setRefreshTokenValue(newRefreshToken);
    setUser(userData);
    setRole(userRoleFinal);
    setIsAuthenticated(true);
    setLastActivity(Date.now());

    return data;
  }, []);

  const register = useCallback(async (userData) => {
    setError('');
    const res = await API.post('/api/auth/register', userData);
    return res.data;
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
      const savedRefreshToken = localStorage.getItem(STORAGE_KEYS.refreshToken);
      const savedUser = localStorage.getItem(STORAGE_KEYS.user);
      const savedRole = localStorage.getItem(STORAGE_KEYS.role);

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
        setRefreshTokenValue(savedRefreshToken);
        setUser(savedUser ? JSON.parse(savedUser) : null);
        setRole(savedRole);
        setIsAuthenticated(true);
        setLastActivity(Date.now());
      } else if (savedAccessToken && savedRefreshToken) {
        API.post('/api/auth/refresh', { refreshToken: savedRefreshToken })
          .then((res) => {
            const newAccess = res.data.accessToken || res.data.token;
            const newRefresh = res.data.refreshToken || savedRefreshToken;
            localStorage.setItem(STORAGE_KEYS.accessToken, newAccess);
            localStorage.setItem(STORAGE_KEYS.refreshToken, newRefresh);
            setToken(newAccess);
            setRefreshTokenValue(newRefresh);
            setUser(savedUser ? JSON.parse(savedUser) : null);
            setRole(savedRole);
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
    user, token, role, isAuthenticated, loading, error,
    login, register, logout, refreshToken, getAuthHeaders, isTokenExpired,
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
