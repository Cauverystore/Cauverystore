import axios from 'axios';

const api = axios.create({
  baseURL: process.env.REACT_APP_API_URL || 'http://localhost:9091',
  withCredentials: true,
});

let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach((prom) => {
    if (error) prom.reject(error);
    else prom.resolve(token);
  });
  failedQueue = [];
};

api.interceptors.request.use(
  (config) => {
    if (!(config.data instanceof FormData)) {
      config.headers['Content-Type'] = 'application/json';
    }
    if (config.method === 'get' || config.method === 'GET') {
      config.params = { ...config.params, _t: Date.now() };
    }
    const token = localStorage.getItem('accessToken') || localStorage.getItem('admin_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    try {
      const impSession = localStorage.getItem('impersonation_session');
      if (impSession) {
        const parsed = JSON.parse(impSession);
        const impToken = parsed.session?.token || parsed.session?.impersonationToken;
        if (impToken) {
          config.headers['X-Impersonation-Token'] = impToken;
        }
      }
    } catch { /* ignore */ }
    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => {
    // A request that succeeded proves the session is working, so arm the expiry notice again.
    // Without this the flag is set once and never cleared: sign in again after being timed out
    // and the *next* expiry in the same tab redirects nobody, leaving the page silently broken.
    if (sessionStorage.getItem('auth_session_expired_told')) {
      sessionStorage.removeItem('auth_session_expired_told');
    }
    return response;
  },
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 403 && error.response?.data?.message?.toLowerCase().includes('impersonation')) {
      localStorage.removeItem('impersonation_session');
      window.location.reload();
    }

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return api(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        // Deliberately bypasses the `api` instance (and its interceptors) - if the refresh
        // token/cookie is also stale, this call 401s too, and routing it back through this
        // same interceptor would queue it behind `isRefreshing`, which only clears when this
        // very call finishes. That's a permanent deadlock: every future request hangs forever
        // instead of ever reaching the catch block below.
        const res = await axios.post(
          (process.env.REACT_APP_API_URL || 'http://localhost:9091') + '/api/auth/refresh',
          {},
          { withCredentials: true }
        );

        const newAccessToken = res.data.accessToken || res.data.token;

        localStorage.setItem('accessToken', newAccessToken);

        processQueue(null, newAccessToken);

        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        // Read before clearing. Both of these are about to be deleted, and reading them
        // afterwards is how the previous attempt sent every expired admin to the customer
        // login screen: role was always '' by the time it was looked at.
        const hadToken =
          !!localStorage.getItem('accessToken') || !!localStorage.getItem('admin_token');
        const role = (localStorage.getItem('role') || '').toLowerCase();

        localStorage.removeItem('accessToken');
        localStorage.removeItem('user');
        localStorage.removeItem('role');
        localStorage.removeItem('admin_token');
        const path = window.location.pathname + window.location.search;
        const alreadyOnLogin = /^\/(login|admin\/login)(\?|$)/.test(path);
        // Only tell someone their session expired if they actually had one. A guest browsing a
        // public page can still provoke a 401, and being bounced to "session expired" from the
        // home page is what this looked like from the outside.
        if (hadToken && !alreadyOnLogin && !sessionStorage.getItem('auth_session_expired_told')) {
          sessionStorage.setItem('auth_session_expired_told', '1');
          const isStaff = ['admin', 'super_admin', 'executive'].includes(role);
          const login = isStaff ? '/admin/login' : '/login';
          const redirect = isStaff ? '/admin' : (path.startsWith('/login') ? '' : path);
          window.location.replace(
            `${login}?reason=session-expired&redirect=${encodeURIComponent(redirect || '/')}`
          );
        }
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default api;
