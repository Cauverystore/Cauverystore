import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getDefaultRoute } from '../utils/rolePermissions';

const ProtectedRoute = ({ requiredRole, redirectTo, children }) => {
  const { isAuthenticated, role, roles, loading } = useAuth();

  if (loading) {
    return (
      <div className="auth-loading">
        <div className="auth-loading-spinner"></div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (requiredRole) {
    const allowedRoles = Array.isArray(requiredRole) ? requiredRole : [requiredRole];
    // A dual-capability account (e.g. customer + seller) holds multiple roles at once - check
    // the full set, not just whichever one was active at login, so a seller browsing as
    // "Customer" can still reach /seller/** routes without logging in again.
    const myRoles = Array.isArray(roles) && roles.length > 0 ? roles : [role];
    const hasAccess = allowedRoles.some((r) => myRoles.includes(r));
    if (!hasAccess) {
      const defaultRoute = getDefaultRoute(role);
      return <Navigate to={redirectTo || defaultRoute || '/unauthorized'} replace />;
    }
  }

  return children;
};

export default ProtectedRoute;
