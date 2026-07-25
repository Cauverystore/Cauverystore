import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getDefaultRoute } from '../utils/rolePermissions';

const ProtectedRoute = ({ requiredRole, redirectTo, children }) => {
  const { isAuthenticated, role, loading } = useAuth();

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
    if (!allowedRoles.includes(role)) {
      const defaultRoute = getDefaultRoute(role);
      return <Navigate to={redirectTo || defaultRoute || '/unauthorized'} replace />;
    }
  }

  return children;
};

export default ProtectedRoute;
